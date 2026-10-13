package com.yuncang.biz.mapper;

import com.yuncang.biz.entity.StocktakeOrder;
import com.yuncang.biz.entity.StocktakeOrderItem;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StocktakeMapper {
    int insert(StocktakeOrder order);
    int insertItem(StocktakeOrderItem item);
    StocktakeOrder findById(@Param("id") Long id);
    StocktakeOrder findForUpdate(@Param("id") Long id);
    List<StocktakeOrder> list(@Param("warehouseId") Long warehouseId, @Param("status") String status);
    List<StocktakeOrderItem> findItemsByOrderId(@Param("orderId") Long orderId);
    List<StocktakeOrderItem> findItemsByOrderIds(@Param("orderIds") List<Long> orderIds);
    int cancel(@Param("id") Long id, @Param("cancelledBy") Long cancelledBy);
    int confirm(@Param("id") Long id, @Param("confirmedBy") Long confirmedBy);
}
