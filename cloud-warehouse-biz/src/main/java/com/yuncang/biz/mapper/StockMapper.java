package com.yuncang.biz.mapper;

import java.util.List;

import com.yuncang.biz.entity.WarehouseStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 库存查询与库存业务写操作。 */
@Mapper
public interface StockMapper {
    WarehouseStock findByWarehouseAndSku(@Param("warehouseId") Long warehouseId, @Param("skuId") Long skuId);

    /**
     * 锁定指定库存行，调用方须处于事务中。
     * @return 库存行，不含安全库存阈值；不存在时为 null
     */
    WarehouseStock findForUpdate(
            @Param("warehouseId") Long warehouseId,
            @Param("skuId") Long skuId
    );

    List<WarehouseStock> list(@Param("warehouseId") Long warehouseId, @Param("skuId") Long skuId);

    List<WarehouseStock> listBySku(@Param("skuId") Long skuId);

    int inboundUpsert(
            @Param("warehouseId") Long warehouseId,
            @Param("skuId") Long skuId,
            @Param("quantity") Long quantity
    );

    int reserve(
            @Param("warehouseId") Long warehouseId,
            @Param("skuId") Long skuId,
            @Param("quantity") Long quantity
    );

    int release(
            @Param("warehouseId") Long warehouseId,
            @Param("skuId") Long skuId,
            @Param("quantity") Long quantity
    );

    int confirmOutbound(
            @Param("warehouseId") Long warehouseId,
            @Param("skuId") Long skuId,
            @Param("quantity") Long quantity
    );

    int adjustStocktake(@Param("warehouseId") Long warehouseId, @Param("skuId") Long skuId,
            @Param("physical") Long physical, @Param("available") Long available,
            @Param("locked") Long locked, @Param("delta") Long delta);

}
