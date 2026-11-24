package org.spauter.dingtodo.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spauter.dingtodo.config.DingTodoProperties;
import org.spauter.dingtodo.store.mysql.ApiCallLogRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 调用留痕定期清理（仅 mysql 模式装配；内存模式天然限量）。
 *
 * <p>默认关闭（dingtodo.api-log-retention-days=0，留痕永久保留）；
 * 留痕是否清理、保留多久属于审计/合规决策，由接入方按需配置开启。
 */
@Component
@ConditionalOnProperty(name = "dingtodo.record-store", havingValue = "mysql")
public class ApiLogCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(ApiLogCleanupTask.class);

    private final DingTodoProperties props;
    private final ApiCallLogRepository repository;

    public ApiLogCleanupTask(DingTodoProperties props, ApiCallLogRepository repository) {
        this.props = props;
        this.repository = repository;
    }

    @Scheduled(cron = "${dingtodo.api-log-cleanup-cron:0 30 3 * * ?}")
    @Transactional
    public void cleanup() {
        int retentionDays = props.apiLogRetentionDays();
        if (retentionDays <= 0) {
            return;
        }
        Instant before = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        long deleted = repository.deleteByCreateTimeBefore(before);
        if (deleted > 0) {
            log.info("调用留痕清理完成: 删除 {} 天前的 {} 条记录", retentionDays, deleted);
        }
    }
}
