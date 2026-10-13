package com.yuncang.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record StocktakeCreateDTO(@NotNull @Positive Long warehouseId,
        @NotEmpty List<@NotNull @Valid StocktakeItemDTO> items, @Size(max = 500) String remark) {}
