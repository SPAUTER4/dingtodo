package org.spauter.dingtodo.dto;

/** 创建待办的钉钉响应 */
public record CreateTaskResult(String taskId, String requestId) {
}
