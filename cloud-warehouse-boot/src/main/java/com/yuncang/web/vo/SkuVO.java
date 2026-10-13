package com.yuncang.web.vo;

import java.time.OffsetDateTime;

public record SkuVO(Long id, String code, String name, Long categoryId, String unit,
                    Long safetyStock, boolean enabled, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
