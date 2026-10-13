package com.yuncang.web.vo;

import java.time.OffsetDateTime;

public record WarehouseVO(Long id, String code, String name, String address, boolean enabled,
                          OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
