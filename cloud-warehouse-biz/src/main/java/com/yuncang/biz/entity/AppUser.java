package com.yuncang.biz.entity;

import lombok.Data;

@Data
public class AppUser {
    private Long id;
    private String username;
    private String passwordHash;
    private String role;
    private Boolean enabled;
}
