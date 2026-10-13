package com.yuncang.biz.entity;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class OutboundOrder {
    private Long id;
    private String orderNo;
    private Long warehouseId;
    private String status;
    private String remark;
    private Long createdBy;
    private Long reservedBy;
    private LocalDateTime reservedAt;
    private Long releasedBy;
    private LocalDateTime releasedAt;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private Long cancelledBy;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<OutboundOrderItem> items;
}
