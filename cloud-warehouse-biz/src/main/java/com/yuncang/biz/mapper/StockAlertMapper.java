package com.yuncang.biz.mapper;

import com.yuncang.biz.entity.StockAlert;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StockAlertMapper {
    StockAlert findByWarehouseAndSku(@Param("warehouseId") Long warehouseId, @Param("skuId") Long skuId);
    int insert(StockAlert alert);
    int update(StockAlert alert);
    List<StockAlert> list(@Param("warehouseId") Long warehouseId,
            @Param("skuId") Long skuId, @Param("status") String status);
}
