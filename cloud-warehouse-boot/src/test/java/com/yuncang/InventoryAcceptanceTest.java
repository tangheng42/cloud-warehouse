package com.yuncang;

import com.yuncang.biz.entity.OutboundOrder;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:yuncangacceptance;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class InventoryAcceptanceTest {
    private static final long ACTOR = 1L;
    @Autowired WarehouseService warehouses;
    @Autowired CategoryService categories;
    @Autowired SkuService skus;
    @Autowired InboundService inbounds;
    @Autowired OutboundService outbounds;
    @Autowired TransferService transfers;
    @Autowired StocktakeService stocktakes;
    @Autowired StockMapper stocks;
    @Autowired StockFlowMapper flows;

    private record Fixture(long warehouseId, long categoryId, List<Long> skuIds) {}

    private Fixture fixture(int skuCount, long initialQuantity) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long warehouseId = warehouses.create("AC-" + suffix, "验收仓", null).getId();
        long categoryId = categories.create("CT-" + suffix, "验收分类").getId();
        List<Long> skuIds = new ArrayList<>();
        List<OrderItemCommand> lines = new ArrayList<>();
        for (int i = 0; i < skuCount; i++) {
            long skuId = skus.create("SK-" + suffix + "-" + i, "商品" + i,
                    categoryId, "件", 2L).getId();
            skuIds.add(skuId);
            lines.add(new OrderItemCommand(skuId, initialQuantity));
        }
        var inbound = inbounds.create(warehouseId, lines, null, ACTOR);
        inbounds.confirm(inbound.getId(), ACTOR);
        return new Fixture(warehouseId, categoryId, skuIds);
    }

    private void stock(long warehouseId, long skuId, long physical, long available, long locked) {
        WarehouseStock row = stocks.findByWarehouseAndSku(warehouseId, skuId);
        assertEquals(physical, row.getPhysicalStock());
        assertEquals(available, row.getAvailableStock());
        assertEquals(locked, row.getLockedStock());
        assertEquals(physical, available + locked);
    }

    private int flowCount(String bizType, long bizId) {
        return flows.list(null, null, bizType, bizId, null).size();
    }

    @Test
    void thirdSkuFailureRollsBackWholeOrder() {
        Fixture fixture = fixture(3, 10);
        List<OrderItemCommand> lines = List.of(
                new OrderItemCommand(fixture.skuIds().get(0), 2L),
                new OrderItemCommand(fixture.skuIds().get(1), 2L),
                new OrderItemCommand(fixture.skuIds().get(2), 11L));
        var order = outbounds.create(fixture.warehouseId(), lines, null, ACTOR);
        BusinessException failure = assertThrows(BusinessException.class,
                () -> outbounds.reserve(order.getId(), ACTOR));
        assertEquals(ErrorCode.INSUFFICIENT_AVAILABLE_STOCK, failure.getErrorCode());
        assertEquals("CREATED", outbounds.get(order.getId()).getStatus());
        for (long skuId : fixture.skuIds()) stock(fixture.warehouseId(), skuId, 10, 10, 0);
        assertEquals(0, flowCount("OUTBOUND", order.getId()));
    }

    @Test
    void sameOrderCanOnlyBeReservedOnceUnderRealConcurrency() throws Exception {
        Fixture fixture = fixture(1, 10);
        long skuId = fixture.skuIds().get(0);
        var order = outbounds.create(fixture.warehouseId(),
                List.of(new OrderItemCommand(skuId, 7L)), null, ACTOR);
        List<String> results = concurrently(
                () -> reserveResult(order.getId()), () -> reserveResult(order.getId()));
        assertEquals(1, results.stream().filter("OK"::equals).count());
        assertEquals(1, results.stream().filter("ORDER_STATE_CONFLICT"::equals).count());
        stock(fixture.warehouseId(), skuId, 10, 3, 7);
        assertEquals(1, flowCount("OUTBOUND", order.getId()));
    }

    @Test
    void competingOrdersCannotOversellSameSku() throws Exception {
        Fixture fixture = fixture(1, 10);
        long skuId = fixture.skuIds().get(0);
        OutboundOrder first = outbounds.create(fixture.warehouseId(),
                List.of(new OrderItemCommand(skuId, 7L)), null, ACTOR);
        OutboundOrder second = outbounds.create(fixture.warehouseId(),
                List.of(new OrderItemCommand(skuId, 7L)), null, ACTOR);
        List<String> results = concurrently(
                () -> reserveResult(first.getId()), () -> reserveResult(second.getId()));
        assertEquals(1, results.stream().filter("OK"::equals).count());
        assertEquals(1, results.stream().filter("INSUFFICIENT_AVAILABLE_STOCK"::equals).count());
        stock(fixture.warehouseId(), skuId, 10, 3, 7);
        assertEquals(1, flowCount("OUTBOUND", first.getId()) + flowCount("OUTBOUND", second.getId()));
    }

    private String reserveResult(long id) {
        try {
            outbounds.reserve(id, ACTOR);
            return "OK";
        } catch (BusinessException error) {
            return error.getCode();
        }
    }

    private List<String> concurrently(Callable<String> first, Callable<String> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<String> a = () -> { ready.countDown(); start.await(); return first.call(); };
            Callable<String> b = () -> { ready.countDown(); start.await(); return second.call(); };
            Future<String> one = executor.submit(a);
            Future<String> two = executor.submit(b);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            return List.of(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void terminalActionsAndStocktakeDeltasLeaveOneFlowPerChange() {
        Fixture fixture = fixture(1, 10);
        long warehouseId = fixture.warehouseId();
        long skuId = fixture.skuIds().get(0);
        long sourceInbound = inbounds.create(warehouseId,
                List.of(new OrderItemCommand(skuId, 1L)), null, ACTOR).getId();
        inbounds.confirm(sourceInbound, ACTOR);
        assertEquals(ErrorCode.ORDER_STATE_CONFLICT, assertThrows(BusinessException.class,
                () -> inbounds.confirm(sourceInbound, ACTOR)).getErrorCode());
        assertEquals(1, flowCount("INBOUND", sourceInbound));

        long created = outbounds.create(warehouseId,
                List.of(new OrderItemCommand(skuId, 1L)), null, ACTOR).getId();
        outbounds.cancel(created, ACTOR);
        assertEquals(0, flowCount("OUTBOUND", created));
        long reserved = outbounds.create(warehouseId,
                List.of(new OrderItemCommand(skuId, 1L)), null, ACTOR).getId();
        outbounds.reserve(reserved, ACTOR);
        outbounds.cancel(reserved, ACTOR);
        assertEquals(2, flowCount("OUTBOUND", reserved));
        stock(warehouseId, skuId, 11, 11, 0);

        long target = warehouses.create("TG-" + UUID.randomUUID().toString().substring(0, 8),
                "目标仓", null).getId();
        long transferCreated = transfers.create(warehouseId, target,
                List.of(new OrderItemCommand(skuId, 1L)), null, ACTOR).getId();
        transfers.cancel(transferCreated, ACTOR);
        assertEquals(0, flowCount("TRANSFER", transferCreated));
        long transferReserved = transfers.create(warehouseId, target,
                List.of(new OrderItemCommand(skuId, 1L)), null, ACTOR).getId();
        transfers.reserve(transferReserved, ACTOR);
        transfers.cancel(transferReserved, ACTOR);
        assertEquals(2, flowCount("TRANSFER", transferReserved));
        stock(warehouseId, skuId, 11, 11, 0);

        long zero = stocktakes.create(warehouseId,
                List.of(new StocktakeItemCommand(skuId, 11L)), null, ACTOR).getId();
        stocktakes.confirm(zero, ACTOR);
        assertEquals(0, flowCount("STOCKTAKE", zero));
        long plus = stocktakes.create(warehouseId,
                List.of(new StocktakeItemCommand(skuId, 13L)), null, ACTOR).getId();
        stocktakes.confirm(plus, ACTOR);
        assertEquals(1, flowCount("STOCKTAKE", plus));
        stock(warehouseId, skuId, 13, 13, 0);
        long minus = stocktakes.create(warehouseId,
                List.of(new StocktakeItemCommand(skuId, 12L)), null, ACTOR).getId();
        stocktakes.confirm(minus, ACTOR);
        assertEquals(1, flowCount("STOCKTAKE", minus));
        stock(warehouseId, skuId, 12, 12, 0);
        assertTrue(inbounds.list(warehouseId, null, 1, 20).items().stream()
                .allMatch(order -> !order.getItems().isEmpty()));
        assertTrue(outbounds.list(warehouseId, null, 1, 20).items().stream()
                .allMatch(order -> !order.getItems().isEmpty()));
        assertTrue(transfers.list(warehouseId, null, null, 1, 20).items().stream()
                .allMatch(order -> !order.getItems().isEmpty()));
        assertTrue(stocktakes.list(warehouseId, null, 1, 20).items().stream()
                .allMatch(order -> !order.getItems().isEmpty()));
    }
}
