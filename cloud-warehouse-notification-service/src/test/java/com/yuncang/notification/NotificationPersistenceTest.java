package com.yuncang.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuncang.notification.messaging.NotificationConsumer;
import com.yuncang.notification.messaging.StockAlertChangedEvent;
import com.yuncang.notification.service.NotificationQueryService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:notification-only;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:notification-test-schema.sql",
    "spring.cloud.nacos.discovery.enabled=false", "spring.cloud.discovery.enabled=false",
    "spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.dynamic=false",
    "app.notifications.consumer-enabled=true", "app.internal.token=test-internal-token"
})
@ActiveProfiles("notification-mysql")
@AutoConfigureMockMvc
class NotificationPersistenceTest {
    @Autowired NotificationConsumer consumer;
    @Autowired NotificationQueryService query;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transaction;
    @Autowired MockMvc mvc;

    private String payload(String id, String status) throws Exception {
        return json.writeValueAsString(new StockAlertChangedEvent(id, 3, 1, 2,
                "RESOLVED", status, 4, 10, Instant.parse("2026-01-01T00:00:00Z")));
    }

    @Test
    void duplicateDeliveryIsIdempotentAndRollbackCanBeRetried() throws Exception {
        String body = payload("00000000-0000-0000-0000-000000000001", "ACTIVE");
        consumer.receive(body);
        consumer.receive(body);
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE event_id=?",
                Long.class, "00000000-0000-0000-0000-000000000001"));
        String retry = payload("00000000-0000-0000-0000-000000000002", "RESOLVED");
        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
            try { consumer.receive(retry); } catch (Exception e) { throw new RuntimeException(e); }
            throw new IllegalStateException("force rollback");
        }));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE event_id=?",
                Long.class, "00000000-0000-0000-0000-000000000002"));
        consumer.receive(retry);
        assertEquals("STOCK_RECOVERED", jdbc.queryForObject(
                "SELECT type FROM notification WHERE event_id=?", String.class,
                "00000000-0000-0000-0000-000000000002"));
        assertThrows(Exception.class, () -> consumer.receive("{bad-json"));
        assertThrows(IllegalArgumentException.class, () -> consumer.receive(payload("", "ACTIVE")));
    }

    @Test
    void pagingHasStableOrderAndChecksInternalCredential() throws Exception {
        consumer.receive(payload("00000000-0000-0000-0000-000000000003", "ACTIVE"));
        consumer.receive(payload("00000000-0000-0000-0000-000000000004", "ACTIVE"));
        jdbc.update("UPDATE notification SET created_at='2026-01-01 00:00:00'");
        var first = query.list(1, 1);
        var second = query.list(2, 1);
        assertTrue(first.items().get(0).id() > second.items().get(0).id());
        assertEquals(first.total(), second.total());
        assertTrue(query.list(Integer.MAX_VALUE, 100).items().isEmpty());
        mvc.perform(get("/internal/v1/notifications/page")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/v1/notifications/page").header("X-Internal-Token", "wrong"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/v1/notifications/page").header("X-Internal-Token", "test-internal-token")
                .param("page", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/internal/v1/notifications/page").header("X-Internal-Token", "test-internal-token")
                .param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/internal/v1/notifications/page").header("X-Internal-Token", "test-internal-token")
                .param("size", "1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].eventId").exists())
                .andExpect(jsonPath("$.items[0].status").value("UNREAD"))
                .andExpect(jsonPath("$.page").value(1));
    }
}
