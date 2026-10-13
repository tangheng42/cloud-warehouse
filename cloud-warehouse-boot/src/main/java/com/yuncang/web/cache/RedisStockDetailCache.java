package com.yuncang.web.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuncang.biz.entity.WarehouseStock;
import com.yuncang.biz.service.StockDetailCache;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Versioned cache keys prevent a pre-commit read from repopulating the current key after invalidation. */
public class RedisStockDetailCache implements StockDetailCache {
    private static final Logger log = LoggerFactory.getLogger(RedisStockDetailCache.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Duration ttl;
    private final Duration jitter;

    public RedisStockDetailCache(StringRedisTemplate redis, ObjectMapper json,
            Duration ttl, Duration jitter) {
        if (ttl.isZero() || ttl.isNegative() || jitter.isNegative()) {
            throw new IllegalArgumentException("库存缓存 TTL 必须为正，抖动时间不能为负");
        }
        this.redis = redis;
        this.json = json;
        this.ttl = ttl;
        this.jitter = jitter;
    }

    @Override
    public WarehouseStock getOrLoad(Long warehouseId, Long skuId,
            Supplier<WarehouseStock> databaseLoader) {
        String generation;
        String key;
        try {
            generation = generation(warehouseId, skuId);
            key = detailKey(warehouseId, skuId, generation);
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                try {
                    return json.readValue(cached, WarehouseStock.class);
                } catch (Exception badCacheValue) {
                    log.warn("库存缓存内容损坏，回退到 MySQL: {}", key, badCacheValue);
                    redis.delete(key);
                }
            }
        } catch (RuntimeException redisUnavailable) {
            log.warn("Redis 不可用，库存详情回退到 MySQL", redisUnavailable);
            return databaseLoader.get();
        }
        WarehouseStock stock = databaseLoader.get();
        // Zero stock has no row. Avoid caching it because a SKU safety-stock update
        // cannot enumerate warehouse IDs for nonexistent rows.
        if (stock.getId() != null) {
            try {
                redis.opsForValue().set(key, json.writeValueAsString(stock), expiry());
            } catch (Exception cacheWriteFailure) {
                log.warn("库存缓存写入失败，MySQL 查询结果仍可正常返回", cacheWriteFailure);
            }
        }
        return stock;
    }

    @Override
    public void invalidateAfterCommit(Long warehouseId, Long skuId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { invalidate(warehouseId, skuId); }
            });
        } else {
            invalidate(warehouseId, skuId);
        }
    }

    private void invalidate(Long warehouseId, Long skuId) {
        try {
            String oldGeneration = generation(warehouseId, skuId);
            redis.opsForValue().increment(generationKey(warehouseId, skuId));
            redis.delete(detailKey(warehouseId, skuId, oldGeneration));
        } catch (RuntimeException redisUnavailable) {
            // The committed MySQL transaction must not become a failed API response.
            // Cached values are bounded by TTL if Redis recovers after this failure.
            log.warn("库存已提交，但 Redis 失效操作失败；缓存将由 TTL 清理", redisUnavailable);
        }
    }

    private Duration expiry() {
        long jitterMillis = jitter.toMillis();
        return ttl.plusMillis(jitterMillis == 0 ? 0
                : ThreadLocalRandom.current().nextLong(jitterMillis + 1));
    }

    private String generation(Long warehouseId, Long skuId) {
        String value = redis.opsForValue().get(generationKey(warehouseId, skuId));
        return value == null ? "0" : value;
    }

    private String generationKey(Long warehouseId, Long skuId) {
        return "stock:detail:version:" + warehouseId + ":" + skuId;
    }

    private String detailKey(Long warehouseId, Long skuId, String generation) {
        return "stock:detail:" + warehouseId + ":" + skuId + ":v" + generation;
    }
}
