package com.yuncang.biz.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.yuncang.biz.entity.OutboundOrder;
import com.yuncang.biz.entity.OutboundOrderItem;
import com.yuncang.biz.entity.StockFlow;
import com.yuncang.biz.entity.WarehouseStock;
import com.yuncang.biz.mapper.OutboundMapper;
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
 * 管理出库单从创建、预占到实际出库的状态流转。
 * 预占将可用库存转为锁定库存；确认出库才扣减实物库存。
 */
@Service
public class OutboundService {
    private final OutboundMapper mapper;
    private final OrderCreationChecks checks;
    private final StockMapper stockMapper;
    private final StockFlowMapper stockFlowMapper;
    private final StockAlertService stockAlertService;
    private final StockDetailCache stockCache;

    public OutboundService(OutboundMapper mapper, OrderCreationChecks checks, StockMapper stockMapper,
                           StockFlowMapper stockFlowMapper, StockAlertService stockAlertService,
                           StockDetailCache stockCache) {
        this.mapper = mapper;
        this.checks = checks;
        this.stockMapper = stockMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.stockAlertService = stockAlertService;
        this.stockCache = stockCache;
    }

    public OutboundOrder get(Long id) {
        OutboundOrder order = mapper.findById(id);
        if (order == null) throw BusinessException.notFound("出库单");
        order.setItems(mapper.findItemsByOrderId(id));
        return order;
    }

    public PageResult<OutboundOrder> list(Long warehouseId, String status, int page, int size) {
        PageHelper.startPage(page, size);
        List<OutboundOrder> orders = mapper.list(warehouseId, status);
        long total = new PageInfo<>(orders).getTotal();
        if (!orders.isEmpty()) {
            Map<Long, List<OutboundOrderItem>> items = mapper.findItemsByOrderIds(
                    orders.stream().map(OutboundOrder::getId).toList()).stream()
                    .collect(Collectors.groupingBy(OutboundOrderItem::getOrderId));
            orders.forEach(order -> order.setItems(items.getOrDefault(order.getId(), List.of())));
        }
        return new PageResult<>(orders, page, size, total);
    }

    /**
     * 创建 CREATED 出库单及明细，不占用库存；仓库和 SKU 必须启用，明细不可重复。
     */
    @Transactional
    public OutboundOrder create(Long warehouseId, List<OrderItemCommand> items, String remark, Long createdBy) {
        checks.warehouse(warehouseId);
        checks.items(items);
        OutboundOrder order = new OutboundOrder();
        order.setOrderNo(OrderNumbers.next("OUT"));
        order.setWarehouseId(warehouseId);
        order.setStatus("CREATED");
        order.setRemark(remark);
        order.setCreatedBy(createdBy);
        mapper.insert(order);
        for (OrderItemCommand command : items) {
            OutboundOrderItem item = new OutboundOrderItem();
            item.setOrderId(order.getId());
            item.setSkuId(command.skuId());
            item.setQuantity(command.quantity());
            mapper.insertItem(item);
        }
        return get(order.getId());
    }

    private OutboundOrder lockCreatedOrder(Long id) {

        OutboundOrder order =
                mapper.findForUpdate(id);

        if (order == null) {
            throw BusinessException.notFound("出库单");
        }

        if (!"CREATED".equals(order.getStatus())) {
            throw BusinessException.conflict(ErrorCode.ORDER_STATE_CONFLICT,
                    "出库单当前状态不可预占"
            );
        }

        return order;
    }

    /**
     * 对 CREATED 出库单整单预占库存，成功后转为 RESERVED。
     * 任一 SKU 可用库存不足时整单回滚；实物库存保持不变。
     */
    @Transactional
    public OutboundOrder reserve(
            Long id,
            Long reservedBy) {

        // 锁定单据并检查旧状态，防止同一出库单被并发重复预占。
        OutboundOrder order =
                lockCreatedOrder(id);


        // 创建后仓库可能已停用，预占前重新校验。
        checks.warehouse(
                order.getWarehouseId()
        );

        List<OutboundOrderItem> items =
                mapper.findItemsByOrderId(id);


        // 固定 SKU 顺序，降低多行库存操作死锁风险
        items.sort(
                Comparator.comparing(
                        OutboundOrderItem::getSkuId
                )
        );

        for (OutboundOrderItem item : items) {

            checks.sku(
                    item.getSkuId()
            );
        }

        for (OutboundOrderItem item : items) {

            Long quantity =
                    item.getQuantity();


            int rows =
                    stockMapper.reserve(
                            order.getWarehouseId(),
                            item.getSkuId(),
                            quantity
                    );

            if (rows != 1) {

                throw BusinessException.conflict(ErrorCode.INSUFFICIENT_AVAILABLE_STOCK,
                        "可用库存不足"
                );
            }

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


            /*
             * reserve 的变化：
             *
             * physical  0
             * available -q
             * locked    +q
             *
             * 所以可以从 after 反推 before
             */
            long beforePhysical =
                    afterPhysical;

            long beforeAvailable =
                    afterAvailable + quantity;

            long beforeLocked =
                    afterLocked - quantity;

            StockFlow flow =
                    new StockFlow();

            flow.setWarehouseId(
                    order.getWarehouseId()
            );

            flow.setSkuId(
                    item.getSkuId()
            );

            flow.setBizType("OUTBOUND");
            flow.setBizId(order.getId());

            flow.setFlowType(
                    "OUTBOUND_LOCK"
            );


            flow.setDeltaPhysical(0L);
            flow.setDeltaAvailable(-quantity);
            flow.setDeltaLocked(quantity);


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
                    reservedBy
            );


            stockFlowMapper.insert(flow);
            stockAlertService.refresh(order.getWarehouseId(), item.getSkuId());
        }

        int rows =
                mapper.markReserved(
                        id,
                        reservedBy
                );

        if (rows != 1) {

            throw BusinessException.conflict(ErrorCode.ORDER_STATE_CONFLICT,
                    "出库单状态已发生变化"
            );
        }


        return get(id);
    }

    /**
     * 释放 RESERVED 出库单的全部预占，恢复可用库存并回到 CREATED。
     * 释放不要求仓库或 SKU 仍启用，避免资源停用后库存无法解锁。
     */
    @Transactional
    public OutboundOrder release(
            Long id,
            Long releasedBy) {

        // 释放与确认共用单据行锁，避免同一笔预占被同时释放和扣减。
        OutboundOrder order =
                lockReservedOrder(id);

        List<OutboundOrderItem> items =
                mapper.findItemsByOrderId(id);


        // 按 SKU ID 固定加锁顺序，降低多明细并发操作的死锁风险。
        items.sort(
                Comparator.comparing(
                        OutboundOrderItem::getSkuId
                )
        );

        for (OutboundOrderItem item : items) {

            Long quantity =
                    item.getQuantity();


            int affected =
                    stockMapper.release(
                            order.getWarehouseId(),
                            item.getSkuId(),
                            quantity
                    );


            /*
             * 正常 RESERVED 状态下，
             * locked 理论上一定足够。
             *
             * 如果这里失败，
             * 说明库存状态出现异常。
             */
            if (affected != 1) {
                throw new BusinessException(
                        ErrorCode.ORDER_STATE_CONFLICT,
                        "锁定库存状态异常，无法释放预占"
                );
            }

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


            /*
             * release：
             *
             * physical   0
             * available +q
             * locked    -q
             *
             * 根据 after 反推 before
             */

            long beforePhysical =
                    afterPhysical;

            long beforeAvailable =
                    afterAvailable - quantity;

            long beforeLocked =
                    afterLocked + quantity;

            StockFlow flow =
                    new StockFlow();

            flow.setWarehouseId(
                    order.getWarehouseId()
            );

            flow.setSkuId(
                    item.getSkuId()
            );

            flow.setBizType("OUTBOUND");
            flow.setBizId(order.getId());

            flow.setFlowType(
                    "OUTBOUND_RELEASE"
            );


            flow.setDeltaPhysical(0L);
            flow.setDeltaAvailable(quantity);
            flow.setDeltaLocked(-quantity);


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
                    releasedBy
            );


            stockFlowMapper.insert(flow);
            stockAlertService.refresh(order.getWarehouseId(), item.getSkuId());
        }

        int affected =
                mapper.markReleased(
                        id,
                        releasedBy
                );

        if (affected != 1) {
            throw new BusinessException(
                    ErrorCode.ORDER_STATE_CONFLICT,
                    "出库单状态已发生变化"
            );
        }


        return get(id);
    }

    private OutboundOrder lockReservedOrder(Long id) {

        OutboundOrder order =
                mapper.findForUpdate(id);

        if (order == null) {
            throw BusinessException.notFound("出库单");
        }

        if (!"RESERVED".equals(order.getStatus())) {
            throw new BusinessException(
                    ErrorCode.ORDER_STATE_CONFLICT,
                    "出库单当前状态不可释放预占"
            );
        }

        return order;
    }

    /**
     * 确认 RESERVED 出库单，扣减实物和锁定库存并转为 COMPLETED。
     * 可用库存已在预占时扣减，此处不再次扣减；流水与状态更新同事务提交。
     */
    @Transactional
    public OutboundOrder confirm(
            Long id,
            Long confirmedBy) {

        // 与释放操作串行处理，确保只扣减仍有效的预占。
        OutboundOrder order =
                lockReservedOrder(id);

        checks.warehouse(
                order.getWarehouseId()
        );

        List<OutboundOrderItem> items =
                mapper.findItemsByOrderId(id);


        // 按 SKU ID 固定加锁顺序，降低多明细并发操作的死锁风险。
        items.sort(
                Comparator.comparing(
                        OutboundOrderItem::getSkuId
                )
        );

        for (OutboundOrderItem item : items) {
            checks.sku(item.getSkuId());
        }

        for (OutboundOrderItem item : items) {

            Long quantity =
                    item.getQuantity();


            int affected =
                    stockMapper.confirmOutbound(
                            order.getWarehouseId(),
                            item.getSkuId(),
                            quantity
                    );


            /*
             * 正常 RESERVED 状态下：
             *
             * locked >= quantity
             * physical >= quantity
             *
             * 如果这里更新失败，
             * 说明订单状态和库存状态已经不一致。
             */
            if (affected != 1) {
                throw new BusinessException(
                        ErrorCode.ORDER_STATE_CONFLICT,
                        "锁定库存状态异常，无法确认出库"
                );
            }

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


            /*
             * confirm：
             *
             * physical  -q
             * available  0
             * locked    -q
             *
             * 根据 after 反推 before
             */

            long beforePhysical =
                    afterPhysical + quantity;

            long beforeAvailable =
                    afterAvailable;

            long beforeLocked =
                    afterLocked + quantity;

            StockFlow flow =
                    new StockFlow();

            flow.setWarehouseId(
                    order.getWarehouseId()
            );

            flow.setSkuId(
                    item.getSkuId()
            );

            flow.setBizType("OUTBOUND");

            flow.setBizId(
                    order.getId()
            );

            flow.setFlowType(
                    "OUTBOUND"
            );


            flow.setDeltaPhysical(
                    -quantity
            );

            flow.setDeltaAvailable(
                    0L
            );

            flow.setDeltaLocked(
                    -quantity
            );


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
            stockCache.invalidateAfterCommit(order.getWarehouseId(), item.getSkuId());
        }

        int affected =
                mapper.markCompleted(
                        id,
                        confirmedBy
                );


        if (affected != 1) {
            throw new BusinessException(
                    ErrorCode.ORDER_STATE_CONFLICT,
                    "出库单状态已发生变化"
            );
        }


        return get(id);
    }

    /**
     * 取消 CREATED 或 RESERVED 出库单；已预占时在同一事务中先释放库存。
     * 已完成或已取消的单据拒绝再次取消。
     */
    @Transactional
    public OutboundOrder cancel(Long id, Long cancelledBy) {
        OutboundOrder order = mapper.findForUpdate(id);
        if (order == null) throw BusinessException.notFound("出库单");
        if ("RESERVED".equals(order.getStatus())) {
            release(id, cancelledBy);
        } else if (!"CREATED".equals(order.getStatus())) {
            throw BusinessException.conflict(ErrorCode.ORDER_STATE_CONFLICT, "出库单当前状态不可取消");
        }
        if (mapper.markCancelled(id, cancelledBy) != 1) {
            throw BusinessException.conflict(ErrorCode.ORDER_STATE_CONFLICT, "出库单状态已发生变化");
        }
        return get(id);
    }

}
