package org.spauter.dingtodo.dto;

import jakarta.validation.constraints.NotNull;

/** 撤回工作通知 */
public record NotifyWithdrawRequest(@NotNull(message = "不能为空") Long msgTaskId) {
}
