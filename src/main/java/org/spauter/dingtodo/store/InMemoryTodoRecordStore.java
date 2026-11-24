package org.spauter.dingtodo.store;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.spauter.dingtodo.enums.TodoRecordStatus;
import org.spauter.dingtodo.model.TodoRecord;

/**
 * 内存台账实现：仅用于开发/演示，重启后数据丢失。
 */
public class InMemoryTodoRecordStore implements TodoRecordStore {

    private final List<TodoRecord> records = new ArrayList<>();
    private final AtomicLong idSeq = new AtomicLong();

    @Override
    public synchronized TodoRecord save(TodoRecord record) {
        Instant now = Instant.now();
        if (record.getId() == null) {
            record.setId(idSeq.incrementAndGet());
        }
        if (record.getCreateTime() == null) {
            record.setCreateTime(now);
        }
        record.setUpdateTime(now);
        records.add(record);
        return record;
    }

    @Override
    public synchronized Optional<TodoRecord> findByTaskId(String taskId) {
        return records.stream()
                .filter(r -> taskId.equals(r.getTaskId()))
                .reduce((a, b) -> b);
    }

    @Override
    public synchronized List<TodoRecord> findPushed(String bizId) {
        return records.stream()
                .filter(r -> bizId.equals(r.getBizId()) && r.getStatus() == TodoRecordStatus.PUSHED)
                .toList();
    }

    @Override
    public synchronized Optional<TodoRecord> findLatestNotify(String bizId) {
        return records.stream()
                .filter(r -> bizId.equals(r.getBizId()) && r.getStatus() == TodoRecordStatus.NOTIFY)
                .max(Comparator.comparing(TodoRecord::getCreateTime));
    }

    @Override
    public synchronized void markWithdrawn(TodoRecord record) {
        record.setStatus(TodoRecordStatus.WITHDRAWN);
        record.setUpdateTime(Instant.now());
    }
}
