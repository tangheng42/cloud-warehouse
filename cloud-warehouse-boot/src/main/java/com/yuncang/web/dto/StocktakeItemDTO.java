package com.yuncang.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record StocktakeItemDTO(@NotNull @Positive Long skuId,
        @NotNull @PositiveOrZero Long countedPhysical) {}
