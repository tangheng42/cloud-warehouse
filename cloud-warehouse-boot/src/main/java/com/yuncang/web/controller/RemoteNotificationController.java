package com.yuncang.web.controller;

import com.yuncang.common.ApiResponse;
import com.yuncang.common.PageResult;
import com.yuncang.common.notification.NotificationView;
import com.yuncang.web.Responses;
import com.yuncang.web.security.RequireRoles;
import com.yuncang.web.service.RemoteNotificationQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@Profile("microservices")
@Tag(name = "异步通知 V1.1")
@SecurityRequirement(name = "bearerAuth")
@RequireRoles({"ADMIN", "WAREHOUSE_MANAGER"})
@RequestMapping("/api/v1.1/notifications")
public class RemoteNotificationController {
    private final RemoteNotificationQueryService service;
    public RemoteNotificationController(RemoteNotificationQueryService service) { this.service = service; }

    @Operation(summary = "分页查询库存预警通知")
    @GetMapping
    public ApiResponse<PageResult<NotificationView>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Responses.ok(service.listNotifications(page, size));
    }
}
