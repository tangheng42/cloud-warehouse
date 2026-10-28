package com.yuncang.biz.entity;

import lombok.Data;

/**
 * 仓库与 SKU 维度的库存视图；实物库存由可用库存和业务预占的锁定库存组成。
 */
@Data
public class WarehouseStock {
    private Long id;
    private Long warehouseId;
    private Long skuId;
    /**
     * 实物库存，按该 SKU 的库存单位计数。
     */
    private Long physicalStock;
    /**
     * 可用库存，可供新的出库或调拨预占。
     */
    private Long availableStock;
    /**
     * 已被出库或调拨预占的数量，不是数据库行锁数量。
     */
    private Long lockedStock;
    /**
     * SKU 级预警阈值，由查询关联补充；仅查询库存表时可能为 null。
     */
    private Long safetyStock;
}
