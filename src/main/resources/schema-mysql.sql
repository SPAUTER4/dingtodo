-- 钉钉待办/通知推送台账（生产建表脚本；开发期可依赖 ddl-auto=update 自动建表）
CREATE TABLE IF NOT EXISTS ding_user_todo (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    biz_id      VARCHAR(64)  NULL COMMENT '业务键（如单据号）',
    user_id     VARCHAR(64)  NULL COMMENT '钉钉 userId',
    task_id     VARCHAR(64)  NULL COMMENT '钉钉待办 taskId',
    request_id  VARCHAR(128) NULL COMMENT '钉钉待办 requestId',
    msg_task_id BIGINT       NULL COMMENT '工作通知 msg task_id（仅通知记录）',
    status      VARCHAR(16)  NOT NULL COMMENT 'PUSHED/WITHDRAWN/NOTIFY',
    create_time DATETIME     NULL,
    update_time DATETIME     NULL,
    PRIMARY KEY (id),
    INDEX idx_biz_status (biz_id, status),
    INDEX idx_task_id (task_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='钉钉待办推送台账';

-- 钉钉 API 调用留痕（所有经 JsonHttp 的调用自动记录；不存明文 access_token）
CREATE TABLE IF NOT EXISTS ding_todo_api (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    open_id     VARCHAR(64)  NULL COMMENT '相关钉钉 userId（从请求体提取）',
    ding_url    VARCHAR(512) NULL COMMENT '请求 URL（access_token 已脱敏）',
    operation   VARCHAR(64)  NULL COMMENT '操作名（如 创建待办/撤回通知）',
    method      VARCHAR(8)   NULL COMMENT 'HTTP 方法',
    params      TEXT         NULL COMMENT '请求体 JSON（超长截断）',
    result      TEXT         NULL COMMENT '响应体 JSON（超长截断）',
    success     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '业务是否成功',
    err_msg     VARCHAR(255) NULL COMMENT '失败摘要',
    elapsed_ms  INT          NULL COMMENT '耗时毫秒',
    create_time DATETIME     NULL,
    PRIMARY KEY (id),
    INDEX idx_create_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='钉钉 API 调用留痕';
