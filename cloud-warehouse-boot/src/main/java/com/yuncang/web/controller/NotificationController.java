package com.yuncang.web.controller;

import com.yuncang.common.ApiResponse;
import com.yuncang.common.PageResult;
import com.yuncang.web.Responses;
import com.yuncang.web.security.RequireRoles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@org.springframework.context.annotation.Profile("!microservices")
@Validated
@Tag(name = "异步通知 V1.1")
@SecurityRequirement(name = "bearerAuth")
@RequireRoles({"ADMIN", "WAREHOUSE_MANAGER"})
@RequestMapping("/api/v1.1/notifications")
@ConditionalOnProperty(name = "app.notifications.enabled", havingValue = "true")
public class NotificationController {
    private final JdbcTemplate jdbc;

    public NotificationController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record NotificationVO(long id, String eventId, long warehouseId, long skuId,
            long alertId, String type, String title, String content, String status,
            LocalDateTime createdAt) {}

    @Operation(summary = "分页查询库存预警通知")
    @GetMapping
    public ApiResponse<PageResult<NotificationVO>> list(@RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM notification", Long.class);
        List<NotificationVO> rows = jdbc.query("SELECT id,event_id,warehouse_id,sku_id,alert_id,"
                        + "type,title,content,status,created_at FROM notification "
                        + "ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                (rs, n) -> new NotificationVO(rs.getLong(1), rs.getString(2), rs.getLong(3),
                        rs.getLong(4), rs.getLong(5), rs.getString(6), rs.getString(7),
                        rs.getString(8), rs.getString(9), rs.getTimestamp(10).toLocalDateTime()),
                size, (page - 1) * size);
        return Responses.ok(new PageResult<>(rows, page, size, total));
    }
}
