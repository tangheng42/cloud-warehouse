package com.yuncang.biz.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.yuncang.biz.entity.Sku;
import com.yuncang.biz.entity.WarehouseStock;
import com.yuncang.biz.mapper.SkuMapper;
import com.yuncang.biz.mapper.StockMapper;
import com.yuncang.biz.mapper.WarehouseMapper;
import com.yuncang.common.BusinessException;
import com.yuncang.common.PageResult;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StockQueryService {
    private final StockMapper mapper;
    private final WarehouseMapper warehouseMapper;
    private final SkuMapper skuMapper;
    private final StockDetailCache cache;

    public StockQueryService(StockMapper mapper, WarehouseMapper warehouseMapper, SkuMapper skuMapper,
            StockDetailCache cache) {
        this.mapper = mapper;
        this.warehouseMapper = warehouseMapper;
        this.skuMapper = skuMapper;
        this.cache = cache;
    }

    public PageResult<WarehouseStock> list(Long warehouseId, Long skuId, int page, int size) {
        PageHelper.startPage(page, size);
        List<WarehouseStock> items = mapper.list(warehouseId, skuId);
        return new PageResult<>(items, page, size, new PageInfo<>(items).getTotal());
    }

    public WarehouseStock get(Long warehouseId, Long skuId) {
        return cache.getOrLoad(warehouseId, skuId, () -> loadFromDatabase(warehouseId, skuId));
    }

    private WarehouseStock loadFromDatabase(Long warehouseId, Long skuId) {
        if (warehouseMapper.findById(warehouseId) == null) throw BusinessException.notFound("仓库");
        Sku sku = skuMapper.findById(skuId);
        if (sku == null) throw BusinessException.notFound("SKU");
        WarehouseStock stock = mapper.findByWarehouseAndSku(warehouseId, skuId);
        if (stock != null) return stock;
        WarehouseStock empty = new WarehouseStock();
        empty.setWarehouseId(warehouseId);
        empty.setSkuId(skuId);
        empty.setPhysicalStock(0L);
        empty.setAvailableStock(0L);
        empty.setLockedStock(0L);
        empty.setSafetyStock(sku.getSafetyStock());
        return empty;
    }
}
