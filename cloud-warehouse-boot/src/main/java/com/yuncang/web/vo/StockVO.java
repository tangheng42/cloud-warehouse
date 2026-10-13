package com.yuncang.web.vo;

public record StockVO(Long warehouseId, Long skuId, boolean exists, Long physicalStock,
                      Long availableStock, Long lockedStock, Long safetyStock) {}
