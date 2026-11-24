package org.spauter.dingtodo.dto;

/** 待办卡片信息 */
public record TodoTaskInfo(String taskId, String subject, String description, Long createTime) {
}
