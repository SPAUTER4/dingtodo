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
