package com.yuncang.common;

/** 与 OpenAPI 中 ApiResponse 结构一致的统一响应载体。 */
public record ApiResponse<T>(String code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>("OK", "成功", data, traceId);
    }

    public static ApiResponse<Void> error(String code, String message, String traceId) {
        return new ApiResponse<>(code, message, null, traceId);
    }
}
