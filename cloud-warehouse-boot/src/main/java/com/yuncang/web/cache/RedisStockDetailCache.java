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

/**
 * 缓存库存详情，并在事务提交后切换缓存版本。
 * 并发回源即使写回旧数据，也只会写入旧版本的键；Redis 异常时回退数据库。
 */
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
        // 无库存行的零库存视图不缓存：修改 SKU 安全库存时，无法枚举这些虚拟仓库组合来失效缓存。
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
            // 数据库已经提交，缓存失效失败不应将业务响应改为失败；残留缓存由 TTL 到期清理。
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
