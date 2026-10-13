package com.yuncang.biz.mapper;

import com.yuncang.biz.entity.TransferOrder;
import com.yuncang.biz.entity.TransferOrderItem;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TransferMapper {
    int insert(TransferOrder order);
    int insertItem(TransferOrderItem item);
    TransferOrder findById(@Param("id") Long id);
    TransferOrder findForUpdate(@Param("id") Long id);
    List<TransferOrder> list(@Param("sourceWarehouseId") Long sourceWarehouseId,
            @Param("targetWarehouseId") Long targetWarehouseId, @Param("status") String status);
    List<TransferOrderItem> findItemsByOrderId(@Param("orderId") Long orderId);
    List<TransferOrderItem> findItemsByOrderIds(@Param("orderIds") List<Long> orderIds);
    int markReserved(@Param("id") Long id, @Param("userId") Long userId);
    int markReleased(@Param("id") Long id, @Param("userId") Long userId);
    int markDispatched(@Param("id") Long id, @Param("userId") Long userId);
    int markReceived(@Param("id") Long id, @Param("userId") Long userId);
    int markCancelled(@Param("id") Long id, @Param("userId") Long userId);
}
