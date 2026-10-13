package com.yuncang.biz.service;

import com.yuncang.biz.entity.WarehouseStock;
import java.util.function.Supplier;

/** Optional read optimization. MySQL remains the source of truth. */
public interface StockDetailCache {
    WarehouseStock getOrLoad(Long warehouseId, Long skuId, Supplier<WarehouseStock> databaseLoader);
    void invalidateAfterCommit(Long warehouseId, Long skuId);
}
