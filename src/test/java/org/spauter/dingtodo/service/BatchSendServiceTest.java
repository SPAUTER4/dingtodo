package org.spauter.dingtodo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spauter.dingtodo.common.ApiException;
import org.spauter.dingtodo.dto.NotifySendRequest;
import org.spauter.dingtodo.dto.TodoSendRequest;
import org.spauter.dingtodo.enums.NotifyType;
import org.spauter.dingtodo.model.BatchProgress;

class BatchSendServiceTest {

    private DingTodoService dingTodoService;
    private BatchSendService service;

    @BeforeEach
    void setUp() {
        dingTodoService = mock(DingTodoService.class);
        // 同步执行器：提交即执行，便于断言最终进度
        service = new BatchSendService(dingTodoService, new BatchProgressTracker(), Runnable::run);
    }

    private TodoSendRequest item(String bizId) {
        return new TodoSendRequest(bizId, "标题", null, null, null,
                List.of("u1"), null, null, null, null);
    }

    @Test
    void submitTodoBatchShouldTrackSuccessAndFailureIndependently() {
        // 第 2 条失败：逐条互不影响
        doThrow(new ApiException("钉钉调用失败")).when(dingTodoService).sendTodo(item("B2"));

        String batchId = service.submitTodoBatch(List.of(item("B1"), item("B2"), item("B3")));

        BatchProgress progress = service.progress(batchId);
        assertEquals(3, progress.getTotal());
        assertEquals(3, progress.getDone());
        assertEquals(2, progress.getSuccess());
        assertEquals(1, progress.getFailed());
        assertTrue(progress.isFinished());
        assertEquals(1, progress.getFailedItems().size());
        assertTrue(progress.getFailedItems().get(0).startsWith("B2:"));
    }

    @Test
    void submitNotifyBatchShouldTrackProgress() {
        NotifySendRequest notify = new NotifySendRequest(NotifyType.TEXT, List.of("u1"),
                null, null, null, null, "内容", "NB-1");

        String batchId = service.submitNotifyBatch(List.of(notify));

        BatchProgress progress = service.progress(batchId);
        assertEquals(1, progress.getTotal());
        assertEquals(1, progress.getSuccess());
    }

    @Test
    void submitShouldRejectEmptyItems() {
        assertThrows(ApiException.class, () -> service.submitTodoBatch(List.of()));
    }

    @Test
    void progressShouldFailForUnknownBatchId() {
        assertThrows(ApiException.class, () -> service.progress("no-such-batch"));
    }
}
