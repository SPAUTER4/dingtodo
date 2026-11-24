package org.spauter.dingtodo.service;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.spauter.dingtodo.common.ApiException;
import org.spauter.dingtodo.model.BatchProgress;
import org.springframework.stereotype.Component;

/**
 * 批量任务进度登记（内存态：重启丢失，历史批次超限淘汰）。
 * 每条推送的持久结果以台账 ding_user_todo 为准，此处仅用于提交后查询执行进度。
 */
@Component
public class BatchProgressTracker {

    /** 内存最多保留的批次数量，超出淘汰最早注册的批次 */
    private static final int MAX_BATCHES = 100;

    private final Object lock = new Object();
    /** LinkedHashMap 保持插入序：队首即最早的批次 */
    private final Map<String, BatchProgress> batches = new LinkedHashMap<>();

    public BatchProgress register(int total) {
        String batchId = UUID.randomUUID().toString();
        synchronized (lock) {
            evictOldestIfNeeded();
            batches.put(batchId, new BatchProgress(batchId, total));
            return batches.get(batchId);
        }
    }

    public BatchProgress get(String batchId) {
        BatchProgress progress = batches.get(batchId);
        if (progress == null) {
            throw new ApiException("批量任务不存在或已被淘汰: " + batchId);
        }
        return progress;
    }

    private void evictOldestIfNeeded() {
        while (batches.size() >= MAX_BATCHES) {
            Iterator<Map.Entry<String, BatchProgress>> it = batches.entrySet().iterator();
            it.next();
            it.remove();
        }
    }
}
