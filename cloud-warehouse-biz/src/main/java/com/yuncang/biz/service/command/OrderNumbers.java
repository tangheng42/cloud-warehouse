package com.yuncang.biz.service.command;

import java.util.UUID;

/** 简单的全局唯一单号；各单据使用不同前缀。 */
public final class OrderNumbers {
    private OrderNumbers() {}

    public static String next(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }
}
