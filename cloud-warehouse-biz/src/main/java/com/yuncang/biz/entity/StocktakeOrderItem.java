package com.yuncang.biz.entity;

import lombok.Data;

@Data
public class StocktakeOrderItem {
    private Long id;
    private Long orderId;
    private Long skuId;
    private Long snapshotPhysical;
    private Long snapshotAvailable;
    private Long snapshotLocked;
    private Long countedPhysical;
}
