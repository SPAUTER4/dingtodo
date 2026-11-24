package org.spauter.dingtodo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** 批量发待办请求：每项与单个发送入参一致，提交后异步执行 */
public record TodoBatchSendRequest(
        @NotEmpty(message = "不能为空") List<@Valid TodoSendRequest> items) {
}
