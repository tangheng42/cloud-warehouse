package com.yuncang.web.controller;

import java.util.Map;

import com.yuncang.common.ApiResponse;
import com.yuncang.web.Responses;
import com.yuncang.web.security.RequireRoles;
import com.yuncang.web.service.RemoteNotificationQueryService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 微服务学习专用接口
 * 通过 OpenFeign 查询通知服务的演示数据
 */
@RestController
@Profile("learning")
@RequestMapping("/api/learning/notifications")
@RequireRoles({"ADMIN", "WAREHOUSE_MANAGER"})
public class LearningNotificationController {

    private final RemoteNotificationQueryService notificationService;

    public LearningNotificationController(
            RemoteNotificationQueryService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list() {

        // 调用 Service，Service 再通过 OpenFeign 调用通知服务
        return Responses.ok(
                notificationService.listLearningNotifications()
        );
    }
}