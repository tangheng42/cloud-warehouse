package com.yuncang.biz.entity;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class TransferOrder {
    private Long id;
    private String orderNo;
    private Long sourceWarehouseId;
    private Long targetWarehouseId;
    private String status;
    private String remark;
    private Long createdBy;
    private Long reservedBy;
    private LocalDateTime reservedAt;
    private Long releasedBy;
    private LocalDateTime releasedAt;
    private Long dispatchedBy;
    private LocalDateTime dispatchedAt;
    private Long receivedBy;
    private LocalDateTime receivedAt;
    private Long cancelledBy;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<TransferOrderItem> items;
}
