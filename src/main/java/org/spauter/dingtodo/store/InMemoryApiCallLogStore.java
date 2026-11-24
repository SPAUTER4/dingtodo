package org.spauter.dingtodo.store;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import org.spauter.dingtodo.model.ApiCallLog;

/**
 * 内存留痕实现：保留最近 {@link #capacity} 条，防止长时间运行内存膨胀。
 */
public class InMemoryApiCallLogStore implements ApiCallLogStore {

    private final int capacity;
    private final Deque<ApiCallLog> logs = new ArrayDeque<>();

    public InMemoryApiCallLogStore() {
        this(500);
    }

    public InMemoryApiCallLogStore(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public synchronized void save(ApiCallLog log) {
        if (log.getCreateTime() == null) {
            log.setCreateTime(Instant.now());
        }
        if (logs.size() >= capacity) {
            logs.removeFirst();
        }
        logs.addLast(log);
    }

    public synchronized int size() {
        return logs.size();
    }
}
