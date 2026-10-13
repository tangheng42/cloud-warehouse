package com.yuncang.biz.service;

import com.yuncang.biz.entity.StockFlow;
import com.yuncang.biz.entity.WarehouseStock;
import com.yuncang.biz.mapper.StockFlowMapper;
import org.springframework.stereotype.Component;

@Component
public class StockFlowWriter {
    private final StockFlowMapper mapper;

    public StockFlowWriter(StockFlowMapper mapper) { this.mapper = mapper; }

    public void write(String bizType, Long bizId, String flowType, Long operatorId,
            Long warehouseId, Long skuId, long beforePhysical, long beforeAvailable,
            long beforeLocked, WarehouseStock after) {
        StockFlow flow = new StockFlow();
        flow.setWarehouseId(warehouseId);
        flow.setSkuId(skuId);
        flow.setBizType(bizType);
        flow.setBizId(bizId);
        flow.setFlowType(flowType);
        flow.setBeforePhysical(beforePhysical);
        flow.setBeforeAvailable(beforeAvailable);
        flow.setBeforeLocked(beforeLocked);
        flow.setAfterPhysical(after.getPhysicalStock());
        flow.setAfterAvailable(after.getAvailableStock());
        flow.setAfterLocked(after.getLockedStock());
        flow.setDeltaPhysical(after.getPhysicalStock() - beforePhysical);
        flow.setDeltaAvailable(after.getAvailableStock() - beforeAvailable);
        flow.setDeltaLocked(after.getLockedStock() - beforeLocked);
        flow.setOperatorId(operatorId);
        mapper.insert(flow);
    }
}
