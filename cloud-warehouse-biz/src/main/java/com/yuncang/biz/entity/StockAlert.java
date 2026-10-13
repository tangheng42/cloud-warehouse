package com.yuncang.biz.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class StockAlert {
    private Long id;
    private Long warehouseId;
    private Long skuId;
    private Long availableStock;
    private Long safetyStock;
    private String status;
    private LocalDateTime triggeredAt;
    private LocalDateTime resolvedAt;
}
