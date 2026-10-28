package com.yuncang.biz.service;

/**
 * 库存预警状态变化的可选事件出口。启用后在库存事务内持久化事件，再由后台发送。
 */
public interface StockAlertEventSink {
    /**
     * 记录预警状态变化；oldStatus 在首次触发预警时为 null。
     * 调用方须提供业务事务，持久化失败应传播给调用方，防止库存已提交而事件丢失。
     */
    void changed(long alertId, long warehouseId, long skuId, String oldStatus,
            String newStatus, long available, long safety);
}
