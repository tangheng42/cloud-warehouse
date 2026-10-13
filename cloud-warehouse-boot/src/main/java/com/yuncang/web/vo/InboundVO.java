package com.yuncang.web.vo;

import java.time.OffsetDateTime;
import java.util.List;

public record InboundVO(

        Long id,
        String orderNo,
        String status,
        List<OrderItemVO> items,
        String remark,
        Long createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Long warehouseId,
        Long confirmedBy,
        OffsetDateTime confirmedAt,
        Long cancelledBy,
        OffsetDateTime cancelledAt

) {}
