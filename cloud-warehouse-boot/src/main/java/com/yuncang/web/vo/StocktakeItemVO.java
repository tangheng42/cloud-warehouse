package com.yuncang.web.vo;

public record StocktakeItemVO(Long skuId, Long snapshotPhysical, Long snapshotAvailable,
        Long snapshotLocked, Long countedPhysical) {}
