package com.yuncang.common;

import java.util.Objects;

public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    public BusinessException(ErrorCode errorCode, String customMessage) {
        super(Objects.requireNonNull(customMessage, "customMessage"));
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    /** 兼容旧调用；新代码应直接使用 ErrorCode 构造器。 */
    @Deprecated
    public BusinessException(String code, String message, int status) {
        this(ErrorCode.valueOf(code), message);
        if (errorCode.getHttpStatus() != status) {
            throw new IllegalArgumentException("错误码与 HTTP 状态不匹配: " + code);
        }
    }

    public ErrorCode getErrorCode() { return errorCode; }
    public String getCode() { return errorCode.getCode(); }
    public int getStatus() { return errorCode.getHttpStatus(); }

    public static BusinessException notFound(String resource) {
        return new BusinessException(ErrorCode.NOT_FOUND, resource + "不存在");
    }

    public static BusinessException conflict(ErrorCode code, String message) {
        if (code.getHttpStatus() != 409) {
            throw new IllegalArgumentException("该错误码不是状态冲突: " + code);
        }
        return new BusinessException(code, message);
    }

    /** 兼容旧调用；新代码应传入 ErrorCode。 */
    @Deprecated
    public static BusinessException conflict(String code, String message) {
        return conflict(ErrorCode.valueOf(code), message);
    }
}
