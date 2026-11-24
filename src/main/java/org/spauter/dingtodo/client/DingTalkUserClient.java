package org.spauter.dingtodo.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.spauter.dingtodo.common.ApiException;
import org.spauter.dingtodo.config.DingTalkProperties;
import org.spauter.dingtodo.config.DingTodoProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 钉钉用户身份：userId → unionId（带缓存）、免登 code → userId。
 */
@Component
public class DingTalkUserClient {

    private final DingTalkProperties dingProps;
    private final DingTodoProperties props;
    private final DingTalkTokenManager tokenManager;
    private final JsonHttp http;

    private final Map<String, UnionIdEntry> unionIdCache = new ConcurrentHashMap<>();

    public DingTalkUserClient(DingTalkProperties dingProps, DingTodoProperties props,
                              DingTalkTokenManager tokenManager, JsonHttp http) {
        this.dingProps = dingProps;
        this.props = props;
        this.tokenManager = tokenManager;
        this.http = http;
    }

    /** 获取 unionId，本地按 TTL 缓存 */
    public String getUnionId(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new ApiException("userId 不能为空");
        }
        UnionIdEntry entry = unionIdCache.get(userId);
        if (entry != null && System.currentTimeMillis() < entry.expireAt) {
            return entry.unionId;
        }
        JsonNode resp = http.postOapi(
                dingProps.oapiBaseUrl() + "/topapi/v2/user/get?access_token=" + tokenManager.getToken(),
                http.newObject()
                        .put("userid", userId)
                        .put("language", "zh_CN"),
                "获取unionId");
        String unionId = resp.path("result").path("unionid").asText();
        if (!StringUtils.hasText(unionId)) {
            throw new ApiException("获取 unionId 失败: userid=" + userId);
        }
        long ttl = Math.max(props.unionIdCacheSeconds(), 0) * 1000;
        unionIdCache.put(userId, new UnionIdEntry(unionId, System.currentTimeMillis() + ttl));
        return unionId;
    }

    /** 钉钉免登：授权 code 换 userId */
    public String getUserIdByCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new ApiException("免登 code 不能为空");
        }
        JsonNode resp = http.postOapi(
                dingProps.oapiBaseUrl() + "/topapi/v2/user/getuserinfo?access_token=" + tokenManager.getToken(),
                http.newObject().put("code", code),
                "免登换userId");
        String userId = resp.path("result").path("userid").asText();
        if (!StringUtils.hasText(userId)) {
            throw new ApiException("免登失败: 未取到 userid");
        }
        return userId;
    }

    private record UnionIdEntry(String unionId, long expireAt) {
    }
}
