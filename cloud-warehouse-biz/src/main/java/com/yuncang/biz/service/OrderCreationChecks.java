package com.yuncang.biz.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.yuncang.biz.entity.Sku;
import com.yuncang.biz.entity.Warehouse;
import com.yuncang.biz.mapper.SkuMapper;
import com.yuncang.biz.mapper.WarehouseMapper;
import com.yuncang.biz.service.command.OrderItemCommand;
import com.yuncang.common.BusinessException;
import com.yuncang.common.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 校验单据依赖的仓库和 SKU，并锁定对应档案行。
 * 调用方须提供覆盖整个业务操作的事务，防止校验后资源被并发停用。
 */
@Component
public class OrderCreationChecks {
    private final WarehouseMapper warehouses;
    private final SkuMapper skus;

    public OrderCreationChecks(WarehouseMapper warehouses, SkuMapper skus) {
        this.warehouses = warehouses;
        this.skus = skus;
    }

    public void warehouse(Long id) {
        Warehouse warehouse = warehouses.findForUpdate(id);
        if (warehouse == null) throw BusinessException.notFound("仓库");
        if (!Boolean.TRUE.equals(warehouse.getEnabled())) {
            throw BusinessException.conflict(ErrorCode.RESOURCE_DISABLED, "仓库已停用");
        }
    }

    public void items(List<OrderItemCommand> items) {

        Set<Long> seen = new HashSet<>();

        for (OrderItemCommand item : items) {

            if (!seen.add(item.skuId())) {
                throw BusinessException.conflict(ErrorCode.DUPLICATE_SKU_LINE,
                        "同一单据不能重复添加相同 SKU"
                );
            }

        }
        seen.stream().sorted().forEach(this::sku);
    }

    public void sku(Long id) {

        Sku sku = skus.findForUpdate(id);

        if (sku == null) {
            throw BusinessException.notFound("SKU");
        }

        if (!Boolean.TRUE.equals(sku.getEnabled())) {
            throw BusinessException.conflict(ErrorCode.RESOURCE_DISABLED,
                    "SKU 已停用"
            );
        }
    }


}
