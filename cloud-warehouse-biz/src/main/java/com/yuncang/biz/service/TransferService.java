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
