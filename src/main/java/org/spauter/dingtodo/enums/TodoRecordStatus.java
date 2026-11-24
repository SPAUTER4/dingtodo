package org.spauter.dingtodo.enums;

/**
 * 台账记录状态（对应原项目的 SP/SH/TZ 魔法值）。
 */
public enum TodoRecordStatus {
    /** 待办已推送 */
    PUSHED,
    /** 待办已撤回 */
    WITHDRAWN,
    /** 工作通知记录 */
    NOTIFY
}
