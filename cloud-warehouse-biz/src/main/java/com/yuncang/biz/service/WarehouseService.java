package com.yuncang.biz.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.yuncang.biz.entity.Warehouse;
import com.yuncang.biz.mapper.WarehouseMapper;
import com.yuncang.biz.mapper.ActiveOrderMapper;
import com.yuncang.common.BusinessException;
import com.yuncang.common.ErrorCode;
import com.yuncang.common.PageResult;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 维护仓库档案，停用前检查未完成单据，避免中断在途业务。
 */
@Service
public class WarehouseService {
    private final WarehouseMapper mapper;
    private final ActiveOrderMapper activeOrders;

    public WarehouseService(WarehouseMapper mapper, ActiveOrderMapper activeOrders) {
        this.mapper = mapper;
        this.activeOrders = activeOrders;
    }

    public Warehouse get(Long id) {
        Warehouse warehouse = mapper.findById(id);
        if (warehouse == null) throw BusinessException.notFound("仓库");
        return warehouse;
    }

    public PageResult<Warehouse> list(String code, String name, Boolean enabled, int page, int size) {
        PageHelper.startPage(page, size);
        List<Warehouse> items = mapper.list(code, name, enabled);
        return new PageResult<>(items, page, size, new PageInfo<>(items).getTotal());
    }

    @Transactional
    public Warehouse create(String code, String name, String address) {
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(code.trim());
        warehouse.setName(name.trim());
        warehouse.setAddress(address);
        try {
            mapper.insert(warehouse);
        } catch (DuplicateKeyException e) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_CODE, "仓库编码已存在");
        }
        return get(warehouse.getId());
    }

    @Transactional
    public Warehouse update(Long id, String name, String address) {
        Warehouse warehouse = get(id);
        warehouse.setName(name.trim());
        warehouse.setAddress(address);
        mapper.update(warehouse);
        return get(id);
    }

    /**
     * 锁定仓库后变更启用状态；存在未完成单据时拒绝停用，重复设置目标状态时报冲突。
     */
    @Transactional
    public Warehouse setEnabled(Long id, boolean enabled) {
        Warehouse warehouse = mapper.findForUpdate(id);
        if (warehouse == null) throw BusinessException.notFound("仓库");
        if (warehouse.getEnabled() == enabled) {
            throw BusinessException.conflict(ErrorCode.ORDER_STATE_CONFLICT, "仓库已处于目标状态");
        }
        if (!enabled) {
            if (activeOrders.countForWarehouse(id) > 0) {
                throw BusinessException.conflict(ErrorCode.ACTIVE_ORDER_EXISTS, "仓库存在未完成单据");
            }
        }
        if (mapper.setEnabled(id, enabled) != 1) {
            throw BusinessException.conflict(ErrorCode.ORDER_STATE_CONFLICT, "仓库状态已变化");
        }
        return get(id);
    }
}
