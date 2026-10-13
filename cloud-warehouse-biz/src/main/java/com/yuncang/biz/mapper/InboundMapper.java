package com.yuncang.biz.mapper;

import java.util.List;

import com.yuncang.biz.entity.InboundOrder;
import com.yuncang.biz.entity.InboundOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InboundMapper {

    int insert(InboundOrder order);

    int insertItem(InboundOrderItem item);

    InboundOrder findById(@Param("id") Long id);

    InboundOrder findForUpdate(@Param("id") Long id);

    List<InboundOrder> list(@Param("warehouseId") Long warehouseId, @Param("status") String status);

    int cancel(@Param("id") Long id, @Param("cancelledBy") Long cancelledBy);

    List<InboundOrderItem> findItemsByOrderId(
            @Param("orderId") Long orderId
    );
    List<InboundOrderItem> findItemsByOrderIds(@Param("orderIds") List<Long> orderIds);

    int complete(
            @Param("id") Long id,
            @Param("confirmedBy") Long confirmedBy
    );
}
