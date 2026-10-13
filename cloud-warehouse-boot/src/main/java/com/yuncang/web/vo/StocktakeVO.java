package com.yuncang.web.vo;

import java.time.OffsetDateTime;
import java.util.List;

public record StocktakeVO(Long id, String orderNo, String status, List<StocktakeItemVO> items,
        String remark, Long createdBy, OffsetDateTime createdAt, OffsetDateTime updatedAt,
        Long warehouseId, Long confirmedBy, OffsetDateTime confirmedAt,
        Long cancelledBy, OffsetDateTime cancelledAt) {}
