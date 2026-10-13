package com.yuncang.web.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record InboundCreateDTO(

        @NotNull
        @Positive
        Long warehouseId,

        @NotEmpty
        List<@NotNull @Valid OrderItemDTO> items,

        @Size(max = 500)
        String remark

) {}
