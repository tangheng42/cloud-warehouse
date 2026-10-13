package com.yuncang.web.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuncang.biz.entity.WarehouseStock;
import com.yuncang.biz.service.StockDetailCache;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisStockDetailCacheTest {
    @Test
    void cacheFeatureFlagSelectsExactlyOneImplementation() {
        var context = new ApplicationContextRunner()
                .withInitializer(application -> application.getBeanFactory()
                        .setConversionService(ApplicationConversionService.getSharedInstance()))
                .withUserConfiguration(StockCacheConfig.class)
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(ObjectMapper.class, ObjectMapper::new);
        context.run(application -> {
            assertEquals(1, application.getBeansOfType(StockDetailCache.class).size());
            assertTrue(!(application.getBean(StockDetailCache.class) instanceof RedisStockDetailCache));
        });
        context.withPropertyValues("app.stock-cache.enabled=true").run(application -> {
            assertEquals(1, application.getBeansOfType(StockDetailCache.class).size());
            assertTrue(application.getBean(StockDetailCache.class) instanceof RedisStockDetailCache);
        });
    }

    private final Map<String, String> store = new ConcurrentHashMap<>();
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private RedisStockDetailCache cache;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(call -> store.get(call.getArgument(0)));
        doAnswer(call -> {
            store.put(call.getArgument(0), call.getArgument(1));
            Duration expiry = call.getArgument(2);
            assertTrue(expiry.compareTo(Duration.ofMinutes(5)) >= 0);
            assertTrue(expiry.compareTo(Duration.ofMinutes(5).plusSeconds(30)) <= 0);
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.increment(anyString())).thenAnswer(call -> {
            String key = call.getArgument(0);
            long next = Long.parseLong(store.getOrDefault(key, "0")) + 1;
            store.put(key, Long.toString(next));
            return next;
        });
        when(redis.delete(anyString())).thenAnswer(call -> store.remove(call.getArgument(0)) != null);
        cache = new RedisStockDetailCache(redis, new ObjectMapper(),
                Duration.ofMinutes(5), Duration.ofSeconds(30));
    }

    private WarehouseStock stock(long quantity) {
        WarehouseStock result = new WarehouseStock();
        result.setId(1L);
        result.setWarehouseId(2L);
        result.setSkuId(3L);
        result.setPhysicalStock(quantity);
        result.setAvailableStock(quantity);
        result.setLockedStock(0L);
        result.setSafetyStock(5L);
        return result;
    }

    @Test
    void cacheHitAndAfterCommitInvalidation() {
        AtomicInteger databaseReads = new AtomicInteger();
        assertEquals(10, cache.getOrLoad(2L, 3L, () -> {
            databaseReads.incrementAndGet();
            return stock(10);
        }).getPhysicalStock());
        assertEquals(10, cache.getOrLoad(2L, 3L, () -> {
            databaseReads.incrementAndGet();
            return stock(20);
        }).getPhysicalStock());
        assertEquals(1, databaseReads.get());

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            cache.invalidateAfterCommit(2L, 3L);
            assertEquals(10, cache.getOrLoad(2L, 3L, () -> stock(20)).getPhysicalStock());
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clear();
        }
        assertEquals(20, cache.getOrLoad(2L, 3L, () -> {
            databaseReads.incrementAndGet();
            return stock(20);
        }).getPhysicalStock());
        assertEquals(2, databaseReads.get());
    }

    @Test
    void oldReadCannotRepopulateCurrentGeneration() throws Exception {
        CountDownLatch loaderEntered = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var oldRead = executor.submit(() -> cache.getOrLoad(2L, 3L, () -> {
                loaderEntered.countDown();
                try { releaseLoader.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return stock(10);
            }));
            assertTrue(loaderEntered.await(5, TimeUnit.SECONDS));
            cache.invalidateAfterCommit(2L, 3L);
            releaseLoader.countDown();
            assertEquals(10, oldRead.get(5, TimeUnit.SECONDS).getPhysicalStock());
            assertEquals(20, cache.getOrLoad(2L, 3L, () -> stock(20)).getPhysicalStock());
        } finally {
            releaseLoader.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void nonexistentStockIsNotCached() {
        AtomicInteger databaseReads = new AtomicInteger();
        cache.getOrLoad(2L, 3L, () -> { databaseReads.incrementAndGet(); return new WarehouseStock(); });
        cache.getOrLoad(2L, 3L, () -> { databaseReads.incrementAndGet(); return new WarehouseStock(); });
        assertEquals(2, databaseReads.get());
    }
}
