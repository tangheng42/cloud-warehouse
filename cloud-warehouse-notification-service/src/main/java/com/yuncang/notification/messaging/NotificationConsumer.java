package com.yuncang.notification.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuncang.notification.persistence.NotificationMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 将库存预警事件落为站内通知，通过事件 ID 唯一约束避免重复投递产生多条通知。
 */
@Component
@Profile("notification-mysql")
@ConditionalOnProperty(name = "app.notifications.consumer-enabled", havingValue = "true")
public class NotificationConsumer {
    private final NotificationMapper mapper;
    private final ObjectMapper json;
    public NotificationConsumer(NotificationMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    @RabbitListener(queues = "${app.notifications.queue:yuncang.stock-alert.notification}")
    @Transactional
    public void receive(String payload) throws JsonProcessingException {
        StockAlertChangedEvent event = json.readValue(payload, StockAlertChangedEvent.class);
        if (event == null || event.eventId() == null || event.eventId().isBlank()) {
            throw new IllegalArgumentException("Missing eventId");
        }
        String type = "ACTIVE".equals(event.newStatus()) ? "LOW_STOCK" : "STOCK_RECOVERED";
        String title = "LOW_STOCK".equals(type) ? "库存不足" : "库存已恢复";
        String content = "仓库 " + event.warehouseId() + " 的 SKU " + event.skuId()
                + " 可用库存 " + event.availableStock() + "，安全库存 " + event.safetyStock();
        try {
            mapper.insert(event.eventId(), event.warehouseId(), event.skuId(), event.alertId(),
                    type, title, content);
        } catch (DuplicateKeyException duplicate) {
            // 插入时由事件 ID 唯一约束识别重复消息，已提交事件再次投递时无需重复写入。
        }
    }
}
