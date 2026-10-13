package com.yuncang.biz.mapper;

import java.util.List;

import com.yuncang.biz.entity.StockFlow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StockFlowMapper {
    StockFlow findById(@Param("id") Long id);
    List<StockFlow> list(@Param("warehouseId") Long warehouseId, @Param("skuId") Long skuId,
            @Param("bizType") String bizType, @Param("bizId") Long bizId,
            @Param("flowType") String flowType);
    int insert(StockFlow flow);
}
