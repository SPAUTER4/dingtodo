package org.spauter.dingtodo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** 批量发工作通知请求 */
public record NotifyBatchSendRequest(
        @NotEmpty(message = "不能为空") List<@Valid NotifySendRequest> items) {
}
