package com.yuncang.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 盘点明细。countedPhysical 为实盘总数量，不是相对账面库存的增减量；允许为零。
 */
public record StocktakeItemDTO(@NotNull @Positive Long skuId,
        @NotNull @PositiveOrZero Long countedPhysical) {}
