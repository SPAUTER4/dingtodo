package org.spauter.dingtodo.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.spauter.dingtodo.common.ApiException;
import org.spauter.dingtodo.config.DingTalkProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 工作通知（企业会话消息）：发（asyncsend_v2）/ 撤（recall）。
 *
 * <p>接收人直接传钉钉 userId 列表（asyncsend_v2 语义），不做 unionId 转换。
 */
@Component
public class DingTalkNotifyClient {

    private final DingTalkProperties props;
    private final DingTalkTokenManager tokenManager;
    private final JsonHttp http;

    public DingTalkNotifyClient(DingTalkProperties props, DingTalkTokenManager tokenManager, JsonHttp http) {
        this.props = props;
        this.tokenManager = tokenManager;
        this.http = http;
    }

    /** 发卡片通知，返回 msg task_id（用于撤回） */
    public long sendActionCard(List<String> userIds, String title, String markdown,
                               String singleTitle, String singleUrl) {
        ObjectNode card = http.newObject()
                .put("title", title)
                .put("markdown", markdown);
        if (StringUtils.hasText(singleUrl)) {
            card.put("single_title", StringUtils.hasText(singleTitle) ? singleTitle : "查看详情")
                    .put("single_url", singleUrl);
        }
        ObjectNode msg = http.newObject().put("msgtype", "action_card").set("action_card", card);
        return asyncSend(userIds, msg);
    }

    /** 发纯文本通知，返回 msg task_id */
    public long sendText(List<String> userIds, String content) {
        ObjectNode text = http.newObject().put("content", content);
        ObjectNode msg = http.newObject().put("msgtype", "text").set("text", text);
        return asyncSend(userIds, msg);
    }

    /** 撤回工作通知 */
    public void recallNotify(long msgTaskId) {
        http.postOapi(
                props.oapiBaseUrl() + "/topapi/message/corpconversation/recall?access_token="
                        + tokenManager.getToken(),
                http.newObject()
                        .put("msg_task_id", msgTaskId)
                        .put("agent_id", agentId()),
                "撤回工作通知");
    }

    private long asyncSend(List<String> userIds, ObjectNode msg) {
        if (userIds == null || userIds.isEmpty()) {
            throw new ApiException("通知接收人不能为空");
        }
        JsonNode resp = http.postOapi(
                props.oapiBaseUrl() + "/topapi/message/corpconversation/asyncsend_v2?access_token="
                        + tokenManager.getToken(),
                http.newObject()
                        .put("agent_id", agentId())
                        .put("userid_list", String.join(",", userIds))
                        .set("msg", msg),
                "发工作通知");
        return resp.path("task_id").asLong();
    }

    private long agentId() {
        if (!StringUtils.hasText(props.agentId())) {
            throw new ApiException("dingding.agent-id 未配置");
        }
        try {
            return Long.parseLong(props.agentId().trim());
        } catch (NumberFormatException e) {
            throw new ApiException("dingding.agent-id 必须是数字: " + props.agentId());
        }
    }
}
