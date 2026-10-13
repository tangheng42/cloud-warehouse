package com.yuncang.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemDTO(

        @NotNull
        @Positive
        Long skuId,

        @NotNull
        @Positive
        Long quantity

) {}
