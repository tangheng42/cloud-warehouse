package com.yuncang.biz.entity;

import lombok.Data;

@Data
public class WarehouseStock {
    private Long id;
    private Long warehouseId;
    private Long skuId;
    private Long physicalStock;
    private Long availableStock;
    private Long lockedStock;
    private Long safetyStock;
}
