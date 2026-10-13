package com.yuncang.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record SkuCreateDTO(@NotBlank @Size(max = 64) String code,
                           @NotBlank @Size(max = 100) String name,
                           @NotNull @Positive Long categoryId,
                           @NotBlank @Size(max = 16) String unit,
                           @NotNull @PositiveOrZero Long safetyStock) {}
