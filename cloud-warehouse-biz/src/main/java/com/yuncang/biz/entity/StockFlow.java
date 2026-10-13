package com.yuncang.biz.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class StockFlow {
    private Long id;
    private Long warehouseId;
    private Long skuId;
    private String bizType;
    private Long bizId;
    private String flowType;
    private Long deltaPhysical;
    private Long deltaAvailable;
    private Long deltaLocked;
    private Long beforePhysical;
    private Long beforeAvailable;
    private Long beforeLocked;
    private Long afterPhysical;
    private Long afterAvailable;
    private Long afterLocked;
    private Long operatorId;
    private LocalDateTime createdAt;
}
