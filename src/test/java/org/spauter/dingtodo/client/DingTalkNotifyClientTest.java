package org.spauter.dingtodo.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spauter.dingtodo.common.ApiException;
import org.spauter.dingtodo.config.DingTalkProperties;

class DingTalkNotifyClientTest {

    private MockWebServer server;
    private DingTalkNotifyClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        DingTalkProperties props = new DingTalkProperties("appkey-x", "appsecret-x", "100",
                server.url("/").toString().replaceAll("/$", ""), "https://api.dingtalk.com", 7000);
        DingTalkTokenManager tokenManager = mock(DingTalkTokenManager.class);
        when(tokenManager.getToken()).thenReturn("tk");
        client = new DingTalkNotifyClient(props, tokenManager,
                new JsonHttp(new OkHttpClient(), new ObjectMapper(), new org.spauter.dingtodo.store.InMemoryApiCallLogStore()));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void sendActionCardShouldPostAsyncSendV2() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"errcode\":0,\"task_id\":777}"));

        long msgTaskId = client.sendActionCard(List.of("u1", "u2"), "标题", "内容", "去处理", "https://x");

        assertEquals(777, msgTaskId);
        RecordedRequest recorded = server.takeRequest();
        assertEquals("/topapi/message/corpconversation/asyncsend_v2?access_token=tk", recorded.getPath());
        JsonNode body = new ObjectMapper().readTree(recorded.getBody().readUtf8());
        assertEquals(100, body.path("agent_id").asLong());
        assertEquals("u1,u2", body.path("userid_list").asText());
        assertEquals("action_card", body.path("msg").path("msgtype").asText());
        assertEquals("去处理", body.path("msg").path("action_card").path("single_title").asText());
        assertEquals("https://x", body.path("msg").path("action_card").path("single_url").asText());
    }

    @Test
    void sendTextShouldPostTextMsg() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"errcode\":0,\"task_id\":888}"));

        long msgTaskId = client.sendText(List.of("u1"), "hello");

        assertEquals(888, msgTaskId);
        RecordedRequest recorded = server.takeRequest();
        JsonNode body = new ObjectMapper().readTree(recorded.getBody().readUtf8());
        assertEquals("text", body.path("msg").path("msgtype").asText());
        assertEquals("hello", body.path("msg").path("text").path("content").asText());
    }

    @Test
    void recallNotifyShouldPostMsgTaskIdAndAgentId() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"errcode\":0}"));

        client.recallNotify(777);

        RecordedRequest recorded = server.takeRequest();
        assertEquals("/topapi/message/corpconversation/recall?access_token=tk", recorded.getPath());
        JsonNode body = new ObjectMapper().readTree(recorded.getBody().readUtf8());
        assertEquals(777, body.path("msg_task_id").asLong());
        assertEquals(100, body.path("agent_id").asLong());
    }

    @Test
    void shouldThrowApiExceptionWhenErrcodeNotZero() {
        server.enqueue(new MockResponse().setBody("{\"errcode\":60020,\"errmsg\":\"IP 不在白名单\"}"));

        ApiException e = assertThrows(ApiException.class, () -> client.sendText(List.of("u1"), "hi"));
        assertEquals("60020", e.getCode());
    }
}
