package org.spauter.dingtodo.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Deque;
import java.util.ArrayDeque;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spauter.dingtodo.common.ApiException;
import org.spauter.dingtodo.config.DingTalkProperties;
import org.spauter.dingtodo.config.DingTodoProperties;
import org.spauter.dingtodo.model.ApiCallLog;
import org.spauter.dingtodo.store.ApiCallLogStore;

class DingTalkUserClientTest {

    /** 测试专用：记录每条留痕 */
    private static class RecordingStore implements ApiCallLogStore {
        final Deque<ApiCallLog> logs = new ArrayDeque<>();

        @Override
        public void save(ApiCallLog log) {
            logs.addLast(log);
        }
    }

    private MockWebServer server;
    private DingTalkUserClient client;
    private RecordingStore logStore;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        DingTalkProperties dingProps = new DingTalkProperties("k", "s", "100",
                server.url("/").toString().replaceAll("/$", ""), "https://api.dingtalk.com", 7000);
        DingTalkTokenManager tokenManager = mock(DingTalkTokenManager.class);
        when(tokenManager.getToken()).thenReturn("tk");
        logStore = new RecordingStore();
        client = new DingTalkUserClient(dingProps,
                new DingTodoProperties(true, "memory", 3600, 0, "0 30 3 * * ?", 5, 20000),
                tokenManager, new JsonHttp(new OkHttpClient(), new ObjectMapper(), logStore));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void getUserIdByMobileShouldPostMobileAndReturnUserId() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"errcode\":0,\"result\":{\"userid\":\"user001\"}}"));

        String userId = client.getUserIdByMobile("13800138000");

        assertEquals("user001", userId);
        RecordedRequest recorded = server.takeRequest();
        assertEquals("/topapi/v2/user/getbymobile?access_token=tk", recorded.getPath());
        String body = recorded.getBody().readUtf8();
        assertEquals("{\"mobile\":\"13800138000\"}", new ObjectMapper().readTree(body).toString());
        assertEquals("手机号查userId", logStore.logs.getLast().getOperation());
        // 请求体只有 mobile，留痕时还不知道 userId，open_id 应为空
        assertNull(logStore.logs.getLast().getOpenId());
    }

    @Test
    void getUserIdByMobileShouldFailWhenUserNotFound() {
        server.enqueue(new MockResponse().setBody(
                "{\"errcode\":60121,\"errmsg\":\"找不到该用户\"}"));

        ApiException e = assertThrows(ApiException.class, () -> client.getUserIdByMobile("13800138000"));
        assertEquals("60121", e.getCode());
    }

    @Test
    void getUserIdByMobileShouldRejectBlankMobile() {
        assertThrows(ApiException.class, () -> client.getUserIdByMobile(" "));
    }

    @Test
    void getUnionIdShouldCacheWithinTtl() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"errcode\":0,\"result\":{\"unionid\":\"union-1\"}}"));

        assertEquals("union-1", client.getUnionId("user001"));
        assertEquals("union-1", client.getUnionId("user001"));
        assertEquals(1, server.getRequestCount());
    }
}
