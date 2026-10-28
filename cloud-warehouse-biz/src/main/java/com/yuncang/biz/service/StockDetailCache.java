package com.yuncang.biz.service;

import com.yuncang.biz.entity.WarehouseStock;
import java.util.function.Supplier;

/**
 * 库存详情的可选读取优化；数据库仍为库存事实来源，业务写入不得依赖缓存值。
 */
public interface StockDetailCache {
    WarehouseStock getOrLoad(Long warehouseId, Long skuId, Supplier<WarehouseStock> databaseLoader);
    /**
     * 有事务时在成功提交后失效缓存；无事务时立即失效，避免回滚操作提前改变缓存版本。
     */
    void invalidateAfterCommit(Long warehouseId, Long skuId);
}
