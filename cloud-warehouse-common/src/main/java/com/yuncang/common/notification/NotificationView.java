package com.yuncang.common.notification;

import java.time.LocalDateTime;

/** Wire contract only; contains no persistence or warehouse implementation. */
public record NotificationView(long id, String eventId, long warehouseId, long skuId,
        long alertId, String type, String title, String content, String status,
        LocalDateTime createdAt) {}
