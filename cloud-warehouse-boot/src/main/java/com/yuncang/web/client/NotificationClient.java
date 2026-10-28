package com.yuncang.web.client;

import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 通知微服务 Feign 客户端
 * 用于调用独立通知服务的内部接口
 */
@FeignClient(name = "yuncang-notification")
public interface NotificationClient {

    @GetMapping("/internal/v1/notifications/page")
    com.yuncang.common.PageResult<com.yuncang.common.notification.NotificationView> page(
            @RequestHeader("X-Internal-Token") String token,
            @org.springframework.web.bind.annotation.RequestParam("page") int page,
            @org.springframework.web.bind.annotation.RequestParam("size") int size);

    /**
     * 查询演示通知
     *
     * @param token 内部服务调用凭据
     * @return 通知服务返回的演示数据
     */
    @GetMapping("/internal/v1/notifications")
    Map<String, Object> getDemoNotifications(
            @RequestHeader("X-Internal-Token") String token
    );
}
