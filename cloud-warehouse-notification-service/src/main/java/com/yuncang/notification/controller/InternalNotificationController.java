package com.yuncang.notification.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知微服务内部查询接口
 * 当前只返回演示数据，不访问真实数据库
 */
@RestController
@RequestMapping("/internal/v1/notifications")
public class InternalNotificationController {

    // 从环境变量读取服务调用凭据，不在代码中硬编码密码
    @Value("${app.internal.token:}")
    private String internalToken;

    @GetMapping
    public ResponseEntity<?> list(
            @RequestHeader(value = "X-Internal-Token",
                    required = false) String token) throws InterruptedException {

        // 未配置凭据或凭据不正确时，拒绝访问
        if (internalToken.isBlank() || !internalToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Unauthorized"));
        }


        // 模拟通知数据，后续再实现真实分页查询
        return ResponseEntity.ok(Map.of(
                "demo", true,
                "records", List.of(
                        Map.of(
                                "id", 1,
                                "title", "库存预警",
                                "content", "这是一条学习演示通知"
                        )
                )
        ));
    }
}