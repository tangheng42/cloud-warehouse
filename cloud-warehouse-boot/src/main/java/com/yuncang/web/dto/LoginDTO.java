package com.yuncang.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginDTO(@NotBlank @Size(max = 64) String username, @NotBlank String password) {}
