package com.yuncang;

import com.yuncang.biz.service.CategoryService;
import com.yuncang.biz.service.InboundService;
import com.yuncang.biz.service.SkuService;
import com.yuncang.biz.service.WarehouseService;
import com.yuncang.biz.service.UserService;
import com.yuncang.web.security.JwtService;
import com.yuncang.biz.service.command.OrderItemCommand;
import com.yuncang.web.notification.NotificationOutboxPublisher;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Opt-in: real MySQL 8 and a dedicated RabbitMQ exchange/queue created for this run. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"mysql-acceptance", "rabbit-acceptance"})
@EnabledIfEnvironmentVariable(named = "MYSQL_TEST_URL", matches = "jdbc:mysql:.*")
@EnabledIfEnvironmentVariable(named = "RABBIT_TEST_HOST", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RabbitMqAcceptanceTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitAdmin admin;
    @Autowired RabbitListenerEndpointRegistry listeners;
    @Autowired NotificationOutboxPublisher publisher;
    @Autowired WarehouseService warehouses;
    @Autowired CategoryService categories;
    @Autowired SkuService skus;
    @Autowired InboundService inbounds;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired UserService users;
    @Value("${app.notifications.exchange}") String exchange;
    @Value("${app.notifications.queue}") String queue;

    @BeforeAll
    void requireIsolatedResources() {
        assertTrue(jdbc.queryForObject("SELECT DATABASE()", String.class)
                .startsWith("yuncang_acceptance_"));
        assertTrue(queue.startsWith("yuncang.acceptance.yuncang_acceptance_"));
        assertNotNull(admin.getQueueProperties(queue));
    }

    @AfterAll
    void removeOnlyOwnedBrokerResources() {
        listeners.stop();
        admin.deleteQueue(queue);
        admin.deleteQueue(queue + ".dlq");
        admin.deleteExchange(exchange);
        admin.deleteExchange(exchange + ".dlx");
    }

    private static void until(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(100);
        }
        fail("Timed out waiting for RabbitMQ consumer result");
    }

    private record Pair(long warehouse, long sku) {}

    private Pair pair() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        long warehouse = warehouses.create("MW-" + tag, "消息仓", null).getId();
        long category = categories.create("MC-" + tag, "消息分类").getId();
        long sku = skus.create("MS-" + tag, "消息SKU", category, "件", 10L).getId();
        return new Pair(warehouse, sku);
    }

    @Test
    void alertOutboxConfirmConsumeDuplicateAndRecovery() throws Exception {
        Pair p = pair();
        var item = List.of(new OrderItemCommand(p.sku(), 2L));
        inbounds.confirm(inbounds.create(p.warehouse(), item, null, 1L).getId(), 1L);
        String eventId = jdbc.queryForObject("SELECT event_id FROM stock_alert_outbox "
                + "ORDER BY id DESC LIMIT 1", String.class);
        String payload = jdbc.queryForObject("SELECT payload FROM stock_alert_outbox WHERE event_id=?",
                String.class, eventId);
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM stock_alert_outbox WHERE event_id=?",
                String.class, eventId));

        listeners.stop();
        publisher.publishPending();
        assertEquals("SENT", jdbc.queryForObject("SELECT status FROM stock_alert_outbox WHERE event_id=?",
                String.class, eventId));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE event_id=?",
                Long.class, eventId));
        listeners.start();
        until(() -> jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE event_id=?",
                Long.class, eventId) == 1);
        assertEquals("LOW_STOCK", jdbc.queryForObject("SELECT type FROM notification WHERE event_id=?",
                String.class, eventId));
        String visible = mvc.perform(get("/api/v1.1/notifications")
                        .header("Authorization", "Bearer " + jwt.issue(users.activeById(1L)).token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(visible.contains(eventId), "通知中心 API 应展示 MQ 消费结果");

        rabbit.convertAndSend(exchange, "notification", payload);
        until(() -> {
            var properties = admin.getQueueProperties(queue);
            return properties != null && ((Number) properties.get("QUEUE_MESSAGE_COUNT")).intValue() == 0;
        });
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE event_id=?",
                Long.class, eventId));

        inbounds.confirm(inbounds.create(p.warehouse(), List.of(new OrderItemCommand(p.sku(), 10L)),
                null, 1L).getId(), 1L);
        assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM stock_alert_outbox "
                        + "WHERE JSON_EXTRACT(payload,'$.skuId')=?",
                Long.class, p.sku()));
        publisher.publishPending();
        until(() -> jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE sku_id=?",
                Long.class, p.sku()) == 2);
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM notification "
                + "WHERE sku_id=? AND type='STOCK_RECOVERED'", Long.class, p.sku()));
    }

    @Test
    void confirmReturnPoisonMessageAndOutboxRetry() throws Exception {
        CorrelationData unroutable = new CorrelationData(UUID.randomUUID().toString());
        rabbit.convertAndSend(exchange, "no-such-routing-key", "unroutable", unroutable);
        assertTrue(unroutable.getFuture().get(5, TimeUnit.SECONDS).isAck());
        assertNotNull(unroutable.getReturned(), "mandatory return must detect unroutable messages");

        rabbit.convertAndSend(exchange, "notification", "not-json");
        until(() -> admin.getQueueProperties(queue + ".dlq") != null
                && ((Number) admin.getQueueProperties(queue + ".dlq").get("QUEUE_MESSAGE_COUNT"))
                        .intValue() > 0);
        assertNotNull(rabbit.receive(queue + ".dlq"));

        Pair p = pair();
        inbounds.confirm(inbounds.create(p.warehouse(), List.of(new OrderItemCommand(p.sku(), 1L)),
                null, 1L).getId(), 1L);
        String eventId = jdbc.queryForObject("SELECT event_id FROM stock_alert_outbox ORDER BY id DESC LIMIT 1",
                String.class);
        CachingConnectionFactory refused = new CachingConnectionFactory("127.0.0.1", 15673);
        try {
            RabbitTemplate broken = new RabbitTemplate(refused);
            new NotificationOutboxPublisher(jdbc, broken, exchange).publishPending();
        } finally {
            refused.destroy();
        }
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM stock_alert_outbox WHERE event_id=?",
                String.class, eventId));
        assertEquals(1, jdbc.queryForObject("SELECT retry_count FROM stock_alert_outbox WHERE event_id=?",
                Integer.class, eventId));
        jdbc.update("UPDATE stock_alert_outbox SET next_retry_at=NOW(6) WHERE event_id=?", eventId);
        publisher.publishPending();
        until(() -> jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE event_id=?",
                Long.class, eventId) == 1);
        assertEquals("SENT", jdbc.queryForObject("SELECT status FROM stock_alert_outbox WHERE event_id=?",
                String.class, eventId));
    }
}
