package org.spauter.dingtodo.controller;

import jakarta.validation.Valid;
import org.spauter.dingtodo.common.Result;
import org.spauter.dingtodo.dto.BatchSubmitResult;
import org.spauter.dingtodo.dto.NotifyBatchSendRequest;
import org.spauter.dingtodo.dto.TodoBatchSendRequest;
import org.spauter.dingtodo.model.BatchProgress;
import org.spauter.dingtodo.service.BatchSendService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class BatchSendController {

    private final BatchSendService service;

    public BatchSendController(BatchSendService service) {
        this.service = service;
    }

    /** 批量发待办：立即受理返回 batchId，后台逐条异步执行（结果落台账） */
    @PostMapping("/todo/batch/send")
    public Result<BatchSubmitResult> submitTodoBatch(@Valid @RequestBody TodoBatchSendRequest request) {
        String batchId = service.submitTodoBatch(request.items());
        return Result.ok(new BatchSubmitResult(batchId, request.items().size()));
    }

    /** 批量发工作通知：立即受理返回 batchId，后台逐条异步执行 */
    @PostMapping("/notify/batch/send")
    public Result<BatchSubmitResult> submitNotifyBatch(@Valid @RequestBody NotifyBatchSendRequest request) {
        String batchId = service.submitNotifyBatch(request.items());
        return Result.ok(new BatchSubmitResult(batchId, request.items().size()));
    }

    /** 查询批量执行进度：total/done/success/failed + 失败明细（bizId: 原因） */
    @GetMapping("/todo/batch/{batchId}")
    public Result<BatchProgress> batchProgress(@PathVariable String batchId) {
        return Result.ok(service.progress(batchId));
    }
}
