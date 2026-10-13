package com.yuncang.web.vo;

import java.time.OffsetDateTime;
import java.util.List;

public record TransferVO(Long id, String orderNo, String status, List<OrderItemVO> items,
        String remark, Long createdBy, OffsetDateTime createdAt, OffsetDateTime updatedAt,
        Long sourceWarehouseId, Long targetWarehouseId,
        Long reservedBy, OffsetDateTime reservedAt, Long releasedBy, OffsetDateTime releasedAt,
        Long dispatchedBy, OffsetDateTime dispatchedAt, Long receivedBy, OffsetDateTime receivedAt,
        Long cancelledBy, OffsetDateTime cancelledAt) {}
