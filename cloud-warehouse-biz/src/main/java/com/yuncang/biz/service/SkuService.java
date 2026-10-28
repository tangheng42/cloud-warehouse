package com.yuncang.biz.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.yuncang.biz.entity.Sku;
import com.yuncang.biz.entity.SkuCategory;
import com.yuncang.biz.mapper.CategoryMapper;
import com.yuncang.biz.mapper.SkuMapper;
import com.yuncang.biz.mapper.ActiveOrderMapper;
import com.yuncang.common.BusinessException;
import com.yuncang.common.ErrorCode;
import com.yuncang.common.PageResult;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 维护 SKU 档案及启停约束；安全库存变更时同步刷新已有库存行的预警。
 */
@Service
public class SkuService {
    private final SkuMapper mapper;
    private final CategoryMapper categoryMapper;
    private final ActiveOrderMapper activeOrders;
    private final StockAlertService alerts;

    public SkuService(SkuMapper mapper, CategoryMapper categoryMapper,
            ActiveOrderMapper activeOrders, StockAlertService alerts) {
        this.mapper = mapper;
        this.categoryMapper = categoryMapper;
        this.activeOrders = activeOrders;
        this.alerts = alerts;
    }

    public Sku get(Long id) {
        Sku sku = mapper.findById(id);
        if (sku == null) throw BusinessException.notFound("SKU");
        return sku;
    }

    public PageResult<Sku> list(String code, String name, Long categoryId, Boolean enabled, int page, int size) {
        PageHelper.startPage(page, size);
        List<Sku> items = mapper.list(code, name, categoryId, enabled);
        return new PageResult<>(items, page, size, new PageInfo<>(items).getTotal());
    }

    private void requireActiveCategory(Long categoryId) {
        SkuCategory category = categoryMapper.findForUpdate(categoryId);
        if (category == null) throw BusinessException.notFound("分类");
        if (!Boolean.TRUE.equals(category.getEnabled())) {
            throw BusinessException.conflict(ErrorCode.RESOURCE_DISABLED, "分类已停用");
        }
    }

    @Transactional
    public Sku create(String code, String name, Long categoryId, String unit, Long safetyStock) {
        requireActiveCategory(categoryId);
        Sku sku = new Sku();
        sku.setCode(code.trim());
        sku.setName(name.trim());
        sku.setCategoryId(categoryId);
        sku.setUnit(unit.trim());
        sku.setSafetyStock(safetyStock);
        try {
            mapper.insert(sku);
        } catch (DuplicateKeyException e) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_CODE, "SKU 编码已存在");
        }
        return get(sku.getId());
    }

    /**
     * 更新 SKU 档案及安全库存，并在同一事务中刷新相关库存预警；分类必须启用。
     */
    @Transactional
    public Sku update(Long id, String name, Long categoryId, String unit, Long safetyStock) {
        Sku sku = get(id);
        requireActiveCategory(categoryId);
        sku.setName(name.trim());
        sku.setCategoryId(categoryId);
        sku.setUnit(unit.trim());
        sku.setSafetyStock(safetyStock);
        mapper.update(sku);
        alerts.refreshSku(id);
        return get(id);
    }

    /**
     * 变更 SKU 启用状态；启用时分类必须可用，停用时不得存在未完成单据。
     * 已处于目标状态时抛出状态冲突。
     */
    @Transactional
    public Sku setEnabled(Long id, boolean enabled) {
        Sku sku = mapper.findForUpdate(id);
        if (sku == null) throw BusinessException.notFound("SKU");
        if (sku.getEnabled() == enabled) {
            throw BusinessException.conflict(ErrorCode.ORDER_STATE_CONFLICT, "SKU 已处于目标状态");
        }
        if (enabled) requireActiveCategory(sku.getCategoryId());
        if (!enabled) {
            if (activeOrders.countForSku(id) > 0) {
                throw BusinessException.conflict(ErrorCode.ACTIVE_ORDER_EXISTS, "SKU 存在未完成单据");
            }
        }
        if (mapper.setEnabled(id, enabled) != 1) {
            throw BusinessException.conflict(ErrorCode.ORDER_STATE_CONFLICT, "SKU 状态已变化");
        }
        return get(id);
    }
}
