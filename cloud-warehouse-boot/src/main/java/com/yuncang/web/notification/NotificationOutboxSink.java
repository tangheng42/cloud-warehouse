package com.yuncang.web.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuncang.biz.service.StockAlertEventSink;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 在库存业务事务中保存预警事件到发件箱，由后台发布器负责实际发送。
 * 调用方须保持预警变更与发件箱插入处于同一事务，避免通知与库存不一致。
 */
@Component
@ConditionalOnProperty(name = "app.notifications.enabled", havingValue = "true")
public class NotificationOutboxSink implements StockAlertEventSink {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public NotificationOutboxSink(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void changed(long alertId, long warehouseId, long skuId, String oldStatus,
            String newStatus, long available, long safety) {
        StockAlertChangedEvent event = new StockAlertChangedEvent(UUID.randomUUID().toString(),
                alertId, warehouseId, skuId, oldStatus, newStatus, available, safety, Instant.now());
        try {
            jdbc.update("INSERT INTO stock_alert_outbox(event_id, payload) VALUES (?, ?)",
                    event.eventId(), json.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化库存预警事件", e);
        }
    }
}
