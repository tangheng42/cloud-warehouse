package com.yuncang.web.notification;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 扫描已提交的库存预警发件箱，向消息队列发送通知并重试失败记录。
 * 发送成功与数据库标记之间可能中断，消费者必须按事件 ID 去重。
 */
@Component
@ConditionalOnProperty(name = "app.notifications.enabled", havingValue = "true")
public class NotificationOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxPublisher.class);
    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    private final String exchange;

    public NotificationOutboxPublisher(JdbcTemplate jdbc, RabbitTemplate rabbit,
            @Value("${app.notifications.exchange:yuncang.stock-alert}") String exchange) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
        this.exchange = exchange;
    }

    private record Pending(long id, String eventId, String payload) {}

    /**
     * 每轮锁定最多 10 条到期记录，跳过其他实例已锁定的行。
     * 收到确认且消息未被退回才标记 SENT；失败记录延后 5 秒重试。
     */
    @Scheduled(fixedDelayString = "${app.notifications.publish-interval-ms:5000}")
    @Transactional
    public void publishPending() {
        List<Pending> due = jdbc.query("SELECT id, event_id, payload FROM stock_alert_outbox "
                + "WHERE status='PENDING' AND next_retry_at<=NOW(6) "
                + "ORDER BY id LIMIT 10 FOR UPDATE SKIP LOCKED",
                (rs, row) -> new Pending(rs.getLong(1), rs.getString(2), rs.getString(3)));
        for (Pending event : due) {
            try {
                CorrelationData correlation = new CorrelationData(event.eventId());
                rabbit.convertAndSend(exchange, "notification", event.payload(), correlation);
                var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
                if (!confirm.isAck() || correlation.getReturned() != null) {
                    throw new IllegalStateException("MQ 未确认或消息不可路由: " + confirm.getReason());
                }
                jdbc.update("UPDATE stock_alert_outbox SET status='SENT', sent_at=NOW(6) WHERE id=?",
                        event.id());
            } catch (Exception e) {
                log.warn("库存预警通知发送失败，eventId={}，稍后重试", event.eventId(), e);
                jdbc.update("UPDATE stock_alert_outbox SET retry_count=retry_count+1, "
                        + "next_retry_at=DATE_ADD(NOW(6), INTERVAL 5 SECOND) WHERE id=?", event.id());
            }
        }
    }
}
