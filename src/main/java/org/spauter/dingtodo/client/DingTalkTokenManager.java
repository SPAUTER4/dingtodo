package org.spauter.dingtodo.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.spauter.dingtodo.common.ApiException;
import org.spauter.dingtodo.config.DingTalkProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * access_token 获取与 TTL 缓存（原项目每次实时请求，无缓存）。
 */
@Component
public class DingTalkTokenManager {

    private final DingTalkProperties props;
    private final JsonHttp http;

    private String cachedToken;
    private long expireAt;

    public DingTalkTokenManager(DingTalkProperties props, JsonHttp http) {
        this.props = props;
        this.http = http;
    }

    public synchronized String getToken() {
        if (cachedToken != null && System.currentTimeMillis() < expireAt) {
            return cachedToken;
        }
        if (!StringUtils.hasText(props.appKey()) || !StringUtils.hasText(props.appSecret())) {
            throw new ApiException("dingding.appkey / dingding.appsecret 未配置");
        }
        String url = props.oapiBaseUrl() + "/gettoken"
                + "?appkey=" + encode(props.appKey())
                + "&appsecret=" + encode(props.appSecret());
        JsonNode resp = http.getOapi(url);
        cachedToken = resp.path("access_token").asText();
        long expiresIn = resp.path("expires_in").asLong(7200);
        // 取官方有效期与配置上限的较小值，并预留 60s 安全边界
        long ttl = Math.min(Math.max(expiresIn - 60, 0), props.tokenCacheSeconds());
        expireAt = System.currentTimeMillis() + ttl * 1000;
        return cachedToken;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
