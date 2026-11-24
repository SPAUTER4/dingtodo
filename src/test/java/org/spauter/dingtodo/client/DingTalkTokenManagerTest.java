package org.spauter.dingtodo.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spauter.dingtodo.config.DingTalkProperties;

class DingTalkTokenManagerTest {

    private MockWebServer server;
    private DingTalkTokenManager manager;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private DingTalkProperties props(long tokenCacheSeconds) {
        String oapi = server.url("/").toString().replaceAll("/$", "");
        return new DingTalkProperties("appkey-x", "appsecret-x", "100", oapi, "https://api.dingtalk.com",
                tokenCacheSeconds);
    }

    @Test
    void getTokenShouldCacheWithinTtl() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"errcode\":0,\"access_token\":\"tk1\",\"expires_in\":7200}"));
        manager = new DingTalkTokenManager(props(7000), new JsonHttp(new OkHttpClient(), new ObjectMapper(), new org.spauter.dingtodo.store.InMemoryApiCallLogStore()));

        assertEquals("tk1", manager.getToken());
        assertEquals("tk1", manager.getToken());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void getTokenShouldRefreshWhenCacheDisabled() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"errcode\":0,\"access_token\":\"tk1\",\"expires_in\":7200}"));
        server.enqueue(new MockResponse().setBody(
                "{\"errcode\":0,\"access_token\":\"tk2\",\"expires_in\":7200}"));
        // 缓存秒数为 0 表示 TTL 立即过期，每次都重新获取
        manager = new DingTalkTokenManager(props(0), new JsonHttp(new OkHttpClient(), new ObjectMapper(), new org.spauter.dingtodo.store.InMemoryApiCallLogStore()));

        assertEquals("tk1", manager.getToken());
        assertEquals("tk2", manager.getToken());
        assertEquals(2, server.getRequestCount());
    }
}
