package com.yuncang.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryCreateDTO(@NotBlank @Size(max = 32) String code,
                                @NotBlank @Size(max = 100) String name) {}
