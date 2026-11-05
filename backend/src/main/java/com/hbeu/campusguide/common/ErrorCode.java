package com.hbeu.campusguide.common;

import org.springframework.http.HttpStatus;

/**
 * Stable business error codes shared by public and admin APIs.
 */
public enum ErrorCode {
    BAD_REQUEST(40000, "请求参数不正确", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(40100, "登录状态无效或已过期", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(40300, "没有执行该操作的权限", HttpStatus.FORBIDDEN),
    NOT_FOUND(40400, "请求的资源不存在", HttpStatus.NOT_FOUND),
    CONFLICT(40900, "当前操作与已有数据冲突", HttpStatus.CONFLICT),
    NO_EVIDENCE(42201, "没有找到可核验的校园资料", HttpStatus.UNPROCESSABLE_ENTITY),
    EXTERNAL_SERVICE(50200, "外部 AI 服务暂时不可用", HttpStatus.BAD_GATEWAY),
    INTERNAL_ERROR(50000, "系统内部错误", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
