package org.spauter.dingtodo.dto;

import jakarta.validation.constraints.NotBlank;

/** 按业务键撤回待办（撤回该 bizId 下所有已推送记录） */
public record TodoWithdrawByBizRequest(@NotBlank(message = "不能为空") String bizId) {
}
