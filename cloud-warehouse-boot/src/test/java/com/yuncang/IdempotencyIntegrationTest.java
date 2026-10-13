package com.yuncang;

import com.yuncang.biz.service.CategoryService;
import com.yuncang.biz.service.InboundService;
import com.yuncang.biz.service.OutboundService;
import com.yuncang.biz.service.SkuService;
import com.yuncang.biz.service.StocktakeService;
import com.yuncang.biz.service.TransferService;
import com.yuncang.biz.service.WarehouseService;
import com.yuncang.biz.service.command.OrderItemCommand;
import com.yuncang.biz.service.command.StocktakeItemCommand;
import com.yuncang.common.BusinessException;
import com.yuncang.common.ErrorCode;
import com.yuncang.web.service.CreateRequestIdempotencyService;
import com.yuncang.web.security.JwtService;
import com.yuncang.biz.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mysql-acceptance")
@EnabledIfEnvironmentVariable(named = "MYSQL_TEST_URL", matches = "jdbc:mysql:.*")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IdempotencyIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired CreateRequestIdempotencyService idem;
    @Autowired WarehouseService warehouses;
    @Autowired CategoryService categories;
    @Autowired SkuService skus;
    @Autowired InboundService inbounds;
    @Autowired OutboundService outbounds;
    @Autowired TransferService transfers;
    @Autowired StocktakeService stocktakes;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    @Autowired UserService users;

    private record Pair(long warehouse, long sku) {}

    @BeforeAll
    void requireEphemeralMySqlSchema() {
        assertTrue(jdbc.queryForObject("SELECT DATABASE()", String.class)
                .startsWith("yuncang_acceptance_"));
        assertEquals("InnoDB", jdbc.queryForObject("SELECT ENGINE FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() AND table_name='create_request_idempotency'", String.class));
    }

    private Pair pair() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        long warehouse = warehouses.create("IW-" + tag, "幂等仓", null).getId();
        long category = categories.create("IC-" + tag, "幂等分类").getId();
        long sku = skus.create("IS-" + tag, "幂等SKU", category, "件", 10L).getId();
        return new Pair(warehouse, sku);
    }

    @Test
    void concurrentSameKeyCreatesOneOutboundOrder() throws Exception {
        Pair p = pair();
        String key = UUID.randomUUID().toString();
        var items = List.of(new OrderItemCommand(p.sku(), 7L));
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Long> attempt = () -> {
                ready.countDown();
                assertTrue(go.await(5, TimeUnit.SECONDS));
                return idem.create(key, 1L, "OUTBOUND_CREATE", List.of(p.warehouse(), items),
                        () -> outbounds.create(p.warehouse(), items, null, 1L),
                        order -> order.getId(), outbounds::get).getId();
            };
            Future<Long> a = pool.submit(attempt);
            Future<Long> b = pool.submit(attempt);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            go.countDown();
            assertEquals(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM outbound_order WHERE warehouse_id=?",
                Long.class, p.warehouse()));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM create_request_idempotency "
                + "WHERE idem_key=?", Long.class, key));
    }

    @Test
    void keyScopePayloadConflictAndFailedCreateRollback() {
        Pair p = pair();
        jdbc.update("INSERT INTO app_user (username, password_hash, role, enabled) VALUES (?, ?, 'ADMIN', 1)",
                "idem-" + UUID.randomUUID(), "unused");
        long anotherUser = jdbc.queryForObject("SELECT MAX(id) FROM app_user", Long.class);
        String key = UUID.randomUUID().toString();
        var one = List.of(new OrderItemCommand(p.sku(), 1L));
        var two = List.of(new OrderItemCommand(p.sku(), 2L));
        long first = idem.create(key, 1L, "INBOUND_CREATE", one,
                () -> inbounds.create(p.warehouse(), one, null, 1L),
                order -> order.getId(), inbounds::get).getId();
        long replay = idem.create(key, 1L, "INBOUND_CREATE", one,
                () -> inbounds.create(p.warehouse(), one, null, 1L),
                order -> order.getId(), inbounds::get).getId();
        assertEquals(first, replay);
        BusinessException conflict = assertThrows(BusinessException.class, () ->
                idem.create(key, 1L, "INBOUND_CREATE", two,
                        () -> inbounds.create(p.warehouse(), two, null, 1L),
                        order -> order.getId(), inbounds::get));
        assertEquals(ErrorCode.IDEMPOTENCY_KEY_CONFLICT, conflict.getErrorCode());
        long secondUser = idem.create(key, anotherUser, "INBOUND_CREATE", one,
                () -> inbounds.create(p.warehouse(), one, null, anotherUser),
                order -> order.getId(), inbounds::get).getId();
        assertNotEquals(first, secondUser);
        String failedKey = UUID.randomUUID().toString();
        assertThrows(BusinessException.class, () -> idem.create(failedKey, 1L,
                "STOCKTAKE_CREATE", List.of(new StocktakeItemCommand(p.sku(), 4L)),
                () -> stocktakes.create(p.warehouse(), List.of(new StocktakeItemCommand(p.sku(), 4L)), null, 1L),
                order -> order.getId(), stocktakes::get));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM create_request_idempotency WHERE idem_key=?",
                Long.class, failedKey));
    }

    @Test
    void transferAndStocktakeReplayUseSameBusinessId() {
        Pair p = pair();
        String tag = UUID.randomUUID().toString().substring(0, 8);
        long target = warehouses.create("IT-" + tag, "目标仓", null).getId();
        var items = List.of(new OrderItemCommand(p.sku(), 2L));
        String transferKey = UUID.randomUUID().toString();
        long transfer = idem.create(transferKey, 1L, "TRANSFER_CREATE", items,
                () -> transfers.create(p.warehouse(), target, items, null, 1L),
                order -> order.getId(), transfers::get).getId();
        assertEquals(transfer, idem.create(transferKey, 1L, "TRANSFER_CREATE", items,
                () -> transfers.create(p.warehouse(), target, items, null, 1L),
                order -> order.getId(), transfers::get).getId());

        inbounds.confirm(inbounds.create(p.warehouse(), items, null, 1L).getId(), 1L);
        var counted = List.of(new StocktakeItemCommand(p.sku(), 2L));
        String stocktakeKey = UUID.randomUUID().toString();
        long stocktake = idem.create(stocktakeKey, 1L, "STOCKTAKE_CREATE", counted,
                () -> stocktakes.create(p.warehouse(), counted, null, 1L),
                order -> order.getId(), stocktakes::get).getId();
        assertEquals(stocktake, idem.create(stocktakeKey, 1L, "STOCKTAKE_CREATE", counted,
                () -> stocktakes.create(p.warehouse(), counted, null, 1L),
                order -> order.getId(), stocktakes::get).getId());
    }

    @Test
    void optionalHttpHeaderReplaysCreateWithoutChangingV1Body() throws Exception {
        Pair p = pair();
        String key = UUID.randomUUID().toString();
        String bearer = "Bearer " + jwt.issue(users.activeById(1L)).token();
        String body = "{\"warehouseId\":" + p.warehouse() + ",\"items\":[{\"skuId\":"
                + p.sku() + ",\"quantity\":7}]}";
        String first = mvc.perform(post("/api/v1/outbounds").header("Authorization", bearer)
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = mvc.perform(post("/api/v1/outbounds").header("Authorization", bearer)
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertEquals(json.readTree(first).path("data").path("id").asLong(),
                json.readTree(second).path("data").path("id").asLong());
        String changed = body.replace("\"quantity\":7", "\"quantity\":8");
        String response = mvc.perform(post("/api/v1/outbounds").header("Authorization", bearer)
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(changed))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
        assertEquals("IDEMPOTENCY_KEY_CONFLICT", json.readTree(response).path("code").asText());
    }
}
