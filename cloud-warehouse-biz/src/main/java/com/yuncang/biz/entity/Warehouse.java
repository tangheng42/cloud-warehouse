package com.yuncang.biz.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class Warehouse {
    private Long id;
    private String code;
    private String name;
    private String address;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
