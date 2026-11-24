package org.spauter.dingtodo.dto;

/** 批量提交受理结果：用 batchId 调 GET /todo/batch/{batchId} 查询进度与失败明细 */
public record BatchSubmitResult(String batchId, int total) {
}
