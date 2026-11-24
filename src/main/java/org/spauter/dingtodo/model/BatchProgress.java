package org.spauter.dingtodo.model;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 批量任务进度（内存态，重启丢失；每条明细以台账为准）。
 */
public class BatchProgress {

    private final String batchId;
    private final int total;
    private final AtomicInteger done = new AtomicInteger();
    private final AtomicInteger success = new AtomicInteger();
    /** 失败条目的 bizId + 原因摘要（bizId: err） */
    private final List<String> failedItems = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    public BatchProgress(String batchId, int total) {
        this.batchId = batchId;
        this.total = total;
    }

    public void markSuccess() {
        success.incrementAndGet();
        done.incrementAndGet();
    }

    public void markFailure(String bizId, String reason) {
        failedItems.add(bizId + ": " + (reason == null ? "unknown" : reason));
        done.incrementAndGet();
    }

    public String getBatchId() {
        return batchId;
    }

    public int getTotal() {
        return total;
    }

    public int getDone() {
        return done.get();
    }

    public int getSuccess() {
        return success.get();
    }

    public int getFailed() {
        return done.get() - success.get();
    }

    public List<String> getFailedItems() {
        return List.copyOf(failedItems);
    }

    public boolean isFinished() {
        return done.get() >= total;
    }
}
