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

    @Transactional
    public InboundOrder cancel(Long id, Long cancelledBy) {
        if (inboundMapper.cancel(id, cancelledBy) == 0) {
            get(id);
            throw BusinessException.conflict(ErrorCode.ORDER_STATE_CONFLICT, "入库单当前状态不可取消");
        }
        return get(id);
    }


    @Transactional
    public InboundOrder create(
            Long warehouseId,
            List<OrderItemCommand> items,
            String remark,
            Long createdBy) {

        checks.warehouse(warehouseId);
        checks.items(items);


        // 4. 创建入库单头
        InboundOrder order = new InboundOrder();

        order.setOrderNo(OrderNumbers.next("IN"));
        order.setWarehouseId(warehouseId);
        order.setStatus("PENDING");
        order.setRemark(remark);
        order.setCreatedBy(createdBy);

        inboundMapper.insert(order);


        // 5. 创建明细
        for (OrderItemCommand command : items) {

            InboundOrderItem item =
                    new InboundOrderItem();

            item.setOrderId(order.getId());
            item.setSkuId(command.skuId());
            item.setQuantity(command.quantity());

            inboundMapper.insertItem(item);
        }


        // 6. 返回完整入库单
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

    @Transactional
    public InboundOrder confirm(Long id, Long confirmedBy) {

        // 1. 锁入库单 + 校验状态
        InboundOrder order =
                lockPendingOrder(id);


        // 2. 校验仓库仍然启用
        checks.warehouse(order.getWarehouseId());



        // 3. 查询订单明细，并按照 skuId 固定顺序
        List<InboundOrderItem> items =
                inboundMapper.findItemsByOrderId(id)
                        .stream()
                        .sorted(
                                Comparator.comparing(
                                        InboundOrderItem::getSkuId
                                )
                        )
                        .toList();


        // 4. 先检查所有 SKU
        for (InboundOrderItem item : items) {

            checks.sku(item.getSkuId());
        }


        // 5. 执行整单入库
        for (InboundOrderItem item : items) {

            Long quantity =
                    item.getQuantity();


            // 加库存：
            // 第一次 → INSERT
            // 已存在 → UPDATE
            stockMapper.inboundUpsert(
                    order.getWarehouseId(),
                    item.getSkuId(),
                    quantity
            );


            // 查询修改后的库存
            WarehouseStock stock =
                    stockMapper.findForUpdate(
                            order.getWarehouseId(),
                            item.getSkuId()
                    );


            // after
            long afterPhysical =
                    stock.getPhysicalStock();

            long afterAvailable =
                    stock.getAvailableStock();

            long afterLocked =
                    stock.getLockedStock();


            // 根据入库变化反推 before
            long beforePhysical =
                    afterPhysical - quantity;

            long beforeAvailable =
                    afterAvailable - quantity;

            long beforeLocked =
                    afterLocked;


            // 6. 写库存流水
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


        // 7. 修改订单状态
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


        // 8. 返回最新订单
        return get(id);
    }


}
