package com.yuncang.biz.entity;

import lombok.Data;

@Data
public class OutboundOrderItem {
    private Long id;
    private Long orderId;
    private Long skuId;
    private Long quantity;
}
