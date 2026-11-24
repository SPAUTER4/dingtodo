package org.spauter.dingtodo.service;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spauter.dingtodo.common.ApiException;
import org.spauter.dingtodo.dto.NotifySendRequest;
import org.spauter.dingtodo.dto.TodoSendRequest;
import org.spauter.dingtodo.model.BatchProgress;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * 批量发待办/通知：提交后立即返回 batchId，后台线程池逐条执行，
 * 每条结果落台账（持久）+ 进度登记（内存）。调用方用 batchId 查询进度与失败明细，
 * 不必阻塞等待——解决 ct-zjjh 同步推送 6000+ 项目负责人导致等待过长的问题。
 */
@Service
@Validated
public class BatchSendService {

    private static final Logger log = LoggerFactory.getLogger(BatchSendService.class);

    private final DingTodoService dingTodoService;
    private final BatchProgressTracker tracker;
    private final Executor batchExecutor;
    private final int queueCapacity;

    public BatchSendService(DingTodoService dingTodoService, BatchProgressTracker tracker,
                            @Qualifier("dingTodoBatchExecutor") Executor batchExecutor) {
        this.dingTodoService = dingTodoService;
        this.tracker = tracker;
        this.batchExecutor = batchExecutor;
        this.queueCapacity = readQueueCapacity(batchExecutor);
    }

    /** 批量发待办：立即返回 batchId，逐条异步执行（每条独立成功/失败，互不影响） */
    public String submitTodoBatch(List<@Valid TodoSendRequest> items) {
        return submit(items, this::sendOneTodo);
    }

    /** 批量发工作通知：立即返回 batchId，逐条异步执行 */
    public String submitNotifyBatch(List<@Valid NotifySendRequest> items) {
        return submit(items, this::sendOneNotify);
    }

    /** 查询批量执行进度（含失败明细） */
    public BatchProgress progress(String batchId) {
        return tracker.get(batchId);
    }

    private String submit(List<?> items, Consumer<Object> task) {
        if (items == null || items.isEmpty()) {
            throw new ApiException("批量提交内容不能为空");
        }
        if (items.size() > queueCapacity) {
            throw new ApiException(
                    "一次提交 " + items.size() + " 条超过队列容量 " + queueCapacity + "，请分批提交");
        }
        BatchProgress batchProgress = tracker.register(items.size());
        String batchId = batchProgress.getBatchId();
        for (Object item : items) {
            try {
                batchExecutor.execute(() -> executeOne(batchId, item, task));
            } catch (RejectedExecutionException e) {
                // CallerRuns 策略下几乎不会发生；防御兜底
                tracker.get(batchId).markFailure(bizIdOf(item), "队列拒绝: " + e.getMessage());
            }
        }
        log.info("批量任务已提交: batchId={}, total={}", batchId, items.size());
        return batchId;
    }

    private void executeOne(String batchId, Object item, Consumer<Object> task) {
        try {
            task.accept(item);
            tracker.get(batchId).markSuccess();
        } catch (Exception e) {
            log.warn("批量任务单条失败: batchId={}, bizId={}, reason={}",
                    batchId, bizIdOf(item), e.getMessage());
            tracker.get(batchId).markFailure(bizIdOf(item), e.getMessage());
        }
    }

    private void sendOneTodo(Object item) {
        dingTodoService.sendTodo((TodoSendRequest) item);
    }

    private void sendOneNotify(Object item) {
        dingTodoService.sendNotify((NotifySendRequest) item);
    }

    private String bizIdOf(Object item) {
        if (item instanceof TodoSendRequest req) {
            return req.bizId();
        }
        if (item instanceof NotifySendRequest req && StringUtils.hasText(req.bizId())) {
            return req.bizId();
        }
        return "index-unknown";
    }

    /** 从线程池反查队列总容量，作为单次提交数量上限（队列剩余容量会随消费变化，故取总容量） */
    private int readQueueCapacity(Executor executor) {
        if (executor instanceof ThreadPoolTaskExecutor pool) {
            return pool.getThreadPoolExecutor().getQueue().size()
                    + pool.getThreadPoolExecutor().getQueue().remainingCapacity();
        }
        return Integer.MAX_VALUE;
    }
}
