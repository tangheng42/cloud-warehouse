package com.yuncang.biz.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.yuncang.biz.entity.SkuCategory;
import com.yuncang.biz.mapper.CategoryMapper;
import com.yuncang.biz.mapper.SkuMapper;
import com.yuncang.common.BusinessException;
import com.yuncang.common.ErrorCode;
import com.yuncang.common.PageResult;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 维护 SKU 分类；分类下仍有启用 SKU 时禁止停用。
 */
@Service
public class CategoryService {
    private final CategoryMapper mapper;
    private final SkuMapper skuMapper;

    public CategoryService(CategoryMapper mapper, SkuMapper skuMapper) {
        this.mapper = mapper;
        this.skuMapper = skuMapper;
    }

    public SkuCategory get(Long id) {
        SkuCategory category = mapper.findById(id);
        if (category == null) throw BusinessException.notFound("分类");
        return category;
    }

    public PageResult<SkuCategory> list(String name, Boolean enabled, int page, int size) {
        PageHelper.startPage(page, size);
        List<SkuCategory> items = mapper.list(name, enabled);
        return new PageResult<>(items, page, size, new PageInfo<>(items).getTotal());
    }

    @Transactional
    public SkuCategory create(String code, String name) {
        SkuCategory category = new SkuCategory();
        category.setCode(code.trim());
        category.setName(name.trim());
        try {
            mapper.insert(category);
        } catch (DuplicateKeyException e) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_CODE, "分类编码已存在");
        }
        return get(category.getId());
    }

    @Transactional
    public SkuCategory update(Long id, String name) {
        SkuCategory category = get(id);
        category.setName(name.trim());
        mapper.update(category);
        return get(id);
    }

    /**
     * 锁定分类后变更启用状态；分类下有启用 SKU 时拒绝停用。
     */
    @Transactional
    public SkuCategory setEnabled(Long id, boolean enabled) {
        SkuCategory category = mapper.findForUpdate(id);
        if (category == null) throw BusinessException.notFound("分类");
        if (category.getEnabled() == enabled) {
            throw BusinessException.conflict(ErrorCode.ORDER_STATE_CONFLICT, "分类已处于目标状态");
        }
        if (!enabled && skuMapper.countEnabledByCategory(id) > 0) {
            throw BusinessException.conflict(ErrorCode.ACTIVE_SKU_EXISTS, "分类下仍有启用的 SKU");
        }
        mapper.setEnabled(id, enabled);
        return get(id);
    }
}
