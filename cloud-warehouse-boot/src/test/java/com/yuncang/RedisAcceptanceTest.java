package com.yuncang;

import com.yuncang.biz.entity.WarehouseStock;
import com.yuncang.biz.mapper.StockFlowMapper;
import com.yuncang.biz.mapper.StockMapper;
import com.yuncang.biz.service.CategoryService;
import com.yuncang.biz.service.InboundService;
import com.yuncang.biz.service.OutboundService;
import com.yuncang.biz.service.SkuService;
import com.yuncang.biz.service.StockDetailCache;
import com.yuncang.biz.service.StockQueryService;
import com.yuncang.biz.service.WarehouseService;
import com.yuncang.biz.service.command.OrderItemCommand;
import com.yuncang.common.BusinessException;
import com.yuncang.common.ErrorCode;
import com.yuncang.web.cache.RedisStockDetailCache;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in integration tests against real MySQL 8 and a dedicated Redis logical database. */
@SpringBootTest
@ActiveProfiles({"mysql-acceptance", "redis-acceptance"})
@EnabledIfEnvironmentVariable(named = "MYSQL_TEST_URL", matches = "jdbc:mysql:.*")
@EnabledIfEnvironmentVariable(named = "REDIS_TEST_HOST", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisAcceptanceTest {
    private static final long ACTOR = 1L;
    @Autowired StringRedisTemplate redis;
    @Autowired StockDetailCache cache;
    @Autowired StockQueryService queries;
    @Autowired StockMapper stocks;
    @Autowired StockFlowMapper flows;
    @Autowired WarehouseService warehouses;
    @Autowired CategoryService categories;
    @Autowired SkuService skus;
    @Autowired InboundService inbounds;
    @Autowired OutboundService outbounds;
    private final List<String> ownedPrefixes = new ArrayList<>();

    private record Pair(long warehouse, long sku) {}

    @BeforeAll
    void requireDedicatedRealRedisDatabase() {
        assertInstanceOf(RedisStockDetailCache.class, cache);
        LettuceConnectionFactory factory =
                (LettuceConnectionFactory) redis.getConnectionFactory();
        assertEquals(15, factory.getDatabase(), "Tests must use isolated Redis logical DB 15");
        assertEquals("PONG", redis.getConnectionFactory().getConnection().ping());
        assertEquals(Set.of(), redis.keys("stock:detail:*"),
                "Redis DB 15 already contains stock cache keys; refusing to touch them");
    }

    @AfterAll
    void deleteOnlyKeysCreatedByThisTestClass() {
        for (String prefix : ownedPrefixes) {
            Set<String> details = redis.keys(prefix + ":v*");
            if (details != null && !details.isEmpty()) redis.delete(details);
            redis.delete(prefix.replace("stock:detail:", "stock:detail:version:"));
        }
    }

    private Pair pair(long safety) {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        long warehouse = warehouses.create("RW-" + tag, "Redis验收仓", null).getId();
        long category = categories.create("RC-" + tag, "Redis验收分类").getId();
        long sku = skus.create("RS-" + tag, "Redis验收SKU", category, "件", safety).getId();
        ownedPrefixes.add("stock:detail:" + warehouse + ":" + sku);
        return new Pair(warehouse, sku);
    }

    private long inbound(Pair p, long quantity) {
        long id = inbounds.create(p.warehouse(), List.of(new OrderItemCommand(p.sku(), quantity)),
                null, ACTOR).getId();
        inbounds.confirm(id, ACTOR);
        return id;
    }

    private String version(Pair p) {
        String value = redis.opsForValue().get(
                "stock:detail:version:" + p.warehouse() + ":" + p.sku());
        return value == null ? "0" : value;
    }

    private String detailKey(Pair p, String version) {
        return "stock:detail:" + p.warehouse() + ":" + p.sku() + ":v" + version;
    }

    @Test
    void missHitTtlJitterAndExpiryReallyUseRedis() throws Exception {
        Pair p = pair(0);
        inbound(p, 10);
        String key = detailKey(p, version(p));
        assertNull(redis.opsForValue().get(key));
        assertEquals(10L, queries.get(p.warehouse(), p.sku()).getPhysicalStock());
        assertTrue(redis.opsForValue().get(key).contains("\"physicalStock\":10"));
        Long ttl = redis.getExpire(key, TimeUnit.MILLISECONDS);
        assertTrue(ttl > 0 && ttl <= 3000, "TTL must be 2-3 seconds");
        // A supplier that must never run proves a real cache hit.
        assertEquals(10L, cache.getOrLoad(p.warehouse(), p.sku(),
                () -> { throw new AssertionError("cache hit unexpectedly reached MySQL"); })
                .getPhysicalStock());

        List<Long> ttls = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            long fakeSku = 9_000_000L + i;
            ownedPrefixes.add("stock:detail:" + p.warehouse() + ":" + fakeSku);
            WarehouseStock synthetic = new WarehouseStock();
            synthetic.setId(fakeSku);
            synthetic.setWarehouseId(p.warehouse());
            synthetic.setSkuId(fakeSku);
            synthetic.setPhysicalStock(1L);
            synthetic.setAvailableStock(1L);
            synthetic.setLockedStock(0L);
            synthetic.setSafetyStock(0L);
            cache.getOrLoad(p.warehouse(), fakeSku, () -> synthetic);
            ttls.add(redis.getExpire("stock:detail:" + p.warehouse() + ":" + fakeSku + ":v0",
                    TimeUnit.MILLISECONDS));
        }
        assertTrue(ttls.stream().allMatch(value -> value > 0 && value <= 3000));
        assertTrue(ttls.stream().distinct().count() > 1, "TTL jitter should vary expiry");
        Thread.sleep(3200);
        assertNull(redis.opsForValue().get(key));
        AtomicInteger reloads = new AtomicInteger();
        cache.getOrLoad(p.warehouse(), p.sku(), () -> {
            reloads.incrementAndGet();
            return stocks.findByWarehouseAndSku(p.warehouse(), p.sku());
        });
        assertEquals(1, reloads.get());
        assertTrue(redis.hasKey(key));
    }

    @Test
    void successfulCommitInvalidatesButUnconfirmedOrderDoesNot() {
        Pair p = pair(0);
        inbound(p, 10);
        queries.get(p.warehouse(), p.sku());
        String oldVersion = version(p);
        String oldKey = detailKey(p, oldVersion);
        long id = inbounds.create(p.warehouse(), List.of(new OrderItemCommand(p.sku(), 5L)),
                null, ACTOR).getId();
        assertEquals(oldVersion, version(p));
        assertTrue(redis.hasKey(oldKey));
        inbounds.confirm(id, ACTOR);
        assertEquals(Long.parseLong(oldVersion) + 1, Long.parseLong(version(p)));
        assertFalse(redis.hasKey(oldKey));
        assertEquals(15L, queries.get(p.warehouse(), p.sku()).getPhysicalStock());
        assertTrue(redis.hasKey(detailKey(p, version(p))));
    }

    @Test
    void rollbackLeavesCommittedStockAndCacheUnchanged() {
        Pair first = pair(20);
        inbound(first, 10);
        // Both SKU IDs exist globally and share the same warehouse for this outbound.
        long extraSku = skus.create("RR-" + UUID.randomUUID().toString().substring(0, 8),
                "另一个SKU", categories.create("RX-" + UUID.randomUUID().toString().substring(0, 8),
                        "补充分组").getId(), "件", 20L).getId();
        Pair sharedWarehouseSecond = new Pair(first.warehouse(), extraSku);
        ownedPrefixes.add("stock:detail:" + first.warehouse() + ":" + extraSku);
        inbound(sharedWarehouseSecond, 5);
        queries.get(first.warehouse(), first.sku());
        String oldVersion = version(first);
        String key = detailKey(first, oldVersion);
        long id = outbounds.create(first.warehouse(), List.of(
                new OrderItemCommand(first.sku(), 2L),
                new OrderItemCommand(extraSku, 11L)), null, ACTOR).getId();
        assertEquals(ErrorCode.INSUFFICIENT_AVAILABLE_STOCK,
                assertThrows(BusinessException.class, () -> outbounds.reserve(id, ACTOR))
                        .getErrorCode());
        assertEquals("CREATED", outbounds.get(id).getStatus());
        assertEquals(oldVersion, version(first));
        assertTrue(redis.hasKey(key));
        assertEquals(10L, queries.get(first.warehouse(), first.sku()).getPhysicalStock());
        assertEquals(10L, stocks.findByWarehouseAndSku(first.warehouse(), first.sku())
                .getAvailableStock());
        assertEquals(0, flows.list(null, null, "OUTBOUND", id, null).size());
    }

    @Test
    void oldReadCanOnlyPopulateOldGenerationAfterCommit() throws Exception {
        Pair p = pair(0);
        inbound(p, 10);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch loaderEntered = new CountDownLatch(1);
        CountDownLatch allowOldRead = new CountDownLatch(1);
        try {
            var oldRead = executor.submit(() -> cache.getOrLoad(p.warehouse(), p.sku(), () -> {
                WarehouseStock before = stocks.findByWarehouseAndSku(p.warehouse(), p.sku());
                loaderEntered.countDown();
                try {
                    if (!allowOldRead.await(5, TimeUnit.SECONDS)) throw new AssertionError("timeout");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
                return before;
            }));
            assertTrue(loaderEntered.await(5, TimeUnit.SECONDS));
            String oldVersion = version(p);
            inbound(p, 5);
            assertEquals(Long.parseLong(oldVersion) + 1, Long.parseLong(version(p)));
            allowOldRead.countDown();
            assertEquals(10L, oldRead.get(5, TimeUnit.SECONDS).getPhysicalStock());
            assertEquals(15L, queries.get(p.warehouse(), p.sku()).getPhysicalStock());
            assertTrue(redis.hasKey(detailKey(p, version(p))));
        } finally {
            allowOldRead.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void nonexistentStockIsZeroAndNotCachedThenFirstInboundBecomesVisible() {
        Pair p = pair(4);
        WarehouseStock empty = queries.get(p.warehouse(), p.sku());
        assertNull(empty.getId());
        assertEquals(0L, empty.getPhysicalStock());
        assertEquals(0L, empty.getAvailableStock());
        assertEquals(0L, empty.getLockedStock());
        assertEquals(Set.of(), redis.keys("stock:detail:" + p.warehouse() + ":" + p.sku() + ":v*"));
        inbound(p, 7);
        assertEquals(7L, queries.get(p.warehouse(), p.sku()).getPhysicalStock());
        assertTrue(redis.hasKey(detailKey(p, version(p))));
    }
}
