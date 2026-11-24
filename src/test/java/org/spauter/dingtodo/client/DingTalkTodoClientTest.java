package org.spauter.dingtodo.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        server.enqueue(new MockResponse().setBody("{\"taskId\":123,\"requestId\":\"req-1\"}"));

        CreateTaskResult result = client.createTask("union-1",
                new ObjectMapper().createObjectNode().put("subject", "审批"));

        assertEquals("123", result.taskId());
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
    void queryOrgTasksShouldParsePage() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"todoList\":[{\"taskId\":\"1\",\"subject\":\"s1\"}],\"nextToken\":\"n2\",\"hasMore\":true}"));

        TodoPage page = client.queryOrgTasks("union-1", "");

        assertTrue(page.hasMore());
        assertEquals("n2", page.nextToken());
        assertEquals(1, page.tasks().size());
        assertEquals("1", page.tasks().get(0).taskId());
        RecordedRequest recorded = server.takeRequest();
        assertEquals("/v1.0/todo/users/union-1/org/tasks/query", recorded.getPath());
    }

    @Test
    void shouldThrowApiExceptionOnHttpError() {
        server.enqueue(new MockResponse().setResponseCode(403)
                .setBody("{\"code\":\"Forbidden\",\"message\":\"no permission\"}"));

        ApiException e = assertThrows(ApiException.class, () -> client.deleteTask("union-1", "task-1"));
        assertEquals("Forbidden", e.getCode());
    }
}
