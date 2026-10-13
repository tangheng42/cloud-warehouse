package com.yuncang.biz.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.yuncang.biz.entity.StockAlert;
import com.yuncang.biz.mapper.StockAlertMapper;
import com.yuncang.common.PageResult;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StockAlertQueryService {
    private final StockAlertMapper mapper;

    public StockAlertQueryService(StockAlertMapper mapper) { this.mapper = mapper; }

    public PageResult<StockAlert> list(Long warehouseId, Long skuId, String status, int page, int size) {
        PageHelper.startPage(page, size);
        List<StockAlert> items = mapper.list(warehouseId, skuId, status);
        return new PageResult<>(items, page, size, new PageInfo<>(items).getTotal());
    }
}
