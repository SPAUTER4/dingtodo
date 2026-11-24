package org.spauter.dingtodo.dto;

/** 单个执行人的发待办结果 */
public record TodoSendResult(String userId, String unionId, String taskId, String requestId, Long msgTaskId) {
}
