package com.yuncang.biz.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class Sku {
    private Long id;
    private String code;
    private String name;
    private Long categoryId;
    private String unit;
    private Long safetyStock;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
