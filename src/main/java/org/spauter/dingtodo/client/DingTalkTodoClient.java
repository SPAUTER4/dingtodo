package org.spauter.dingtodo.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.spauter.dingtodo.common.ApiException;
import org.spauter.dingtodo.config.DingTalkProperties;
import org.spauter.dingtodo.dto.CreateTaskResult;
import org.spauter.dingtodo.dto.TodoPage;
import org.spauter.dingtodo.dto.TodoTaskInfo;
import org.springframework.stereotype.Component;

/**
 * 钉钉待办（新版 v1.0 REST API）：发 / 撤 / 查。
 *
 * <p>接口路径中的 unionId 由上层通过 DingTalkUserClient 转换得到。
 */
@Component
public class DingTalkTodoClient {

    private final DingTalkProperties props;
    private final DingTalkTokenManager tokenManager;
    private final JsonHttp http;

    public DingTalkTodoClient(DingTalkProperties props, DingTalkTokenManager tokenManager, JsonHttp http) {
        this.props = props;
        this.tokenManager = tokenManager;
        this.http = http;
    }

    /** 创建待办，taskBody 由 DingTodoService 组装 */
    public CreateTaskResult createTask(String unionId, ObjectNode taskBody) {
        String url = props.apiBaseUrl() + "/v1.0/todo/users/" + encode(unionId) + "/tasks";
        JsonNode resp = http.postApiV1(url, tokenManager.getToken(), taskBody, "创建待办");
        return new CreateTaskResult(
                resp.path("taskId").asText(),
                resp.path("requestId").asText(null));
    }

    /** 删除（撤回）待办 */
    public void deleteTask(String unionId, String taskId) {
        String url = props.apiBaseUrl() + "/v1.0/todo/users/" + encode(unionId)
                + "/tasks/" + encode(taskId);
        http.deleteApiV1(url, tokenManager.getToken(), "删除待办");
    }

    /** 分页查询用户组织内全部待办 */
    public TodoPage queryOrgTasks(String unionId, String nextToken) {
        String url = props.apiBaseUrl() + "/v1.0/todo/users/" + encode(unionId) + "/org/tasks/query";
        ObjectNode body = http.newObject().put("nextToken", nextToken == null ? "" : nextToken);
        JsonNode resp = http.postApiV1(url, tokenManager.getToken(), body, "查询组织待办");

        // 实际响应字段：todoCards（卡片数组，createdTime 毫秒时间戳）、nextToken（字符串数字，
        // 表示已返回条数）、totalCount（总数）；无 hasMore 字段
        List<TodoTaskInfo> tasks = new ArrayList<>();
        for (JsonNode item : resp.path("todoCards")) {
            tasks.add(new TodoTaskInfo(
                    item.path("taskId").asText(),
                    item.path("subject").asText(),
                    item.path("originalSource").path("sourceTitle").asText(null),
                    item.hasNonNull("createdTime") ? item.path("createdTime").asLong() : null));
        }
        String pageToken = resp.hasNonNull("nextToken") ? resp.path("nextToken").asText() : null;
        return new TodoPage(tasks, pageToken, hasMore(tasks, pageToken, resp));
    }

    /**
     * 分页终止判定（防死循环）：nextToken 非空且本页非空才继续；
     * nextToken 是数字时与 totalCount 比较，达到总数即停止。
     */
    private boolean hasMore(List<TodoTaskInfo> tasks, String pageToken, JsonNode resp) {
        if (pageToken == null || pageToken.isEmpty() || tasks.isEmpty()) {
            return false;
        }
        long total = resp.path("totalCount").asLong(-1);
        if (total >= 0) {
            try {
                return Long.parseLong(pageToken.trim()) < total;
            } catch (NumberFormatException ignored) {
                // 非数字游标，退化为「有 token 即继续」
            }
        }
        return true;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
