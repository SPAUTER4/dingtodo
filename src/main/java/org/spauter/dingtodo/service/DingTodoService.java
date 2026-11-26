package org.spauter.dingtodo.service;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spauter.dingtodo.client.DingTalkNotifyClient;
import org.spauter.dingtodo.client.DingTalkTodoClient;
import org.spauter.dingtodo.client.DingTalkUserClient;
import org.spauter.dingtodo.client.JsonHttp;
import org.spauter.dingtodo.common.ApiException;
import org.spauter.dingtodo.config.DingTalkProperties;
import org.spauter.dingtodo.config.DingTodoProperties;
import org.spauter.dingtodo.dto.CreateTaskResult;
import org.spauter.dingtodo.dto.NotifySendRequest;
import org.spauter.dingtodo.dto.TodoPage;
import org.spauter.dingtodo.dto.TodoSendRequest;
import org.spauter.dingtodo.dto.TodoSendResult;
import org.spauter.dingtodo.dto.TodoTaskInfo;
import org.spauter.dingtodo.dto.WithdrawResult;
import org.spauter.dingtodo.enums.NotifyType;
import org.spauter.dingtodo.enums.TodoRecordStatus;
import org.spauter.dingtodo.model.TodoRecord;
import org.spauter.dingtodo.store.TodoRecordStore;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 钉钉待办 / 工作通知组合服务：负责参数组装、台账记录与撤回编排。
 */
@Service
public class DingTodoService {

    private static final Logger log = LoggerFactory.getLogger(DingTodoService.class);

    private final DingTalkProperties dingProps;
    private final DingTodoProperties props;
    private final DingTalkUserClient userClient;
    private final DingTalkTodoClient todoClient;
    private final DingTalkNotifyClient notifyClient;
    private final TodoRecordStore store;
    private final JsonHttp http;

    public DingTodoService(DingTalkProperties dingProps, DingTodoProperties props,
                           DingTalkUserClient userClient, DingTalkTodoClient todoClient,
                           DingTalkNotifyClient notifyClient, TodoRecordStore store, JsonHttp http) {
        this.dingProps = dingProps;
        this.props = props;
        this.userClient = userClient;
        this.todoClient = todoClient;
        this.notifyClient = notifyClient;
        this.store = store;
        this.http = http;
    }

    /** 发待办：每个执行人创建一条，可选同时发工作通知，全部落台账 */
    public List<TodoSendResult> sendTodo(TodoSendRequest req) {
        List<TodoSendResult> results = new ArrayList<>();
        List<String> participantUnionIds = resolveUnionIds(req.participantIds());
        for (String userId : req.executorIds()) {
            String unionId = userClient.getUnionId(userId);
            CreateTaskResult created = todoClient.createTask(unionId, buildTaskBody(req, unionId, participantUnionIds));

            if (props.recordEnabled()) {
                TodoRecord record = newRecord(req.bizId(), userId);
                record.setTaskId(created.taskId());
                record.setRequestId(created.requestId());
                record.setStatus(TodoRecordStatus.PUSHED);
                store.save(record);
            }

            Long msgTaskId = maybeSendNotify(req, userId);
            results.add(new TodoSendResult(userId, unionId, created.taskId(), created.requestId(), msgTaskId));
        }
        return results;
    }

    /** 按 taskId 直接撤回待办 */
    public void withdrawByTaskId(String userId, String taskId) {
        String unionId = userClient.getUnionId(userId);
        todoClient.deleteTask(unionId, taskId);
        if (props.recordEnabled()) {
            store.findByTaskId(taskId).ifPresent(store::markWithdrawn);
        }
    }

    /** 按业务键撤回：撤回该 bizId 下所有已推送待办及配套通知 */
    public WithdrawResult withdrawByBizId(String bizId) {
        requireRecordEnabled();
        List<TodoRecord> pushed = store.findPushed(bizId);
        List<String> failed = new ArrayList<>();
        int success = 0;
        for (TodoRecord record : pushed) {
            try {
                String unionId = userClient.getUnionId(record.getUserId());
                todoClient.deleteTask(unionId, record.getTaskId());
                store.markWithdrawn(record);
                success++;
            } catch (ApiException e) {
                log.warn("撤回待办失败: bizId={}, userId={}, taskId={}, reason={}",
                        bizId, record.getUserId(), record.getTaskId(), e.getMessage());
                failed.add(record.getUserId());
            }
        }
        store.findLatestNotify(bizId).ifPresent(notify -> {
            if (notify.getMsgTaskId() != null) {
                try {
                    notifyClient.recallNotify(notify.getMsgTaskId());
                } catch (ApiException e) {
                    log.warn("撤回配套通知失败: bizId={}, msgTaskId={}, reason={}",
                            bizId, notify.getMsgTaskId(), e.getMessage());
                }
            }
        });
        return new WithdrawResult(pushed.size(), success, failed);
    }

    /** 查询用户组织内全部待办，fetchAll 时自动翻页聚合 */
    public List<TodoTaskInfo> listOrgTodos(String userId, boolean fetchAll) {
        String unionId = userClient.getUnionId(userId);
        List<TodoTaskInfo> all = new ArrayList<>();
        String nextToken = "0";
        do {
            TodoPage page = todoClient.queryOrgTasks(unionId, nextToken);
            all.addAll(page.tasks());
            nextToken = fetchAll && page.hasMore() ? page.nextToken() : null;
        } while (nextToken != null);
        return all;
    }

    /** 发工作通知 */
    public long sendNotify(NotifySendRequest req) {
        validateNotifyRequest(req);
        long msgTaskId = doSendNotify(req);
        if (props.recordEnabled() && StringUtils.hasText(req.bizId())) {
            TodoRecord record = newRecord(req.bizId(), null);
            record.setMsgTaskId(msgTaskId);
            record.setStatus(TodoRecordStatus.NOTIFY);
            store.save(record);
        }
        return msgTaskId;
    }

    /** 撤回工作通知 */
    public void withdrawNotify(long msgTaskId) {
        notifyClient.recallNotify(msgTaskId);
    }

    /** 钉钉免登：code 换 userId */
    public String getUserIdByCode(String code) {
        return userClient.getUserIdByCode(code);
    }

    /** 根据手机号查询钉钉 userId */
    public String getUserIdByMobile(String mobile) {
        return userClient.getUserIdByMobile(mobile);
    }

    private ObjectNode buildTaskBody(TodoSendRequest req, String unionId, List<String> participantUnionIds) {
        ObjectNode body = http.newObject()
                .put("subject", req.subject());
        if (StringUtils.hasText(req.description())) {
            body.put("description", req.description());
        }
        ObjectNode detailUrl = body.putObject("detailUrl");
        if (StringUtils.hasText(req.appUrl())) {
            detailUrl.put("appUrl", req.appUrl());
        }
        if (StringUtils.hasText(req.pcUrl())) {
            detailUrl.put("pcUrl", req.pcUrl());
        }
        body.putObject("remindNotifyConfigs").put("dingNotify", "1");
        body.putArray("executorIds").add(unionId);
        if (!participantUnionIds.isEmpty()) {
            ArrayNode participants = body.putArray("participantIds");
            participantUnionIds.forEach(participants::add);
        }
        return body;
    }

    /** 发待办时附带的工作通知：sendNotify=true 且有 appUrl 时才发，按钮跳 appUrl */
    private Long maybeSendNotify(TodoSendRequest req, String userId) {
        if (!Boolean.TRUE.equals(req.sendNotify()) || !StringUtils.hasText(req.appUrl())) {
            return null;
        }
        String title = StringUtils.hasText(req.notifyTitle()) ? req.notifyTitle() : req.subject();
        String markdown = StringUtils.hasText(req.notifyContent()) ? req.notifyContent() : req.description();
        long msgTaskId = notifyClient.sendActionCard(List.of(userId), title,
                markdown == null ? title : markdown, "查看详情", req.appUrl());
        if (props.recordEnabled()) {
            TodoRecord record = newRecord(req.bizId(), userId);
            record.setMsgTaskId(msgTaskId);
            record.setStatus(TodoRecordStatus.NOTIFY);
            store.save(record);
        }
        return msgTaskId;
    }

    private long doSendNotify(NotifySendRequest req) {
        if (req.type() == NotifyType.ACTION_CARD) {
            return notifyClient.sendActionCard(req.userIds(), req.title(), req.markdown(),
                    req.singleTitle(), req.singleUrl());
        }
        return notifyClient.sendText(req.userIds(), req.text());
    }

    private void validateNotifyRequest(NotifySendRequest req) {
        if (req.type() == NotifyType.ACTION_CARD) {
            if (!StringUtils.hasText(req.title()) || !StringUtils.hasText(req.markdown())) {
                throw new ApiException("ACTION_CARD 通知必须传 title 和 markdown");
            }
        } else if (req.type() == NotifyType.TEXT && !StringUtils.hasText(req.text())) {
            throw new ApiException("TEXT 通知必须传 text");
        }
    }

    private List<String> resolveUnionIds(List<String> userIds) {
        List<String> unionIds = new ArrayList<>();
        if (userIds != null) {
            for (String userId : userIds) {
                unionIds.add(userClient.getUnionId(userId));
            }
        }
        return unionIds;
    }

    private TodoRecord newRecord(String bizId, String userId) {
        TodoRecord record = new TodoRecord();
        record.setBizId(bizId);
        record.setUserId(userId);
        return record;
    }

    private void requireRecordEnabled() {
        if (!props.recordEnabled()) {
            throw new ApiException("dingtodo.record-enabled=false，按业务键撤回不可用（仅支持按 taskId 撤回）");
        }
    }
}
