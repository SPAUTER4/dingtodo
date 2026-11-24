package org.spauter.dingtodo.common;

/**
 * 钉钉接口调用失败或本插件使用方式错误时抛出。
 */
public class ApiException extends RuntimeException {

    /** 钉钉错误码（oapi 的 errcode / v1.0 REST 的 code），本地错误为 "local" */
    private final String code;

    public ApiException(String message) {
        this("local", message);
    }

    public ApiException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.code = "local";
    }

    public String getCode() {
        return code;
    }
}
