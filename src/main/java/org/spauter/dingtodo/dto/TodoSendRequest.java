package org.spauter.dingtodo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 发待办请求。
 *
 * <p>executorIds / participantIds 传钉钉 userId（插件内部转换为 unionId）。
 * appUrl 建议传钉钉深链（dingtalk://dingtalkclient/page/link?url=...&amp;pc_slide=true）。
 */
public record TodoSendRequest(
        /** 业务键（如单据号），按业务键撤回时必填，同一 bizId 重复发送时由接入方自行控制 */
        @NotBlank(message = "不能为空") String bizId,
        /** 待办标题 */
        @NotBlank(message = "不能为空") String subject,
        String description,
        /** 移动端跳转地址 */
        String appUrl,
        /** PC 端跳转地址 */
        String pcUrl,
        /** 执行人（待办责任人）钉钉 userId 列表 */
        @NotEmpty(message = "不能为空") List<String> executorIds,
        /** 参与人钉钉 userId 列表，可选 */
        List<String> participantIds,
        /** 是否同时发一条工作通知提醒（需传 appUrl 作为跳转按钮） */
        Boolean sendNotify,
        /** 工作通知标题，缺省用 subject */
        String notifyTitle,
        /** 工作通知内容（markdown），缺省用 description */
        String notifyContent) {
}
