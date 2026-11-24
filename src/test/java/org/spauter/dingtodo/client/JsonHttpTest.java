package org.spauter.dingtodo.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayDeque;
import java.util.Deque;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spauter.dingtodo.common.ApiException;
import org.spauter.dingtodo.model.ApiCallLog;
import org.spauter.dingtodo.store.ApiCallLogStore;

class JsonHttpTest {

    /** 测试专用：记录每条留痕 */
    private static class RecordingStore implements ApiCallLogStore {
        final Deque<ApiCallLog> logs = new ArrayDeque<>();

        @Override
        public void save(ApiCallLog log) {
            logs.addLast(log);
        }

        ApiCallLog last() {
            return logs.getLast();
        }
    }

    private MockWebServer server;
    private JsonHttp http;
    private RecordingStore logStore;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        logStore = new RecordingStore();
        http = new JsonHttp(new OkHttpClient(), new ObjectMapper(), logStore);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private String baseUrl() {
        return server.url("/").toString().replaceAll("/$", "");
    }

    @Test
    void successfulCallShouldLogMaskedUrlAndUserId() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"errcode\":0,\"result\":{\"unionid\":\"u-union\"}}"));

        JsonNode resp = http.postOapi(baseUrl() + "/topapi/v2/user/get?access_token=secret-token-abc",
                http.newObject().put("userid", "user001"), "获取unionId");

        assertEquals("u-union", resp.path("result").path("unionid").asText());
        assertEquals(1, logStore.logs.size());
        ApiCallLog log = logStore.last();
        assertTrue(log.isSuccess());
        assertEquals("获取unionId", log.getOperation());
        assertEquals("POST", log.getMethod());
        assertEquals("user001", log.getOpenId());
        assertNotNull(log.getElapsedMs());
        assertNotNull(log.getCreateTime());
        assertFalse(log.getDingUrl().contains("secret-token-abc"), "URL 中的 access_token 必须脱敏");
        assertTrue(log.getDingUrl().contains("access_token=***"));
        assertEquals("{\"userid\":\"user001\"}", log.getParams());
        assertNull(log.getErrMsg());
    }

    @Test
    void oapiBusinessFailureShouldLogUnsuccessful() {
        server.enqueue(new MockResponse().setBody("{\"errcode\":60121,\"errmsg\":\"找不到该用户\"}"));

        assertThrows(ApiException.class, () ->
                http.postOapi(baseUrl() + "/topapi/v2/user/get?access_token=t", http.newObject(), "获取unionId"));

        ApiCallLog log = logStore.last();
        assertFalse(log.isSuccess());
        assertEquals("60121: 找不到该用户", log.getErrMsg());
        assertNotNull(log.getElapsedMs());
    }

    @Test
    void httpErrorShouldLogFailure() {
        server.enqueue(new MockResponse().setResponseCode(403)
                .setBody("{\"code\":\"Forbidden\",\"message\":\"no permission\"}"));

        assertThrows(ApiException.class, () ->
                http.deleteApiV1(baseUrl() + "/v1.0/todo/users/u/tasks/t1", "tk", "删除待办"));

        ApiCallLog log = logStore.last();
        assertFalse(log.isSuccess());
        assertEquals("删除待办", log.getOperation());
        assertEquals("DELETE", log.getMethod());
        assertEquals("Forbidden: no permission", log.getErrMsg());
    }

    @Test
    void logStoreFailureShouldNotBreakBusinessCall() {
        ApiCallLogStore brokenStore = log -> {
            throw new RuntimeException("db down");
        };
        JsonHttp httpWithBrokenStore = new JsonHttp(new OkHttpClient(), new ObjectMapper(), brokenStore);
        server.enqueue(new MockResponse().setBody("{\"errcode\":0,\"ok\":true}"));

        JsonNode resp = httpWithBrokenStore.getOapi(baseUrl() + "/gettoken", "获取access_token");

        assertTrue(resp.path("ok").asBoolean(), "留痕失败不应影响业务调用");
    }

    @Test
    void oversizedBodyShouldBeTruncated() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"errcode\":0}"));

        String big = "{\"userid\":\"u1\",\"pad\":\"" + "x".repeat(5000) + "\"}";
        http.postOapi(baseUrl() + "/topapi/message/corpconversation/asyncsend_v2?access_token=t",
                new ObjectMapper().readTree(big), "发工作通知");

        ApiCallLog log = logStore.last();
        assertEquals(2000 + "...(truncated)".length(), log.getParams().length());
    }
}
