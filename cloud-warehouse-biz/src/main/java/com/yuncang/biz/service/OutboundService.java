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

    @Transactional
    public OutboundOrder reserve(
            Long id,
            Long reservedBy) {

        // 1. 锁住出库单并检查 CREATED
        OutboundOrder order =
                lockCreatedOrder(id);


        // 2. 确认仓库当前仍然启用
        checks.warehouse(
                order.getWarehouseId()
        );


        // 3. 获取明细
        List<OutboundOrderItem> items =
                mapper.findItemsByOrderId(id);


        // 固定 SKU 顺序，降低多行库存操作死锁风险
        items.sort(
                Comparator.comparing(
                        OutboundOrderItem::getSkuId
                )
        );


        // 4. reserve 时重新检查所有 SKU
        for (OutboundOrderItem item : items) {

            checks.sku(
                    item.getSkuId()
            );
        }


        // 5. 整单预占
        for (OutboundOrderItem item : items) {

            Long quantity =
                    item.getQuantity();


            int rows =
                    stockMapper.reserve(
                            order.getWarehouseId(),
                            item.getSkuId(),
                            quantity
                    );


            // 当前这一刻库存不足
            if (rows != 1) {

                throw BusinessException.conflict(ErrorCode.INSUFFICIENT_AVAILABLE_STOCK,
                        "可用库存不足"
                );
            }


            // UPDATE 已经执行，现在读到的是 after
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


            // 6. 写 OUTBOUND_LOCK 流水
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


        // 7. 整单预占完成
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

    @Transactional
    public OutboundOrder release(
            Long id,
            Long releasedBy) {

        // 1. 锁出库单，只允许 RESERVED
        OutboundOrder order =
                lockReservedOrder(id);


        // 2. 查询明细
        List<OutboundOrderItem> items =
                mapper.findItemsByOrderId(id);


        // 固定库存操作顺序
        items.sort(
                Comparator.comparing(
                        OutboundOrderItem::getSkuId
                )
        );


        // 3. 整单释放
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


            // 获取 release 后的库存
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


            // 4. OUTBOUND_RELEASE 流水
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


        // 5. RESERVED → CREATED
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

    @Transactional
    public OutboundOrder confirm(
            Long id,
            Long confirmedBy) {

        // 1. 锁订单，只允许 RESERVED
        OutboundOrder order =
                lockReservedOrder(id);


        // 2. 确认出库时重新检查仓库
        checks.warehouse(
                order.getWarehouseId()
        );


        // 3. 查询订单明细
        List<OutboundOrderItem> items =
                mapper.findItemsByOrderId(id);


        // 固定库存操作顺序，降低死锁风险
        items.sort(
                Comparator.comparing(
                        OutboundOrderItem::getSkuId
                )
        );


        // 4. confirm 时重新检查 SKU 是否仍然启用
        for (OutboundOrderItem item : items) {
            checks.sku(item.getSkuId());
        }


        // 5. 真正确认出库
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


            // 6. 查询更新后的库存，也就是 after
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


            // 7. 写 OUTBOUND 流水
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


        // 8. RESERVED → COMPLETED
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
