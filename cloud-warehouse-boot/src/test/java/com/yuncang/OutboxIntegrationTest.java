package com.yuncang;

import com.yuncang.biz.service.CategoryService;
import com.yuncang.biz.service.InboundService;
import com.yuncang.biz.service.SkuService;
import com.yuncang.biz.service.StockAlertService;
import com.yuncang.biz.service.WarehouseService;
import com.yuncang.biz.service.command.OrderItemCommand;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.dynamic=false"})
@ActiveProfiles({"mysql-acceptance", "rabbit-acceptance"})
@EnabledIfEnvironmentVariable(named = "MYSQL_TEST_URL", matches = "jdbc:mysql:.*")
@EnabledIfEnvironmentVariable(named = "RABBIT_TEST_HOST", matches = ".+")
class OutboxIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transaction;
    @Autowired WarehouseService warehouses;
    @Autowired CategoryService categories;
    @Autowired SkuService skus;
    @Autowired InboundService inbounds;
    @Autowired StockAlertService alerts;

    @Test
    void alertAndOutboxRollbackWithInventory() {
        assertTrue(jdbc.queryForObject("SELECT DATABASE()", String.class)
                .startsWith("yuncang_acceptance_"));
        String tag = UUID.randomUUID().toString().substring(0, 8);
        long warehouse = warehouses.create("OW-" + tag, "回滚仓", null).getId();
        long category = categories.create("OC-" + tag, "回滚分类").getId();
        long sku = skus.create("OS-" + tag, "回滚SKU", category, "件", 10L).getId();
        inbounds.confirm(inbounds.create(warehouse, List.of(new OrderItemCommand(sku, 10L)),
                null, 1L).getId(), 1L);
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM stock_alert_outbox", Long.class);
        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
            jdbc.update("UPDATE warehouse_stock SET physical_stock=physical_stock-1, "
                    + "available_stock=available_stock-1 WHERE warehouse_id=? AND sku_id=?", warehouse, sku);
            alerts.refresh(warehouse, sku);
            throw new IllegalStateException("Force whole transaction rollback");
        }));
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM stock_alert_outbox", Long.class));
        assertEquals(10L, jdbc.queryForObject("SELECT available_stock FROM warehouse_stock "
                + "WHERE warehouse_id=? AND sku_id=?", Long.class, warehouse, sku));
        assertEquals("RESOLVED", jdbc.queryForObject("SELECT status FROM stock_alert "
                + "WHERE warehouse_id=? AND sku_id=?", String.class, warehouse, sku));
    }
}
