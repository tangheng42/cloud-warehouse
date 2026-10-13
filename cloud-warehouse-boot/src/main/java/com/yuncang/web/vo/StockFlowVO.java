package com.yuncang.web.vo;

import java.time.OffsetDateTime;

public record StockFlowVO(Long id, Long warehouseId, Long skuId, String bizType, Long bizId,
        String flowType, Long deltaPhysical, Long deltaAvailable, Long deltaLocked,
        Long beforePhysical, Long beforeAvailable, Long beforeLocked,
        Long afterPhysical, Long afterAvailable, Long afterLocked,
        Long operatorId, OffsetDateTime createdAt) {}
