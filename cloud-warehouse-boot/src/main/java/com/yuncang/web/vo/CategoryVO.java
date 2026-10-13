package com.yuncang.web.vo;

import java.time.OffsetDateTime;

public record CategoryVO(Long id, String code, String name, boolean enabled,
                         OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
