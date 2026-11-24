package org.spauter.dingtodo.dto;

import jakarta.validation.constraints.NotBlank;

/** 按 taskId 直接撤回待办 */
public record TodoWithdrawByTaskRequest(
        @NotBlank(message = "不能为空") String userId,
        @NotBlank(message = "不能为空") String taskId) {
}
