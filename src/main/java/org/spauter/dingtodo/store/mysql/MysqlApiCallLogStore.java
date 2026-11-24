package org.spauter.dingtodo.store.mysql;

import java.time.Instant;
import org.spauter.dingtodo.model.ApiCallLog;
import org.spauter.dingtodo.store.ApiCallLogStore;

/**
 * 留痕 MySQL 持久化实现（dingtodo.record-store=mysql 时启用）。
 */
public class MysqlApiCallLogStore implements ApiCallLogStore {

    private final ApiCallLogRepository repository;

    public MysqlApiCallLogStore(ApiCallLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(ApiCallLog log) {
        if (log.getCreateTime() == null) {
            log.setCreateTime(Instant.now());
        }
        repository.save(log);
    }
}
