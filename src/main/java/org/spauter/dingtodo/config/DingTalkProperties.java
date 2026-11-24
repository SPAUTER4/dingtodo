package org.spauter.dingtodo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 钉钉企业内部应用配置。
 *
 * <p>凭证禁止写死在仓库里，请通过环境变量或配置中心注入。
 */
@ConfigurationProperties(prefix = "dingding")
public record DingTalkProperties(
        String appKey,
        String appSecret,
        String agentId,
        /** oapi 旧版接口域名，测试时可指向 MockWebServer */
        @DefaultValue("https://oapi.dingtalk.com") String oapiBaseUrl,
        /** 新版 v1.0 REST 接口域名，测试时可指向 MockWebServer */
        @DefaultValue("https://api.dingtalk.com") String apiBaseUrl,
        /** access_token 本地缓存秒数上限（钉钉官方有效期 7200s） */
        @DefaultValue("7000") long tokenCacheSeconds) {
}
