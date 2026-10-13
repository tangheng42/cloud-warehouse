package com.yuncang.biz.entity;

import lombok.Data;

@Data
public class TransferOrderItem {
    private Long id;
    private Long orderId;
    private Long skuId;
    private Long quantity;
}
