package com.yuncang.biz.mapper;

import java.util.List;

import com.yuncang.biz.entity.OutboundOrder;
import com.yuncang.biz.entity.OutboundOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OutboundMapper {
    int insert(OutboundOrder order);
    int insertItem(OutboundOrderItem item);
    OutboundOrder findById(@Param("id") Long id);
    List<OutboundOrder> list(@Param("warehouseId") Long warehouseId, @Param("status") String status);
    List<OutboundOrderItem> findItemsByOrderId(@Param("orderId") Long orderId);
    List<OutboundOrderItem> findItemsByOrderIds(@Param("orderIds") List<Long> orderIds);
    OutboundOrder findForUpdate(@Param("id") Long id);

    int markReserved(
            @Param("id") Long id,
            @Param("reservedBy") Long reservedBy
    );

    int markReleased(
            @Param("id") Long id,
            @Param("releasedBy") Long releasedBy
    );

    int markCompleted(
            @Param("id") Long id,
            @Param("confirmedBy") Long confirmedBy
    );
    int markCancelled(@Param("id") Long id, @Param("cancelledBy") Long cancelledBy);
}

