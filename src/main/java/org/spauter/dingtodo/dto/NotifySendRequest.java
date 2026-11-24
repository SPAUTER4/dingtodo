package org.spauter.dingtodo.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.spauter.dingtodo.enums.NotifyType;

/**
 * 发工作通知请求。
 *
 * <ul>
 *   <li>ACTION_CARD：必填 title + markdown，可选 singleTitle/singleUrl（跳转按钮）</li>
 *   <li>TEXT：必填 text</li>
 * </ul>
 */
public record NotifySendRequest(
        @NotNull(message = "不能为空") NotifyType type,
        /** 接收人钉钉 userId 列表 */
        @NotEmpty(message = "不能为空") List<String> userIds,
        String title,
        String markdown,
        String singleTitle,
        String singleUrl,
        String text,
        /** 可选业务键，传入且启用台账时记录通知，可配合撤回 */
        String bizId) {
}
