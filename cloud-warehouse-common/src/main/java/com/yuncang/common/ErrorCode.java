package com.yuncang.common;

/** OpenAPI 业务错误码与项目现有通用错误码的统一定义。 */
public enum ErrorCode {
    VALIDATION_ERROR("请求参数不合法", 400),
    UNAUTHENTICATED("请先登录", 401),
    FORBIDDEN("无权执行该操作", 403),
    NOT_FOUND("请求资源不存在", 404),
    INTERNAL_ERROR("服务暂不可用", 500),
    NOTIFICATION_SERVICE_UNAVAILABLE("通知服务暂不可用", 503),
    INVALID_CREDENTIALS("用户名或密码错误", 401),
    USER_DISABLED("用户已停用", 403),
    DUPLICATE_CODE("编码已存在", 409),
    RESOURCE_DISABLED("资源已停用", 409),
    ORDER_STATE_CONFLICT("状态冲突", 409),
    ACTIVE_ORDER_EXISTS("存在未完成单据", 409),
    ACTIVE_SKU_EXISTS("分类下仍有启用的 SKU", 409),
    DUPLICATE_SKU_LINE("同一单据不能重复添加相同 SKU", 409),
    SAME_WAREHOUSE("源仓和目标仓不能相同", 409),
    STOCK_ROW_NOT_FOUND("库存记录不存在", 409),
    INSUFFICIENT_AVAILABLE_STOCK("可用库存不足", 409),
    STOCK_SNAPSHOT_CHANGED("库存快照已变化", 409),
    COUNT_BELOW_LOCKED("实盘数量不能低于锁定库存", 409),
    STOCK_BUSY("库存操作繁忙，请重试", 409),
    IDEMPOTENCY_KEY_CONFLICT("同一幂等键不能用于不同请求", 409),
    NOTIFICATION_RATE_LIMITED("通知查询过于频繁，请稍后重试", 429);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    ErrorCode(String defaultMessage, int httpStatus) {
        this.code = name();
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    public String getCode() { return code; }
    public String getDefaultMessage() { return defaultMessage; }
    public int getHttpStatus() { return httpStatus; }
}
