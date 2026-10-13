package com.yuncang.biz.service;

/** Persists a noncritical notification event within the inventory transaction when enabled. */
public interface StockAlertEventSink {
    void changed(long alertId, long warehouseId, long skuId, String oldStatus,
            String newStatus, long available, long safety);
}
