package com.yuncang;

import com.yuncang.biz.mapper.StockFlowMapper;
import com.yuncang.biz.mapper.StockMapper;
import com.yuncang.biz.service.CategoryService;
import com.yuncang.biz.service.InboundService;
import com.yuncang.biz.service.OutboundService;
import com.yuncang.biz.service.SkuService;
import com.yuncang.biz.service.StockQueryService;
import com.yuncang.biz.service.WarehouseService;
import com.yuncang.biz.service.command.OrderItemCommand;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A refused Redis endpoint represents an outage; MySQL operations must still commit. */
@SpringBootTest(properties = {
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=16380",
        "spring.data.redis.timeout=200ms"
})
@ActiveProfiles({"mysql-acceptance", "redis-acceptance"})
@EnabledIfEnvironmentVariable(named = "MYSQL_TEST_URL", matches = "jdbc:mysql:.*")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisUnavailableAcceptanceTest {
    private static final long ACTOR = 1L;
    @Autowired JdbcTemplate jdbc;
    @Autowired WarehouseService warehouses;
    @Autowired CategoryService categories;
    @Autowired SkuService skus;
    @Autowired InboundService inbounds;
    @Autowired OutboundService outbounds;
    @Autowired StockQueryService queries;
    @Autowired StockMapper stocks;
    @Autowired StockFlowMapper flows;

    @BeforeAll
    void requireIsolatedMySqlAndRefusedRedisEndpoint() {
        assertTrue(jdbc.queryForObject("SELECT DATABASE()", String.class)
                .startsWith("yuncang_acceptance_"));
        try (Socket socket = new Socket()) {
            assertThrows(Exception.class,
                    () -> socket.connect(new InetSocketAddress("127.0.0.1", 16380), 200));
        } catch (Exception closeFailure) {
            throw new AssertionError(closeFailure);
        }
    }

    @Test
    void readsFallBackAndInventoryWritesCommitDuringRedisOutage() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        long warehouse = warehouses.create("DW-" + tag, "断线仓", null).getId();
        long category = categories.create("DC-" + tag, "断线分类").getId();
        long sku = skus.create("DS-" + tag, "断线SKU", category, "件", 8L).getId();
        assertEquals(0L, queries.get(warehouse, sku).getPhysicalStock());
        long inbound = inbounds.create(warehouse,
                List.of(new OrderItemCommand(sku, 10L)), null, ACTOR).getId();
        inbounds.confirm(inbound, ACTOR);
        assertEquals(10L, queries.get(warehouse, sku).getPhysicalStock());
        long outbound = outbounds.create(warehouse,
                List.of(new OrderItemCommand(sku, 4L)), null, ACTOR).getId();
        outbounds.reserve(outbound, ACTOR);
        assertEquals("RESERVED", outbounds.get(outbound).getStatus());
        assertEquals(10L, stocks.findByWarehouseAndSku(warehouse, sku).getPhysicalStock());
        assertEquals(6L, queries.get(warehouse, sku).getAvailableStock());
        assertEquals(4L, stocks.findByWarehouseAndSku(warehouse, sku).getLockedStock());
        assertEquals(1, flows.list(null, null, "OUTBOUND", outbound, null).size());
        assertEquals("ACTIVE", jdbc.queryForObject(
                "SELECT status FROM stock_alert WHERE warehouse_id=? AND sku_id=?",
                String.class, warehouse, sku));
    }
}
