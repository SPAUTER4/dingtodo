package org.spauter.dingtodo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * dingtodo 插件自身行为配置。
 */
@ConfigurationProperties(prefix = "dingtodo")
public record DingTodoProperties(
        /** 是否启用本地待办台账（按业务键撤回依赖台账，关闭后仅支持按 taskId 撤回） */
        @DefaultValue("true") boolean recordEnabled,
        /** 台账存储实现：memory（默认）/ mysql */
        @DefaultValue("memory") String recordStore,
        /** userId → unionId 映射缓存秒数 */
        @DefaultValue("3600") long unionIdCacheSeconds) {
}
