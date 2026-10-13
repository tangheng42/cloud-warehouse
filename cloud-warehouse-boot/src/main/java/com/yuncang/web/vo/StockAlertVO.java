package com.yuncang.web.vo;

import java.time.OffsetDateTime;

public record StockAlertVO(Long id, Long warehouseId, Long skuId, Long availableStock,
        Long safetyStock, String status, OffsetDateTime triggeredAt, OffsetDateTime resolvedAt) {}
