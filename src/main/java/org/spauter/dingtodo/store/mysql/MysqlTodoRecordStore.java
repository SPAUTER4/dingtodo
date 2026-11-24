package org.spauter.dingtodo.store.mysql;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.spauter.dingtodo.enums.TodoRecordStatus;
import org.spauter.dingtodo.model.TodoRecord;
import org.spauter.dingtodo.store.TodoRecordStore;
import org.springframework.util.StringUtils;

/**
 * 台账 MySQL 持久化实现（dingtodo.record-store=mysql 时启用）。
 */
public class MysqlTodoRecordStore implements TodoRecordStore {

    private final TodoRecordRepository repository;

    public MysqlTodoRecordStore(TodoRecordRepository repository) {
        this.repository = repository;
    }

    @Override
    public TodoRecord save(TodoRecord record) {
        Instant now = Instant.now();
        if (record.getCreateTime() == null) {
            record.setCreateTime(now);
        }
        record.setUpdateTime(now);
        return repository.save(record);
    }

    @Override
    public Optional<TodoRecord> findByTaskId(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            return Optional.empty();
        }
        return repository.findTopByTaskIdOrderByIdDesc(taskId);
    }

    @Override
    public List<TodoRecord> findPushed(String bizId) {
        return repository.findByBizIdAndStatus(bizId, TodoRecordStatus.PUSHED);
    }

    @Override
    public Optional<TodoRecord> findLatestNotify(String bizId) {
        return repository.findTopByBizIdAndStatusOrderByCreateTimeDesc(bizId, TodoRecordStatus.NOTIFY);
    }

    @Override
    public void markWithdrawn(TodoRecord record) {
        record.setStatus(TodoRecordStatus.WITHDRAWN);
        record.setUpdateTime(Instant.now());
        repository.save(record);
    }
}
