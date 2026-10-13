package com.yuncang.web.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.notifications.enabled", havingValue = "true")
public class NotificationConsumer {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public NotificationConsumer(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @RabbitListener(queues = "${app.notifications.queue:yuncang.stock-alert.notification}")
    @Transactional
    public void receive(String payload) throws JsonProcessingException {
        StockAlertChangedEvent event = json.readValue(payload, StockAlertChangedEvent.class);
        if (event.eventId() == null || event.eventId().isBlank()) {
            throw new IllegalArgumentException("缺少 eventId");
        }
        String active = "ACTIVE".equals(event.newStatus()) ? "LOW_STOCK" : "STOCK_RECOVERED";
        String title = "LOW_STOCK".equals(active) ? "库存不足" : "库存已恢复";
        String content = "仓库 " + event.warehouseId() + " 的 SKU " + event.skuId()
                + " 可用库存 " + event.availableStock() + "，安全库存 " + event.safetyStock();
        try {
            jdbc.update("INSERT INTO notification(event_id,warehouse_id,sku_id,alert_id,type,title,content) "
                    + "VALUES (?,?,?,?,?,?,?)", event.eventId(), event.warehouseId(),
                    event.skuId(), event.alertId(), active, title, content);
        } catch (DuplicateKeyException alreadyConsumed) {
            // A prior delivery committed successfully; the unique event id is the idempotency guard.
        }
    }
}
