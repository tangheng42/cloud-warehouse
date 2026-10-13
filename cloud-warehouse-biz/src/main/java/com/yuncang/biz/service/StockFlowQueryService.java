package com.yuncang.biz.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.yuncang.biz.entity.StockFlow;
import com.yuncang.biz.mapper.StockFlowMapper;
import com.yuncang.common.BusinessException;
import com.yuncang.common.PageResult;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StockFlowQueryService {
    private final StockFlowMapper mapper;

    public StockFlowQueryService(StockFlowMapper mapper) { this.mapper = mapper; }

    public StockFlow get(Long id) {
        StockFlow flow = mapper.findById(id);
        if (flow == null) throw BusinessException.notFound("库存流水");
        return flow;
    }

    public PageResult<StockFlow> list(Long warehouseId, Long skuId, String bizType,
            Long bizId, String flowType, int page, int size) {
        PageHelper.startPage(page, size);
        List<StockFlow> items = mapper.list(warehouseId, skuId, bizType, bizId, flowType);
        return new PageResult<>(items, page, size, new PageInfo<>(items).getTotal());
    }
}
