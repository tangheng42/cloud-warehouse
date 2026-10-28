package com.yuncang.biz.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.yuncang.biz.entity.TransferOrder;
import com.yuncang.biz.entity.TransferOrderItem;
import com.yuncang.biz.entity.WarehouseStock;
import com.yuncang.biz.mapper.StockMapper;
import com.yuncang.biz.mapper.TransferMapper;
import com.yuncang.biz.service.command.OrderItemCommand;
import com.yuncang.biz.service.command.OrderNumbers;
import com.yuncang.common.BusinessException;
import com.yuncang.common.ErrorCode;
import com.yuncang.common.PageResult;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.function.BiFunction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理跨仓调拨的预占、发货和收货。
 * 发货扣减源仓库存，收货增加目标仓库存；在途期间不计入目标仓库存。
 */
@Service
public class TransferService {
    private final TransferMapper mapper;
    private final OrderCreationChecks checks;
    private final StockMapper stocks;
    private final StockFlowWriter flows;
    private final StockAlertService alerts;
    private final StockDetailCache cache;

    public TransferService(TransferMapper mapper, OrderCreationChecks checks, StockMapper stocks,
            StockFlowWriter flows, StockAlertService alerts, StockDetailCache cache) {
        this.mapper = mapper;
        this.checks = checks;
        this.stocks = stocks;
        this.flows = flows;
        this.alerts = alerts;
        this.cache = cache;
    }

    public TransferOrder get(Long id) {
        TransferOrder order = mapper.findById(id);
        if (order == null) throw BusinessException.notFound("调拨单");
        order.setItems(mapper.findItemsByOrderId(id));
        return order;
    }

    public PageResult<TransferOrder> list(Long sourceWarehouseId, Long targetWarehouseId,
            String status, int page, int size) {
        PageHelper.startPage(page, size);
        List<TransferOrder> orders = mapper.list(sourceWarehouseId, targetWarehouseId, status);
        long total = new PageInfo<>(orders).getTotal();
        if (!orders.isEmpty()) {
            Map<Long, List<TransferOrderItem>> items = mapper.findItemsByOrderIds(
                    orders.stream().map(TransferOrder::getId).toList()).stream()
                    .collect(Collectors.groupingBy(TransferOrderItem::getOrderId));
            orders.forEach(order -> order.setItems(items.getOrDefault(order.getId(), List.of())));
        }
        return new PageResult<>(orders, page, size, total);
    }

    /**
     * 创建调拨单，不改变库存；源仓与目标仓必须不同且启用，SKU 不可重复。
     */
    @Transactional
    public TransferOrder create(Long sourceWarehouseId, Long targetWarehouseId,
            List<OrderItemCommand> items, String remark, Long createdBy) {
        if (sourceWarehouseId.equals(targetWarehouseId)) {
            throw BusinessException.conflict(ErrorCode.SAME_WAREHOUSE, "源仓和目标仓不能相同");
        }
        checkWarehouses(sourceWarehouseId, targetWarehouseId);
        checks.items(items);
        TransferOrder order = new TransferOrder();
        order.setOrderNo(OrderNumbers.next("TR"));
        order.setSourceWarehouseId(sourceWarehouseId);
        order.setTargetWarehouseId(targetWarehouseId);
        order.setStatus("CREATED");
        order.setRemark(remark);
        order.setCreatedBy(createdBy);
        mapper.insert(order);
        for (OrderItemCommand command : items) {
            TransferOrderItem item = new TransferOrderItem();
            item.setOrderId(order.getId());
            item.setSkuId(command.skuId());
            item.setQuantity(command.quantity());
            mapper.insertItem(item);
        }
        return get(order.getId());
    }

    private TransferOrder lock(Long id, String expected) {
        TransferOrder order = mapper.findForUpdate(id);
        if (order == null) throw BusinessException.notFound("调拨单");
        if (!expected.equals(order.getStatus())) {
            throw BusinessException.conflict(ErrorCode.ORDER_STATE_CONFLICT, "调拨单当前状态不可执行此操作");
        }
        return order;
    }

    /**
     * 按 SKU ID 固定库存操作顺序，降低多明细并发操作时的死锁风险。
     */
    private List<TransferOrderItem> sortedItems(Long id) {
        return mapper.findItemsByOrderId(id).stream()
                .sorted(Comparator.comparing(TransferOrderItem::getSkuId)).toList();
    }

    private void requireActive(TransferOrder order, List<TransferOrderItem> items) {
        checkWarehouses(order.getSourceWarehouseId(), order.getTargetWarehouseId());
        items.forEach(item -> checks.sku(item.getSkuId()));
    }

    private void checkWarehouses(Long sourceId, Long targetId) {
        checks.warehouse(Math.min(sourceId, targetId));
        checks.warehouse(Math.max(sourceId, targetId));
    }

    private void changeSource(TransferOrder order, List<TransferOrderItem> items, Long userId,
            String flowType, BiFunction<Long, Long, Integer> update) {
        for (TransferOrderItem item : items) {
            Long skuId = item.getSkuId();
            Long quantity = item.getQuantity();
            Long warehouseId = order.getSourceWarehouseId();
            WarehouseStock before = stocks.findForUpdate(warehouseId, skuId);
            if (before == null) {
                ErrorCode code = "TRANSFER_LOCK".equals(flowType)
                        ? ErrorCode.INSUFFICIENT_AVAILABLE_STOCK : ErrorCode.ORDER_STATE_CONFLICT;
                throw BusinessException.conflict(code, "源仓库存行不存在");
            }
            if (update.apply(skuId, quantity) != 1) {
                ErrorCode code = "TRANSFER_LOCK".equals(flowType)
                        ? ErrorCode.INSUFFICIENT_AVAILABLE_STOCK : ErrorCode.ORDER_STATE_CONFLICT;
                throw BusinessException.conflict(code, "源仓库存不足或状态已变化");
            }
            WarehouseStock after = stocks.findForUpdate(warehouseId, skuId);
            flows.write("TRANSFER", order.getId(), flowType, userId, warehouseId, skuId,
                    before.getPhysicalStock(), before.getAvailableStock(), before.getLockedStock(), after);
            if (!"TRANSFER_OUT".equals(flowType)) alerts.refresh(warehouseId, skuId);
            else cache.invalidateAfterCommit(warehouseId, skuId);
        }
    }

    /**
     * 仅 CREATED 调拨单可预占源仓库存，任一明细不足时整单回滚。
     */
    @Transactional
    public TransferOrder reserve(Long id, Long userId) {
        TransferOrder order = lock(id, "CREATED");
        List<TransferOrderItem> items = sortedItems(id);
        requireActive(order, items);
        changeSource(order, items, userId, "TRANSFER_LOCK",
                (sku, quantity) -> stocks.reserve(order.getSourceWarehouseId(), sku, quantity));
        if (mapper.markReserved(id, userId) != 1) throw stateChanged();
        return get(id);
    }

    /**
     * 仅 RESERVED 调拨单可释放源仓预占，成功后回到 CREATED。
     */
    @Transactional
    public TransferOrder release(Long id, Long userId) {
        TransferOrder order = lock(id, "RESERVED");
        releaseStock(order, sortedItems(id), userId);
        if (mapper.markReleased(id, userId) != 1) throw stateChanged();
        return get(id);
    }

    private void releaseStock(TransferOrder order, List<TransferOrderItem> items, Long userId) {
        changeSource(order, items, userId, "TRANSFER_RELEASE",
                (sku, quantity) -> stocks.release(order.getSourceWarehouseId(), sku, quantity));
    }

    /**
     * 将 RESERVED 调拨单发货为 IN_TRANSIT，扣减源仓实物和锁定库存。
     * 本次事务不增加目标仓库存，目标仓在收货时入账。
     */
    @Transactional
    public TransferOrder dispatch(Long id, Long userId) {
        TransferOrder order = lock(id, "RESERVED");
        List<TransferOrderItem> items = sortedItems(id);
        requireActive(order, items);
        changeSource(order, items, userId, "TRANSFER_OUT",
                (sku, quantity) -> stocks.confirmOutbound(order.getSourceWarehouseId(), sku, quantity));
        if (mapper.markDispatched(id, userId) != 1) throw stateChanged();
        return get(id);
    }

    /**
     * 对 IN_TRANSIT 调拨单整单收货，增加目标仓实物和可用库存并完成单据。
     * 先锁定单据并校验状态，防止重复收货造成库存重复增加。
     */
    @Transactional
    public TransferOrder receive(Long id, Long userId) {
        TransferOrder order = lock(id, "IN_TRANSIT");
        List<TransferOrderItem> items = sortedItems(id);
        requireActive(order, items);
        for (TransferOrderItem item : items) {
            Long warehouseId = order.getTargetWarehouseId();
            Long skuId = item.getSkuId();
            Long quantity = item.getQuantity();
            stocks.inboundUpsert(warehouseId, skuId, quantity);
            WarehouseStock after = stocks.findForUpdate(warehouseId, skuId);
            flows.write("TRANSFER", id, "TRANSFER_IN", userId, warehouseId, skuId,
                    after.getPhysicalStock() - quantity, after.getAvailableStock() - quantity,
                    after.getLockedStock(), after);
            alerts.refresh(warehouseId, skuId);
        }
        if (mapper.markReceived(id, userId) != 1) throw stateChanged();
        return get(id);
    }

    /**
     * 仅允许取消 CREATED 或 RESERVED 调拨单；已预占时先释放源仓库存。
     * 发货后的在途单据不可通过取消回退。
     */
    @Transactional
    public TransferOrder cancel(Long id, Long userId) {
        TransferOrder order = mapper.findForUpdate(id);
        if (order == null) throw BusinessException.notFound("调拨单");
        if ("RESERVED".equals(order.getStatus())) {
            release(id, userId);
        } else if (!"CREATED".equals(order.getStatus())) {
            throw BusinessException.conflict(ErrorCode.ORDER_STATE_CONFLICT, "调拨单当前状态不可取消");
        }
        if (mapper.markCancelled(id, userId) != 1) throw stateChanged();
        return get(id);
    }

    private BusinessException stateChanged() {
        return BusinessException.conflict(ErrorCode.ORDER_STATE_CONFLICT, "调拨单状态已发生变化");
    }
}
