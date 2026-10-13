package com.yuncang.biz.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class InboundOrderItem {

    private Long id;
    private Long orderId;
    private Long skuId;
    private Long quantity;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
