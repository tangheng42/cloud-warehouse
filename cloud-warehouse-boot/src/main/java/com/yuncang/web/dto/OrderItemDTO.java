package com.yuncang.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 单据明细。quantity 按 SKU 的库存单位计数且必须大于零；同一单据不可重复提交相同 SKU。
 */
public record OrderItemDTO(

        @NotNull
        @Positive
        Long skuId,

        @NotNull
        @Positive
        Long quantity

) {}
