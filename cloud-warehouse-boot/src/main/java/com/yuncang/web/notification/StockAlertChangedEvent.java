package com.yuncang.web.notification;

import java.time.Instant;

public record StockAlertChangedEvent(String eventId, long alertId, long warehouseId, long skuId,
        String oldStatus, String newStatus, long availableStock, long safetyStock, Instant occurredAt) {}
