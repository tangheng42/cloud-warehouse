package com.yuncang.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryUpdateDTO(@NotBlank @Size(max = 100) String name) {}
