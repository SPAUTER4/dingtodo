package org.spauter.dingtodo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.spauter.dingtodo.common.ApiException;
import org.spauter.dingtodo.model.BatchProgress;

class BatchProgressTrackerTest {

    private final BatchProgressTracker tracker = new BatchProgressTracker();

    @Test
    void registerAndGetShouldWork() {
        BatchProgress progress = tracker.register(2);

        assertEquals(2, progress.getTotal());
        assertFalse(progress.isFinished());
        assertEquals(progress.getTotal(), tracker.get(progress.getBatchId()).getTotal());
    }

    @Test
    void unknownBatchIdShouldFail() {
        assertThrows(ApiException.class, () -> tracker.get("missing"));
    }

    @Test
    void oldestBatchShouldBeEvictedWhenOverLimit() {
        String first = tracker.register(1).getBatchId();
        // 超过 MAX_BATCHES=100 的注册会淘汰最早的批次
        for (int i = 0; i < 100; i++) {
            tracker.register(1);
        }

        assertThrows(ApiException.class, () -> tracker.get(first));
        // 最新批次仍可查
        assertEquals(1, tracker.register(1).getTotal());
    }
}
