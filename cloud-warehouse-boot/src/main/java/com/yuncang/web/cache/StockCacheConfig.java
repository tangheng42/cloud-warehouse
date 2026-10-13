package com.yuncang.web.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuncang.biz.service.StockDetailCache;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
public class StockCacheConfig {
    @Bean
    @ConditionalOnProperty(prefix = "app.stock-cache", name = "enabled", havingValue = "true")
    StockDetailCache redisStockDetailCache(StringRedisTemplate redis, ObjectMapper json,
            @Value("${app.stock-cache.ttl:PT5M}") Duration ttl,
            @Value("${app.stock-cache.jitter:PT30S}") Duration jitter) {
        return new RedisStockDetailCache(redis, json, ttl, jitter);
    }

    @Bean
    @ConditionalOnMissingBean(StockDetailCache.class)
    StockDetailCache databaseOnlyStockDetailCache() {
        return new StockDetailCache() {
            @Override public com.yuncang.biz.entity.WarehouseStock getOrLoad(Long warehouseId,
                    Long skuId, java.util.function.Supplier<com.yuncang.biz.entity.WarehouseStock> loader) {
                return loader.get();
            }
            @Override public void invalidateAfterCommit(Long warehouseId, Long skuId) {}
        };
    }
}
