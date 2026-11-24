package org.spauter.dingtodo.model;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 钉钉 API 调用留痕（通用化原 ding_todo_api 表）。
 *
 * <p>与原项目的差异：不落明文 access_token（URL 中 token 脱敏），新增 method/success/err_msg/elapsed_ms。
 */
@Entity
@Table(name = "ding_todo_api", indexes = {
        @Index(name = "idx_create_time", columnList = "create_time")
})
public class ApiCallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 相关钉钉 userId（从请求体 userid/userid_list 提取，供排查） */
    @Column(name = "open_id", length = 64)
    private String openId;

    /** 请求 URL（access_token 已脱敏） */
    @Column(name = "ding_url", length = 512)
    private String dingUrl;

    /** 操作名（如 创建待办 / 撤回通知） */
    @Column(name = "operation", length = 64)
    private String operation;

    /** HTTP 方法 */
    @Column(name = "method", length = 8)
    private String method;

    /** 请求体 JSON（超长截断） */
    @Column(name = "params", columnDefinition = "TEXT")
    private String params;

    /** 响应体 JSON（超长截断） */
    @Column(name = "result", columnDefinition = "TEXT")
    private String result;

    /** 业务是否成功（oapi errcode=0 / v1.0 HTTP 2xx） */
    @Column(name = "success", nullable = false)
    private boolean success;

    /** 失败摘要（errcode/errmsg 或异常信息） */
    @Column(name = "err_msg", length = 255)
    private String errMsg;

    /** 耗时毫秒 */
    @Column(name = "elapsed_ms")
    private Integer elapsedMs;

    @Column(name = "create_time")
    private Instant createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOpenId() {
        return openId;
    }

    public void setOpenId(String openId) {
        this.openId = openId;
    }

    public String getDingUrl() {
        return dingUrl;
    }

    public void setDingUrl(String dingUrl) {
        this.dingUrl = dingUrl;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.params = params;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrMsg() {
        return errMsg;
    }

    public void setErrMsg(String errMsg) {
        this.errMsg = errMsg;
    }

    public Integer getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(Integer elapsedMs) {
        this.elapsedMs = elapsedMs;
    }

    public Instant getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Instant createTime) {
        this.createTime = createTime;
    }
}
