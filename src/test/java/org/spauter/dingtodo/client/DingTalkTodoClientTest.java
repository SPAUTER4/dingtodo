package org.spauter.dingtodo.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spauter.dingtodo.common.ApiException;
import org.spauter.dingtodo.config.DingTalkProperties;
import org.spauter.dingtodo.dto.CreateTaskResult;
import org.spauter.dingtodo.dto.TodoPage;

class DingTalkTodoClientTest {

    private MockWebServer server;
    private DingTalkTodoClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        DingTalkProperties props = new DingTalkProperties("appkey-x", "appsecret-x", "100",
                "https://oapi.dingtalk.com", server.url("/").toString().replaceAll("/$", ""), 7000);
        DingTalkTokenManager tokenManager = mock(DingTalkTokenManager.class);
        when(tokenManager.getToken()).thenReturn("tk");
        client = new DingTalkTodoClient(props, tokenManager,
                new JsonHttp(new OkHttpClient(), new ObjectMapper(), new org.spauter.dingtodo.store.InMemoryApiCallLogStore()));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void createTaskShouldPostV1RestWithTokenHeader() throws Exception {
        // 创建待办响应的主键字段是 id（非 taskId），requestId 伴随返回
        server.enqueue(new MockResponse().setBody(
                "{\"id\":\"taskfff2f7db2497501cca6dd20519299d26\",\"requestId\":\"req-1\"}"));

        CreateTaskResult result = client.createTask("union-1",
                new ObjectMapper().createObjectNode().put("subject", "审批"));

        assertEquals("taskfff2f7db2497501cca6dd20519299d26", result.taskId());
        assertEquals("req-1", result.requestId());
        RecordedRequest recorded = server.takeRequest();
        assertEquals("/v1.0/todo/users/union-1/tasks", recorded.getPath());
        assertEquals("tk", recorded.getHeader("x-acs-dingtalk-access-token"));
        JsonNode body = new ObjectMapper().readTree(recorded.getBody().readUtf8());
        assertEquals("审批", body.path("subject").asText());
    }

    @Test
    void deleteTaskShouldCallDeleteEndpoint() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        client.deleteTask("union-1", "task-1");

        RecordedRequest recorded = server.takeRequest();
        assertEquals("/v1.0/todo/users/union-1/tasks/task-1", recorded.getPath());
        assertEquals("DELETE", recorded.getMethod());
    }

    @Test
    void queryOrgTasksShouldParseTodoCardsPage() throws Exception {
        // 按真实响应结构 mock：todoCards / nextToken(字符串数字) / totalCount，无 hasMore 字段
        server.enqueue(new MockResponse().setBody(
                "{\"todoCards\":[{\"taskId\":\"t1\",\"subject\":\"审批单\",\"createdTime\":1795672517000,"
                        + "\"originalSource\":{\"sourceTitle\":\"\"}}],"
                        + "\"nextToken\":\"15\",\"totalCount\":18}"));

        TodoPage page = client.queryOrgTasks("union-1", "");

        assertTrue(page.hasMore());
        assertEquals("15", page.nextToken());
        assertEquals(1, page.tasks().size());
        assertEquals("t1", page.tasks().get(0).taskId());
        assertEquals("审批单", page.tasks().get(0).subject());
        assertEquals(1795672517000L, page.tasks().get(0).createTime());
        RecordedRequest recorded = server.takeRequest();
        assertEquals("/v1.0/todo/users/union-1/org/tasks/query", recorded.getPath());
    }

    @Test
    void queryOrgTasksShouldStopWhenNextTokenReachesTotalCount() {
        // 下一页游标已达到总数：hasMore=false，避免翻页死循环
        server.enqueue(new MockResponse().setBody(
                "{\"todoCards\":[{\"taskId\":\"t1\",\"subject\":\"s\"}],\"nextToken\":\"18\",\"totalCount\":18}"));

        TodoPage page = client.queryOrgTasks("union-1", "15");

        assertFalse(page.hasMore());
        assertEquals(1, page.tasks().size());
    }

    @Test
    void queryOrgTasksShouldStopOnEmptyPage() {
        // 空页兜底终止（防游标非数字等异常情形死循环）
        server.enqueue(new MockResponse().setBody(
                "{\"todoCards\":[],\"nextToken\":\"abc\",\"totalCount\":18}"));

        TodoPage page = client.queryOrgTasks("union-1", "15");

        assertFalse(page.hasMore());
        assertTrue(page.tasks().isEmpty());
    }

    @Test
    void shouldThrowApiExceptionOnHttpError() {
        server.enqueue(new MockResponse().setResponseCode(403)
                .setBody("{\"code\":\"Forbidden\",\"message\":\"no permission\"}"));

        ApiException e = assertThrows(ApiException.class, () -> client.deleteTask("union-1", "task-1"));
        assertEquals("Forbidden", e.getCode());
    }
}
