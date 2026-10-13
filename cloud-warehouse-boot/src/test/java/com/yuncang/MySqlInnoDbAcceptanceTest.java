package com.yuncang;

import com.yuncang.biz.entity.StockFlow;
import com.yuncang.biz.entity.WarehouseStock;
import com.yuncang.biz.mapper.StockFlowMapper;
import com.yuncang.biz.mapper.StockMapper;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in acceptance tests. Run only against a newly created yuncang_acceptance_* MySQL 8
 * database initialized from docs/sql/01-schema.sql and 03-v1-business-schema.sql.
 * No test method has @Transactional: every Service call commits on a separate transaction.
 */
@SpringBootTest
@ActiveProfiles("mysql-acceptance")
@EnabledIfEnvironmentVariable(named = "MYSQL_TEST_URL", matches = "jdbc:mysql:.*")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MySqlInnoDbAcceptanceTest {
    private static final long ACTOR = 1L;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    @Autowired WarehouseService warehouses;
    @Autowired CategoryService categories;
    @Autowired SkuService skus;
    @Autowired InboundService inbounds;
    @Autowired OutboundService outbounds;
    @Autowired TransferService transfers;
    @Autowired StocktakeService stocktakes;
    @Autowired StockMapper stocks;
    @Autowired StockFlowMapper flows;

    private record Fixture(long warehouse, List<Long> skus) {}

    @BeforeAll
    void requireIsolatedMySql8AndRealSchema() {
        String database = jdbc.queryForObject("SELECT DATABASE()", String.class);
        String version = jdbc.queryForObject("SELECT VERSION()", String.class);
        assertNotNull(database);
        assertTrue(database.matches("yuncang_acceptance_[a-zA-Z0-9_]+"),
                "Refusing to run acceptance tests outside an isolated database: " + database);
        assertTrue(version.startsWith("8."), "MySQL 8.x required: " + version);
        assertEquals(15L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()", Long.class));
        assertEquals(15L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND engine = 'InnoDB'",
                Long.class));
        assertEquals("REPEATABLE-READ", jdbc.queryForObject("SELECT @@transaction_isolation", String.class));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE id = 1", Long.class));
    }

    @AfterEach
    void allCommittedRowsKeepInventoryFlowAndAlertInvariants() {
        assertEquals(0L, jdbc.queryForObject("""
                SELECT COUNT(*) FROM warehouse_stock
                WHERE physical_stock < 0 OR available_stock < 0 OR locked_stock < 0
                   OR physical_stock <> available_stock + locked_stock
                """, Long.class));
        assertEquals(0L, jdbc.queryForObject("""
                SELECT COUNT(*) FROM stock_flow
                WHERE after_physical <> before_physical + delta_physical
                   OR after_available <> before_available + delta_available
                   OR after_locked <> before_locked + delta_locked
                   OR before_physical <> before_available + before_locked
                   OR after_physical <> after_available + after_locked
                """, Long.class));
        assertEquals(0L, jdbc.queryForObject("""
                SELECT COUNT(*) FROM stock_alert a
                JOIN warehouse_stock ws ON ws.warehouse_id = a.warehouse_id AND ws.sku_id = a.sku_id
                JOIN sku s ON s.id = a.sku_id
                WHERE a.available_stock <> ws.available_stock OR a.safety_stock <> s.safety_stock
                   OR a.status <> IF(s.safety_stock > 0 AND ws.available_stock < s.safety_stock,
                                      'ACTIVE', 'RESOLVED')
                """, Long.class));
    }

    private Fixture fixture(int count, long safety) {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        long warehouse = warehouses.create("MW-" + tag, "验收仓", null).getId();
        long category = categories.create("MC-" + tag, "验收分类").getId();
        List<Long> skuIds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            skuIds.add(skus.create("MS-" + tag + "-" + i, "验收商品", category, "件", safety).getId());
        }
        return new Fixture(warehouse, skuIds);
    }

    private long anotherWarehouse() {
        return warehouses.create("MT-" + UUID.randomUUID().toString().substring(0, 8),
                "目标仓", null).getId();
    }

    private long seed(long warehouse, long sku, long quantity) {
        long id = inbounds.create(warehouse, List.of(new OrderItemCommand(sku, quantity)),
                null, ACTOR).getId();
        inbounds.confirm(id, ACTOR);
        return id;
    }

    private WarehouseStock stock(long warehouse, long sku, long physical, long available, long locked) {
        WarehouseStock row = stocks.findByWarehouseAndSku(warehouse, sku);
        assertNotNull(row);
        assertEquals(physical, row.getPhysicalStock());
        assertEquals(available, row.getAvailableStock());
        assertEquals(locked, row.getLockedStock());
        assertEquals(physical, available + locked);
        return row;
    }

    private List<StockFlow> flowTypes(String biz, long bizId, String... expected) {
        List<StockFlow> actual = flows.list(null, null, biz, bizId, null);
        assertEquals(expected.length, actual.size(), biz + " " + bizId);
        List<String> types = actual.stream().map(StockFlow::getFlowType).sorted().toList();
        assertEquals(Arrays.stream(expected).sorted().toList(), types);
        for (StockFlow flow : actual) {
            assertEquals(biz, flow.getBizType());
            assertEquals(bizId, flow.getBizId());
            assertEquals(ACTOR, flow.getOperatorId());
            assertNotNull(flow.getWarehouseId());
            assertNotNull(flow.getSkuId());
            assertEquals(flow.getBeforePhysical() + flow.getDeltaPhysical(), flow.getAfterPhysical());
            assertEquals(flow.getBeforeAvailable() + flow.getDeltaAvailable(), flow.getAfterAvailable());
            assertEquals(flow.getBeforeLocked() + flow.getDeltaLocked(), flow.getAfterLocked());
            assertEquals(flow.getBeforePhysical(), flow.getBeforeAvailable() + flow.getBeforeLocked());
            assertEquals(flow.getAfterPhysical(), flow.getAfterAvailable() + flow.getAfterLocked());
        }
        return actual;
    }

    private void alert(long warehouse, long sku, long available, long safety, String status) {
        List<String> rows = jdbc.query("""
                SELECT CONCAT(status, ':', available_stock, ':', safety_stock)
                FROM stock_alert WHERE warehouse_id = ? AND sku_id = ?
                """, (rs, row) -> rs.getString(1), warehouse, sku);
        assertEquals(List.of(status + ":" + available + ":" + safety), rows);
    }

    private void error(ErrorCode expected, Runnable action) {
        assertEquals(expected, assertThrows(BusinessException.class, action::run).getErrorCode());
    }

    private String reserveResult(long orderId) {
        try {
            outbounds.reserve(orderId, ACTOR);
            return "OK";
        } catch (BusinessException failure) {
            return failure.getCode();
        }
    }

    private <T> List<T> simultaneously(Callable<T> left, Callable<T> right) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<T> first = () -> { ready.countDown(); start.await(); return left.call(); };
            Callable<T> second = () -> { ready.countDown(); start.await(); return right.call(); };
            Future<T> one = executor.submit(first);
            Future<T> two = executor.submit(second);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            return List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void inboundFirstRowAndRepeatedConfirm() {
        Fixture f = fixture(1, 15);
        long sku = f.skus().get(0);
        assertEquals(0L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM warehouse_stock WHERE warehouse_id=? AND sku_id=?",
                Long.class, f.warehouse(), sku));
        long order = seed(f.warehouse(), sku, 10);
        stock(f.warehouse(), sku, 10, 10, 0);
        assertEquals("COMPLETED", inbounds.get(order).getStatus());
        alert(f.warehouse(), sku, 10, 15, "ACTIVE");
        List<StockFlow> events = flowTypes("INBOUND", order, "INBOUND");
        assertEquals(f.warehouse(), events.get(0).getWarehouseId());
        assertEquals(sku, events.get(0).getSkuId());
        error(ErrorCode.ORDER_STATE_CONFLICT, () -> inbounds.confirm(order, ACTOR));
        stock(f.warehouse(), sku, 10, 10, 0);
        flowTypes("INBOUND", order, "INBOUND");
    }

    @Test
    void twoInboundOrdersCreateFirstStockRowOnce() throws Exception {
        Fixture f = fixture(1, 0);
        long sku = f.skus().get(0);
        long a = inbounds.create(f.warehouse(), List.of(new OrderItemCommand(sku, 10L)),
                null, ACTOR).getId();
        long b = inbounds.create(f.warehouse(), List.of(new OrderItemCommand(sku, 20L)),
                null, ACTOR).getId();
        List<String> results = simultaneously(
                () -> { inbounds.confirm(a, ACTOR); return "OK"; },
                () -> { inbounds.confirm(b, ACTOR); return "OK"; });
        assertEquals(List.of("OK", "OK"), results);
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM warehouse_stock WHERE warehouse_id=? AND sku_id=?",
                Long.class, f.warehouse(), sku));
        stock(f.warehouse(), sku, 30, 30, 0);
        assertEquals("COMPLETED", inbounds.get(a).getStatus());
        assertEquals("COMPLETED", inbounds.get(b).getStatus());
        flowTypes("INBOUND", a, "INBOUND");
        flowTypes("INBOUND", b, "INBOUND");
        alert(f.warehouse(), sku, 30, 0, "RESOLVED");
    }

    @Test
    void uniqueUpsertAlsoWorksWhenTwoTransactionsRaceOnTheStockRow() throws Exception {
        Fixture f = fixture(1, 0);
        long sku = f.skus().get(0);
        List<Integer> results = simultaneously(
                () -> transactions.execute(status -> stocks.inboundUpsert(f.warehouse(), sku, 10L)),
                () -> transactions.execute(status -> stocks.inboundUpsert(f.warehouse(), sku, 20L)));
        assertEquals(2, results.size());
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM warehouse_stock WHERE warehouse_id=? AND sku_id=?",
                Long.class, f.warehouse(), sku));
        stock(f.warehouse(), sku, 30, 30, 0);
    }

    @Test
    void outboundReserveReleaseReserveConfirmAndAlertTransitions() {
        Fixture f = fixture(1, 80);
        long sku = f.skus().get(0);
        seed(f.warehouse(), sku, 100);
        alert(f.warehouse(), sku, 100, 80, "RESOLVED");
        long id = outbounds.create(f.warehouse(), List.of(new OrderItemCommand(sku, 30L)),
                null, ACTOR).getId();
        outbounds.reserve(id, ACTOR);
        assertEquals("RESERVED", outbounds.get(id).getStatus());
        stock(f.warehouse(), sku, 100, 70, 30);
        alert(f.warehouse(), sku, 70, 80, "ACTIVE");
        flowTypes("OUTBOUND", id, "OUTBOUND_LOCK");
        outbounds.release(id, ACTOR);
        assertEquals("CREATED", outbounds.get(id).getStatus());
        stock(f.warehouse(), sku, 100, 100, 0);
        alert(f.warehouse(), sku, 100, 80, "RESOLVED");
        flowTypes("OUTBOUND", id, "OUTBOUND_LOCK", "OUTBOUND_RELEASE");
        outbounds.reserve(id, ACTOR);
        stock(f.warehouse(), sku, 100, 70, 30);
        outbounds.confirm(id, ACTOR);
        assertEquals("COMPLETED", outbounds.get(id).getStatus());
        stock(f.warehouse(), sku, 70, 70, 0);
        alert(f.warehouse(), sku, 70, 80, "ACTIVE");
        flowTypes("OUTBOUND", id, "OUTBOUND_LOCK", "OUTBOUND_RELEASE",
                "OUTBOUND_LOCK", "OUTBOUND");
        error(ErrorCode.ORDER_STATE_CONFLICT, () -> outbounds.confirm(id, ACTOR));
        stock(f.warehouse(), sku, 70, 70, 0);
        flowTypes("OUTBOUND", id, "OUTBOUND_LOCK", "OUTBOUND_RELEASE",
                "OUTBOUND_LOCK", "OUTBOUND");
    }

    @Test
    void twoThreadsReserveTheSameOutboundOrderOnlyOnce() throws Exception {
        Fixture f = fixture(1, 80);
        long sku = f.skus().get(0);
        seed(f.warehouse(), sku, 100);
        long id = outbounds.create(f.warehouse(), List.of(new OrderItemCommand(sku, 30L)),
                null, ACTOR).getId();
        List<String> results = simultaneously(() -> reserveResult(id), () -> reserveResult(id));
        assertEquals(1, results.stream().filter("OK"::equals).count());
        assertEquals(1, results.stream().filter("ORDER_STATE_CONFLICT"::equals).count());
        assertEquals("RESERVED", outbounds.get(id).getStatus());
        stock(f.warehouse(), sku, 100, 70, 30);
        flowTypes("OUTBOUND", id, "OUTBOUND_LOCK");
        alert(f.warehouse(), sku, 70, 80, "ACTIVE");
    }

    @Test
    void twoOrdersCannotReserveMoreThanOneSharedStockRowContains() throws Exception {
        Fixture f = fixture(1, 5);
        long sku = f.skus().get(0);
        seed(f.warehouse(), sku, 10);
        long a = outbounds.create(f.warehouse(), List.of(new OrderItemCommand(sku, 7L)),
                null, ACTOR).getId();
        long b = outbounds.create(f.warehouse(), List.of(new OrderItemCommand(sku, 7L)),
                null, ACTOR).getId();
        List<String> results = simultaneously(() -> reserveResult(a), () -> reserveResult(b));
        assertEquals(1, results.stream().filter("OK"::equals).count());
        assertEquals(1, results.stream().filter("INSUFFICIENT_AVAILABLE_STOCK"::equals).count());
        assertEquals(1, List.of(outbounds.get(a).getStatus(), outbounds.get(b).getStatus())
                .stream().filter("RESERVED"::equals).count());
        stock(f.warehouse(), sku, 10, 3, 7);
        assertEquals(1, flows.list(null, null, "OUTBOUND", a, null).size()
                + flows.list(null, null, "OUTBOUND", b, null).size());
        alert(f.warehouse(), sku, 3, 5, "ACTIVE");
    }

    @Test
    void conditionUpdateItselfPreventsOversellWithoutOrderSerialization() throws Exception {
        Fixture f = fixture(1, 0);
        long sku = f.skus().get(0);
        jdbc.update("""
                INSERT INTO warehouse_stock
                    (warehouse_id, sku_id, physical_stock, available_stock, locked_stock)
                VALUES (?, ?, 10, 10, 0)
                """, f.warehouse(), sku);
        List<Integer> results = simultaneously(
                () -> transactions.execute(status -> stocks.reserve(f.warehouse(), sku, 7L)),
                () -> transactions.execute(status -> stocks.reserve(f.warehouse(), sku, 7L)));
        assertEquals(1, results.stream().filter(rows -> rows == 1).count());
        assertEquals(1, results.stream().filter(rows -> rows == 0).count());
        stock(f.warehouse(), sku, 10, 3, 7);
    }

    @Test
    void thirdSkuFailureRollsBackInventoryFlowsOrderAndAlerts() {
        Fixture f = fixture(3, 95);
        long first = f.skus().get(0);
        long second = f.skus().get(1);
        long third = f.skus().get(2);
        seed(f.warehouse(), first, 100);
        seed(f.warehouse(), second, 100);
        seed(f.warehouse(), third, 5);
        long id = outbounds.create(f.warehouse(), List.of(
                new OrderItemCommand(first, 10L),
                new OrderItemCommand(second, 10L),
                new OrderItemCommand(third, 10L)), null, ACTOR).getId();
        error(ErrorCode.INSUFFICIENT_AVAILABLE_STOCK, () -> outbounds.reserve(id, ACTOR));
        assertEquals("CREATED", outbounds.get(id).getStatus());
        stock(f.warehouse(), first, 100, 100, 0);
        stock(f.warehouse(), second, 100, 100, 0);
        stock(f.warehouse(), third, 5, 5, 0);
        flowTypes("OUTBOUND", id);
        alert(f.warehouse(), first, 100, 95, "RESOLVED");
        alert(f.warehouse(), second, 100, 95, "RESOLVED");
        alert(f.warehouse(), third, 5, 95, "ACTIVE");
    }

    @Test
    void outboundCancelCreatedAndReservedHaveDifferentInventoryEffects() {
        Fixture f = fixture(1, 5);
        long sku = f.skus().get(0);
        seed(f.warehouse(), sku, 10);
        long created = outbounds.create(f.warehouse(), List.of(new OrderItemCommand(sku, 3L)),
                null, ACTOR).getId();
        outbounds.cancel(created, ACTOR);
        assertEquals("CANCELLED", outbounds.get(created).getStatus());
        stock(f.warehouse(), sku, 10, 10, 0);
        flowTypes("OUTBOUND", created);
        long reserved = outbounds.create(f.warehouse(), List.of(new OrderItemCommand(sku, 3L)),
                null, ACTOR).getId();
        outbounds.reserve(reserved, ACTOR);
        stock(f.warehouse(), sku, 10, 7, 3);
        outbounds.cancel(reserved, ACTOR);
        assertEquals("CANCELLED", outbounds.get(reserved).getStatus());
        stock(f.warehouse(), sku, 10, 10, 0);
        flowTypes("OUTBOUND", reserved, "OUTBOUND_LOCK", "OUTBOUND_RELEASE");
        alert(f.warehouse(), sku, 10, 5, "RESOLVED");
        error(ErrorCode.ORDER_STATE_CONFLICT, () -> outbounds.cancel(reserved, ACTOR));
        stock(f.warehouse(), sku, 10, 10, 0);
    }

    @Test
    void transferMovesPhysicalOnlyAtDispatchAndReceiveAndRejectsRepeats() {
        Fixture f = fixture(1, 80);
        long source = f.warehouse();
        long target = anotherWarehouse();
        long sku = f.skus().get(0);
        seed(source, sku, 100);
        assertEquals(0L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM warehouse_stock WHERE warehouse_id=? AND sku_id=?",
                Long.class, target, sku));
        long id = transfers.create(source, target, List.of(new OrderItemCommand(sku, 30L)),
                null, ACTOR).getId();
        assertEquals("CREATED", transfers.get(id).getStatus());
        transfers.reserve(id, ACTOR);
        assertEquals("RESERVED", transfers.get(id).getStatus());
        stock(source, sku, 100, 70, 30);
        flowTypes("TRANSFER", id, "TRANSFER_LOCK");
        alert(source, sku, 70, 80, "ACTIVE");
        error(ErrorCode.ORDER_STATE_CONFLICT, () -> transfers.reserve(id, ACTOR));
        stock(source, sku, 100, 70, 30);
        transfers.dispatch(id, ACTOR);
        assertEquals("IN_TRANSIT", transfers.get(id).getStatus());
        stock(source, sku, 70, 70, 0);
        flowTypes("TRANSFER", id, "TRANSFER_LOCK", "TRANSFER_OUT");
        error(ErrorCode.ORDER_STATE_CONFLICT, () -> transfers.dispatch(id, ACTOR));
        error(ErrorCode.ORDER_STATE_CONFLICT, () -> transfers.cancel(id, ACTOR));
        stock(source, sku, 70, 70, 0);
        transfers.receive(id, ACTOR);
        assertEquals("COMPLETED", transfers.get(id).getStatus());
        stock(source, sku, 70, 70, 0);
        stock(target, sku, 30, 30, 0);
        assertEquals(100L, stocks.findByWarehouseAndSku(source, sku).getPhysicalStock()
                + stocks.findByWarehouseAndSku(target, sku).getPhysicalStock());
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM warehouse_stock WHERE warehouse_id=? AND sku_id=?",
                Long.class, target, sku));
        List<StockFlow> events = flowTypes("TRANSFER", id,
                "TRANSFER_LOCK", "TRANSFER_OUT", "TRANSFER_IN");
        assertEquals(1, events.stream().filter(flow -> "TRANSFER_IN".equals(flow.getFlowType())
                && target == flow.getWarehouseId()).count());
        alert(source, sku, 70, 80, "ACTIVE");
        alert(target, sku, 30, 80, "ACTIVE");
        error(ErrorCode.ORDER_STATE_CONFLICT, () -> transfers.receive(id, ACTOR));
        stock(target, sku, 30, 30, 0);
        flowTypes("TRANSFER", id, "TRANSFER_LOCK", "TRANSFER_OUT", "TRANSFER_IN");
    }

    @Test
    void transferReleaseAndBothCancellationBranchesAreSingleEffect() {
        Fixture f = fixture(1, 5);
        long source = f.warehouse();
        long target = anotherWarehouse();
        long sku = f.skus().get(0);
        seed(source, sku, 100);
        long created = transfers.create(source, target, List.of(new OrderItemCommand(sku, 10L)),
                null, ACTOR).getId();
        transfers.cancel(created, ACTOR);
        assertEquals("CANCELLED", transfers.get(created).getStatus());
        stock(source, sku, 100, 100, 0);
        flowTypes("TRANSFER", created);

        long released = transfers.create(source, target, List.of(new OrderItemCommand(sku, 10L)),
                null, ACTOR).getId();
        transfers.reserve(released, ACTOR);
        stock(source, sku, 100, 90, 10);
        transfers.release(released, ACTOR);
        assertEquals("CREATED", transfers.get(released).getStatus());
        stock(source, sku, 100, 100, 0);
        flowTypes("TRANSFER", released, "TRANSFER_LOCK", "TRANSFER_RELEASE");
        error(ErrorCode.ORDER_STATE_CONFLICT, () -> transfers.release(released, ACTOR));
        stock(source, sku, 100, 100, 0);
        transfers.reserve(released, ACTOR);
        transfers.cancel(released, ACTOR);
        assertEquals("CANCELLED", transfers.get(released).getStatus());
        stock(source, sku, 100, 100, 0);
        flowTypes("TRANSFER", released, "TRANSFER_LOCK", "TRANSFER_RELEASE",
                "TRANSFER_LOCK", "TRANSFER_RELEASE");
        error(ErrorCode.ORDER_STATE_CONFLICT, () -> transfers.cancel(released, ACTOR));
        alert(source, sku, 100, 5, "RESOLVED");
    }

    @Test
    void stocktakeNegativeDeltaWithLockedStockAndZeroDelta() {
        Fixture f = fixture(1, 80);
        long sku = f.skus().get(0);
        seed(f.warehouse(), sku, 100);
        long outbound = outbounds.create(f.warehouse(), List.of(new OrderItemCommand(sku, 30L)),
                null, ACTOR).getId();
        outbounds.reserve(outbound, ACTOR);
        stock(f.warehouse(), sku, 100, 70, 30);
        long adjusted = stocktakes.create(f.warehouse(),
                List.of(new StocktakeItemCommand(sku, 95L)), null, ACTOR).getId();
        var snapshot = stocktakes.get(adjusted).getItems().get(0);
        assertEquals(100L, snapshot.getSnapshotPhysical());
        assertEquals(70L, snapshot.getSnapshotAvailable());
        assertEquals(30L, snapshot.getSnapshotLocked());
        assertEquals(95L, snapshot.getCountedPhysical());
        stocktakes.confirm(adjusted, ACTOR);
        assertEquals("COMPLETED", stocktakes.get(adjusted).getStatus());
        stock(f.warehouse(), sku, 95, 65, 30);
        alert(f.warehouse(), sku, 65, 80, "ACTIVE");
        flowTypes("STOCKTAKE", adjusted, "STOCKTAKE_ADJUST");
        long noChange = stocktakes.create(f.warehouse(),
                List.of(new StocktakeItemCommand(sku, 95L)), null, ACTOR).getId();
        stocktakes.confirm(noChange, ACTOR);
        assertEquals("COMPLETED", stocktakes.get(noChange).getStatus());
        stock(f.warehouse(), sku, 95, 65, 30);
        flowTypes("STOCKTAKE", noChange);
    }

    @Test
    void stocktakeRejectsChangedSnapshotAndCountBelowLocked() {
        Fixture f = fixture(1, 80);
        long sku = f.skus().get(0);
        seed(f.warehouse(), sku, 100);
        long outbound = outbounds.create(f.warehouse(), List.of(new OrderItemCommand(sku, 30L)),
                null, ACTOR).getId();
        outbounds.reserve(outbound, ACTOR);
        long stale = stocktakes.create(f.warehouse(),
                List.of(new StocktakeItemCommand(sku, 95L)), null, ACTOR).getId();
        long valid = stocktakes.create(f.warehouse(),
                List.of(new StocktakeItemCommand(sku, 90L)), null, ACTOR).getId();
        stocktakes.confirm(valid, ACTOR);
        stock(f.warehouse(), sku, 90, 60, 30);
        error(ErrorCode.STOCK_SNAPSHOT_CHANGED, () -> stocktakes.confirm(stale, ACTOR));
        assertEquals("DRAFT", stocktakes.get(stale).getStatus());
        stock(f.warehouse(), sku, 90, 60, 30);
        flowTypes("STOCKTAKE", stale);
        long below = stocktakes.create(f.warehouse(),
                List.of(new StocktakeItemCommand(sku, 20L)), null, ACTOR).getId();
        error(ErrorCode.COUNT_BELOW_LOCKED, () -> stocktakes.confirm(below, ACTOR));
        assertEquals("DRAFT", stocktakes.get(below).getStatus());
        stock(f.warehouse(), sku, 90, 60, 30);
        flowTypes("STOCKTAKE", below);
        alert(f.warehouse(), sku, 60, 80, "ACTIVE");
    }
}
