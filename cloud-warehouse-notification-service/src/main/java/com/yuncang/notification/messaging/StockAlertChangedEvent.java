package com.yuncang.notification.messaging;

import java.time.Instant;

/** JSON contract published by the warehouse transactional outbox. */
public record StockAlertChangedEvent(String eventId, long alertId, long warehouseId, long skuId,
        String oldStatus, String newStatus, long availableStock, long safetyStock, Instant occurredAt) {}
