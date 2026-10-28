package com.yuncang.biz.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.yuncang.biz.entity.StocktakeOrder;
import com.yuncang.biz.entity.StocktakeOrderItem;
import com.yuncang.biz.entity.WarehouseStock;
import com.yuncang.biz.mapper.StockMapper;
import com.yuncang.biz.mapper.StocktakeMapper;
import com.yuncang.biz.service.command.OrderNumbers;
import com.yuncang.biz.service.command.StocktakeItemCommand;
import com.yuncang.common.BusinessException;
import com.yuncang.common.ErrorCode;
import com.yuncang.common.PageResult;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理库存盘点快照与差异调整。
 * 确认前核对三类库存均未偏离创建时快照，避免覆盖盘点期间的出入库变化。
 */
@Service
public class StocktakeService {
    private final StocktakeMapper mapper;
    private final StockMapper stocks;
    private final OrderCreationChecks checks;
    private final StockFlowWriter flows;
    private final StockAlertService alerts;

    public StocktakeService(StocktakeMapper mapper, StockMapper stocks, OrderCreationChecks checks,
            StockFlowWriter flows, StockAlertService alerts) {
        this.mapper = mapper;
        this.stocks = stocks;
        this.checks = checks;
        this.flows = flows;
        this.alerts = alerts;
    }

    public StocktakeOrder get(Long id) {
        StocktakeOrder order = mapper.findById(id);
        if (order == null) throw BusinessException.notFound("盘点单");
        order.setItems(mapper.findItemsByOrderId(id));
        return order;
    }

    public PageResult<StocktakeOrder> list(Long warehouseId, String status, int page, int size) {
        PageHelper.startPage(page, size);
        List<StocktakeOrder> orders = mapper.list(warehouseId, status);
        long total = new PageInfo<>(orders).getTotal();
        if (!orders.isEmpty()) {
            Map<Long, List<StocktakeOrderItem>> items = mapper.findItemsByOrderIds(
                    orders.stream().map(StocktakeOrder::getId).toList()).stream()
                    .collect(Collectors.groupingBy(StocktakeOrderItem::getOrderId));
            orders.forEach(order -> order.setItems(items.getOrDefault(order.getId(), List.of())));
        }
        return new PageResult<>(orders, page, size, total);
    }

    /**
     * 创建 DRAFT 盘点单并保存当前实物、可用、锁定库存快照，不调整库存。
     * 每个 SKU 必须存在库存行，且同一单据不可重复。
     */
    @Transactional
    public StocktakeOrder create(Long warehouseId, List<StocktakeItemCommand> items,
            String remark, Long createdBy) {
        checks.warehouse(warehouseId);
        Set<Long> seen = new HashSet<>();
        List<StocktakeItemCommand> sorted = items.stream()
                .sorted(Comparator.comparing(StocktakeItemCommand::skuId)).toList();
        for (StocktakeItemCommand item : sorted) {
            if (!seen.add(item.skuId())) {
                throw BusinessException.conflict(ErrorCode.DUPLICATE_SKU_LINE, "盘点单存在重复 SKU");
            }
            checks.sku(item.skuId());
        }
        StocktakeOrder order = new StocktakeOrder();
        order.setOrderNo(OrderNumbers.next("ST"));
        order.setWarehouseId(warehouseId);
        order.setStatus("DRAFT");
        order.setRemark(remark);
        order.setCreatedBy(createdBy);
        mapper.insert(order);
        for (StocktakeItemCommand command : sorted) {
            WarehouseStock stock = stocks.findForUpdate(warehouseId, command.skuId());
            if (stock == null) {
                throw BusinessException.conflict(ErrorCode.STOCK_ROW_NOT_FOUND, "盘点 SKU 没有库存行");
            }
            StocktakeOrderItem item = new StocktakeOrderItem();
            item.setOrderId(order.getId());
            item.setSkuId(command.skuId());
            item.setSnapshotPhysical(stock.getPhysicalStock());
            item.setSnapshotAvailable(stock.getAvailableStock());
            item.setSnapshotLocked(stock.getLockedStock());
            item.setCountedPhysical(command.countedPhysical());
            mapper.insertItem(item);
        }
        return get(order.getId());
    }

    /**
     * 确认 DRAFT 盘点单，按实盘数量调整实物和可用库存，保留锁定库存。
     * 库存偏离快照或实盘数量低于锁定库存时拒绝确认；全部明细同事务处理。
     * 差异为零的明细不产生调整流水。
     */
    @Transactional
    public StocktakeOrder confirm(Long id, Long confirmedBy) {
        StocktakeOrder order = mapper.findForUpdate(id);
        if (order == null) throw BusinessException.notFound("盘点单");
        if (!"DRAFT".equals(order.getStatus())) {
            throw BusinessException.conflict(ErrorCode.ORDER_STATE_CONFLICT, "盘点单当前状态不可确认");
        }
        checks.warehouse(order.getWarehouseId());
        List<StocktakeOrderItem> items = mapper.findItemsByOrderId(id).stream()
                .sorted(Comparator.comparing(StocktakeOrderItem::getSkuId)).toList();
        for (StocktakeOrderItem item : items) checks.sku(item.getSkuId());
        for (StocktakeOrderItem item : items) {
            Long warehouseId = order.getWarehouseId();
            Long skuId = item.getSkuId();
            WarehouseStock before = stocks.findForUpdate(warehouseId, skuId);
            if (before == null || !before.getPhysicalStock().equals(item.getSnapshotPhysical())
                    || !before.getAvailableStock().equals(item.getSnapshotAvailable())
                    || !before.getLockedStock().equals(item.getSnapshotLocked())) {
                throw BusinessException.conflict(ErrorCode.STOCK_SNAPSHOT_CHANGED, "库存已变化，请重新创建盘点单");
            }
            if (item.getCountedPhysical() < before.getLockedStock()) {
                throw BusinessException.conflict(ErrorCode.COUNT_BELOW_LOCKED, "实盘数量不能低于锁定库存");
            }
            long delta = item.getCountedPhysical() - before.getPhysicalStock();
            if (delta == 0) continue;
            if (stocks.adjustStocktake(warehouseId, skuId, before.getPhysicalStock(),
                    before.getAvailableStock(), before.getLockedStock(), delta) != 1) {
                throw BusinessException.conflict(ErrorCode.STOCK_SNAPSHOT_CHANGED, "库存已变化，请重新创建盘点单");
            }
            WarehouseStock after = stocks.findForUpdate(warehouseId, skuId);
            flows.write("STOCKTAKE", id, "STOCKTAKE_ADJUST", confirmedBy, warehouseId, skuId,
                    before.getPhysicalStock(), before.getAvailableStock(), before.getLockedStock(), after);
            alerts.refresh(warehouseId, skuId);
        }
        if (mapper.confirm(id, confirmedBy) != 1) {
            throw BusinessException.conflict(ErrorCode.ORDER_STATE_CONFLICT, "盘点单状态已发生变化");
        }
        return get(id);
    }

    /**
     * 仅取消 DRAFT 盘点单，不修改库存；不存在或状态冲突时抛出业务异常。
     */
    @Transactional
    public StocktakeOrder cancel(Long id, Long cancelledBy) {
        if (mapper.cancel(id, cancelledBy) == 0) {
            get(id);
            throw BusinessException.conflict(ErrorCode.ORDER_STATE_CONFLICT, "盘点单当前状态不可取消");
        }
        return get(id);
    }
}
