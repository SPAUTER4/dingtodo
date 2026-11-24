package org.spauter.dingtodo.model;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.spauter.dingtodo.enums.TodoRecordStatus;

/**
 * 待办/通知台账记录（通用化原 ding_user_todo 表，vbillcode → bizId）。
 */
@Entity
@Table(name = "ding_user_todo", indexes = {
        @Index(name = "idx_biz_status", columnList = "biz_id, status"),
        @Index(name = "idx_task_id", columnList = "task_id")
})
public class TodoRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 业务键 */
    @Column(name = "biz_id", length = 64)
    private String bizId;

    /** 钉钉 userId */
    @Column(name = "user_id", length = 64)
    private String userId;

    /** 钉钉待办 taskId */
    @Column(name = "task_id", length = 64)
    private String taskId;

    /** 钉钉待办 requestId */
    @Column(name = "request_id", length = 128)
    private String requestId;

    /** 工作通知 msg task_id（仅 NOTIFY 记录） */
    @Column(name = "msg_task_id")
    private Long msgTaskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private TodoRecordStatus status;

    @Column(name = "create_time")
    private Instant createTime;

    @Column(name = "update_time")
    private Instant updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBizId() {
        return bizId;
    }

    public void setBizId(String bizId) {
        this.bizId = bizId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Long getMsgTaskId() {
        return msgTaskId;
    }

    public void setMsgTaskId(Long msgTaskId) {
        this.msgTaskId = msgTaskId;
    }

    public TodoRecordStatus getStatus() {
        return status;
    }

    public void setStatus(TodoRecordStatus status) {
        this.status = status;
    }

    public Instant getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Instant createTime) {
        this.createTime = createTime;
    }

    public Instant getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Instant updateTime) {
        this.updateTime = updateTime;
    }
}
