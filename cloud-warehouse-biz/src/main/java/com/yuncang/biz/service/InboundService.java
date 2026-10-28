package com.yuncang.biz.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.yuncang.biz.entity.InboundOrder;
import com.yuncang.biz.entity.InboundOrderItem;
import com.yuncang.biz.entity.StockFlow;
import com.yuncang.biz.entity.WarehouseStock;
import com.yuncang.biz.mapper.InboundMapper;
import com.yuncang.biz.mapper.StockFlowMapper;
import com.yuncang.biz.mapper.StockMapper;
import com.yuncang.biz.service.command.OrderItemCommand;
import com.yuncang.biz.service.command.OrderNumbers;
import com.yuncang.common.BusinessException;
import com.yuncang.common.ErrorCode;
import com.yuncang.common.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理入库单及实际入库。创建只保存待入库单，确认时才增加库存。
 * 单据状态、库存变化、流水和预警在同一事务内提交。
 */
@Service
public class InboundService {

    private final InboundMapper inboundMapper;
    private final OrderCreationChecks checks;
    private final StockMapper stockMapper;
    private final StockFlowMapper stockFlowMapper;
    private final StockAlertService stockAlertService;

    public InboundService(
            InboundMapper inboundMapper,
            OrderCreationChecks checks, StockMapper stockMapper, StockFlowMapper stockFlowMapper,
            StockAlertService stockAlertService) {

        this.inboundMapper = inboundMapper;
        this.checks = checks;
        this.stockMapper = stockMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.stockAlertService = stockAlertService;
    }

    public InboundOrder get(Long id) {

        InboundOrder order = inboundMapper.findById(id);

        if (order == null) {
            throw BusinessException.notFound("入库单");
        }

        order.setItems(
                inboundMapper.findItemsByOrderId(id)
        );

        return order;
    }

    public PageResult<InboundOrder> list(Long warehouseId, String status, int page, int size) {
        PageHelper.startPage(page, size);
        List<InboundOrder> orders = inboundMapper.list(warehouseId, status);
        long total = new PageInfo<>(orders).getTotal();
        if (!orders.isEmpty()) {
            Map<Long, List<InboundOrderItem>> items = inboundMapper.findItemsByOrderIds(
                    orders.stream().map(InboundOrder::getId).toList()).stream()
                    .collect(Collectors.groupingBy(InboundOrderItem::getOrderId));
            orders.forEach(order -> order.setItems(items.getOrDefault(order.getId(), List.of())));
        }
        return new PageResult<>(orders, page, size, total);
    }

    /**
     * 取消尚未确认的入库单，不变更库存；不存在或状态不允许时抛出业务异常。
     */
    @Transactional
    public InboundOrder cancel(Long id, Long cancelledBy) {
        if (inboundMapper.cancel(id, cancelledBy) == 0) {
            get(id);
            throw BusinessException.conflict(ErrorCode.ORDER_STATE_CONFLICT, "入库单当前状态不可取消");
        }
        return get(id);
    }


    /**
     * 创建 PENDING 入库单及明细，不增加库存。仓库和 SKU 必须存在且启用，明细不可重复。
     */
    @Transactional
    public InboundOrder create(
            Long warehouseId,
            List<OrderItemCommand> items,
            String remark,
            Long createdBy) {

        checks.warehouse(warehouseId);
        checks.items(items);

        InboundOrder order = new InboundOrder();

        order.setOrderNo(OrderNumbers.next("IN"));
        order.setWarehouseId(warehouseId);
        order.setStatus("PENDING");
        order.setRemark(remark);
        order.setCreatedBy(createdBy);

        inboundMapper.insert(order);

        for (OrderItemCommand command : items) {

            InboundOrderItem item =
                    new InboundOrderItem();

            item.setOrderId(order.getId());
            item.setSkuId(command.skuId());
            item.setQuantity(command.quantity());

            inboundMapper.insertItem(item);
        }

        return get(order.getId());
    }

    private InboundOrder lockPendingOrder(Long id) {

        InboundOrder order =
                inboundMapper.findForUpdate(id);

        if (order == null) {
            throw BusinessException.notFound("入库单");
        }

        if (!"PENDING".equals(order.getStatus())) {
            throw BusinessException.conflict(ErrorCode.ORDER_STATE_CONFLICT,
                    "入库单当前状态不可确认"
            );
        }

        return order;
    }

    /**
     * 确认 PENDING 入库单并增加实物库存、可用库存，锁定库存不变。
     * 先锁单据，再按 SKU 顺序处理库存；任一明细失败则整单回滚。
     * 重复确认抛出状态冲突，不会重复入库。
     */
    @Transactional
    public InboundOrder confirm(Long id, Long confirmedBy) {

        // 在当前事务内锁定单据，阻止并发确认造成重复入库。
        InboundOrder order =
                lockPendingOrder(id);


        // 创建与确认可能间隔较久，实际入库前重新核对仓库状态。
        checks.warehouse(order.getWarehouseId());



        // 按 SKU ID 固定库存操作顺序，降低多明细并发入库时的死锁风险。
        List<InboundOrderItem> items =
                inboundMapper.findItemsByOrderId(id)
                        .stream()
                        .sorted(
                                Comparator.comparing(
                                        InboundOrderItem::getSkuId
                                )
                        )
                        .toList();

        for (InboundOrderItem item : items) {

            checks.sku(item.getSkuId());
        }

        for (InboundOrderItem item : items) {

            Long quantity =
                    item.getQuantity();


            // 首次入库创建库存行；已有库存行则原子累加，避免先查后插的并发竞态。
            stockMapper.inboundUpsert(
                    order.getWarehouseId(),
                    item.getSkuId(),
                    quantity
            );

            WarehouseStock stock =
                    stockMapper.findForUpdate(
                            order.getWarehouseId(),
                            item.getSkuId()
                    );

            long afterPhysical =
                    stock.getPhysicalStock();

            long afterAvailable =
                    stock.getAvailableStock();

            long afterLocked =
                    stock.getLockedStock();


            // 当前读取的是入库后的库存，扣除本次增量以记录流水的变更前值。
            long beforePhysical =
                    afterPhysical - quantity;

            long beforeAvailable =
                    afterAvailable - quantity;

            long beforeLocked =
                    afterLocked;

            StockFlow flow =
                    new StockFlow();

            flow.setWarehouseId(
                    order.getWarehouseId()
            );

            flow.setSkuId(
                    item.getSkuId()
            );

            flow.setBizType("INBOUND");
            flow.setBizId(order.getId());

            flow.setFlowType("INBOUND");


            flow.setDeltaPhysical(quantity);
            flow.setDeltaAvailable(quantity);
            flow.setDeltaLocked(0L);


            flow.setBeforePhysical(
                    beforePhysical
            );

            flow.setBeforeAvailable(
                    beforeAvailable
            );

            flow.setBeforeLocked(
                    beforeLocked
            );


            flow.setAfterPhysical(
                    afterPhysical
            );

            flow.setAfterAvailable(
                    afterAvailable
            );

            flow.setAfterLocked(
                    afterLocked
            );


            flow.setOperatorId(
                    confirmedBy
            );


            stockFlowMapper.insert(flow);
            stockAlertService.refresh(order.getWarehouseId(), item.getSkuId());
        }

        int rows =
                inboundMapper.complete(
                        id,
                        confirmedBy
                );

        if (rows != 1) {

            throw BusinessException.conflict(ErrorCode.ORDER_STATE_CONFLICT,
                    "入库单状态已发生变化"
            );
        }

        return get(id);
    }


}
