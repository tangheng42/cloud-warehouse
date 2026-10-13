package com.yuncang.biz.service.command;

public record OrderItemCommand(
        Long skuId,
        Long quantity
) {}
