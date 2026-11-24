package org.spauter.dingtodo.model;

import java.time.Instant;
import org.spauter.dingtodo.enums.TodoRecordStatus;

/**
 * 待办/通知台账记录（通用化原 ding_user_todo 表，vbillcode → bizId）。
 */
public class TodoRecord {

    private String id;
    /** 业务键 */
    private String bizId;
    /** 钉钉 userId */
    private String userId;
    /** 钉钉待办 taskId */
    private String taskId;
    /** 钉钉待办 requestId */
    private String requestId;
    /** 工作通知 msg task_id（仅 NOTIFY 记录） */
    private Long msgTaskId;
    private TodoRecordStatus status;
    private Instant createTime;
    private Instant updateTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
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
