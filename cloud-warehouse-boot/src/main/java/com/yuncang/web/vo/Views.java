package com.yuncang.web.vo;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.yuncang.biz.entity.AppUser;
import com.yuncang.biz.entity.InboundOrder;
import com.yuncang.biz.entity.OutboundOrder;
import com.yuncang.biz.entity.Sku;
import com.yuncang.biz.entity.SkuCategory;
import com.yuncang.biz.entity.StockAlert;
import com.yuncang.biz.entity.StockFlow;
import com.yuncang.biz.entity.StocktakeOrder;
import com.yuncang.biz.entity.TransferOrder;
import com.yuncang.biz.entity.Warehouse;
import com.yuncang.biz.entity.WarehouseStock;

public final class Views {
    private Views() {}

    private static OffsetDateTime utc(java.time.LocalDateTime time) {
        return time == null ? null : time.atOffset(ZoneOffset.UTC);
    }

    public static UserVO of(AppUser x) {
        return new UserVO(x.getId(), x.getUsername(), x.getRole(), x.getEnabled());
    }
    public static WarehouseVO of(Warehouse x) {
        return new WarehouseVO(x.getId(), x.getCode(), x.getName(), x.getAddress(), x.getEnabled(),
                               utc(x.getCreatedAt()), utc(x.getUpdatedAt()));
    }
    public static CategoryVO of(SkuCategory x) {
        return new CategoryVO(x.getId(), x.getCode(), x.getName(), x.getEnabled(),
                              utc(x.getCreatedAt()), utc(x.getUpdatedAt()));
    }
    public static SkuVO of(Sku x) {
        return new SkuVO(x.getId(), x.getCode(), x.getName(), x.getCategoryId(), x.getUnit(),
                         x.getSafetyStock(), x.getEnabled(), utc(x.getCreatedAt()), utc(x.getUpdatedAt()));
    }
    public static StockVO of(WarehouseStock x) {
        return new StockVO(x.getWarehouseId(), x.getSkuId(), x.getId() != null,
                           x.getPhysicalStock(), x.getAvailableStock(), x.getLockedStock(), x.getSafetyStock());
    }
    public static InboundVO of(InboundOrder x) {

        return new InboundVO(
                x.getId(),
                x.getOrderNo(),
                x.getStatus(),

                x.getItems()
                        .stream()
                        .map(item ->
                                new OrderItemVO(
                                        item.getSkuId(),
                                        item.getQuantity()
                                )
                        )
                        .toList(),

                x.getRemark(),
                x.getCreatedBy(),
                utc(x.getCreatedAt()),
                utc(x.getUpdatedAt()),
                x.getWarehouseId(),
                x.getConfirmedBy(),
                utc(x.getConfirmedAt()),
                x.getCancelledBy(),
                utc(x.getCancelledAt())
        );
    }

    public static OutboundVO of(OutboundOrder x) {
        return new OutboundVO(x.getId(), x.getOrderNo(), x.getStatus(),
                x.getItems().stream().map(i -> new OrderItemVO(i.getSkuId(), i.getQuantity())).toList(),
                x.getRemark(), x.getCreatedBy(), utc(x.getCreatedAt()), utc(x.getUpdatedAt()),
                x.getWarehouseId(), x.getReservedBy(), utc(x.getReservedAt()),
                x.getReleasedBy(), utc(x.getReleasedAt()), x.getConfirmedBy(), utc(x.getConfirmedAt()),
                x.getCancelledBy(), utc(x.getCancelledAt()));
    }

    public static TransferVO of(TransferOrder x) {
        return new TransferVO(x.getId(), x.getOrderNo(), x.getStatus(),
                x.getItems().stream().map(i -> new OrderItemVO(i.getSkuId(), i.getQuantity())).toList(),
                x.getRemark(), x.getCreatedBy(), utc(x.getCreatedAt()), utc(x.getUpdatedAt()),
                x.getSourceWarehouseId(), x.getTargetWarehouseId(),
                x.getReservedBy(), utc(x.getReservedAt()), x.getReleasedBy(), utc(x.getReleasedAt()),
                x.getDispatchedBy(), utc(x.getDispatchedAt()), x.getReceivedBy(), utc(x.getReceivedAt()),
                x.getCancelledBy(), utc(x.getCancelledAt()));
    }

    public static StocktakeVO of(StocktakeOrder x) {
        return new StocktakeVO(x.getId(), x.getOrderNo(), x.getStatus(),
                x.getItems().stream().map(i -> new StocktakeItemVO(i.getSkuId(), i.getSnapshotPhysical(),
                        i.getSnapshotAvailable(), i.getSnapshotLocked(), i.getCountedPhysical())).toList(),
                x.getRemark(), x.getCreatedBy(), utc(x.getCreatedAt()), utc(x.getUpdatedAt()),
                x.getWarehouseId(), x.getConfirmedBy(), utc(x.getConfirmedAt()),
                x.getCancelledBy(), utc(x.getCancelledAt()));
    }

    public static StockFlowVO of(StockFlow x) {
        return new StockFlowVO(x.getId(), x.getWarehouseId(), x.getSkuId(), x.getBizType(), x.getBizId(),
                x.getFlowType(), x.getDeltaPhysical(), x.getDeltaAvailable(), x.getDeltaLocked(),
                x.getBeforePhysical(), x.getBeforeAvailable(), x.getBeforeLocked(),
                x.getAfterPhysical(), x.getAfterAvailable(), x.getAfterLocked(),
                x.getOperatorId(), utc(x.getCreatedAt()));
    }

    public static StockAlertVO of(StockAlert x) {
        return new StockAlertVO(x.getId(), x.getWarehouseId(), x.getSkuId(), x.getAvailableStock(),
                x.getSafetyStock(), x.getStatus(), utc(x.getTriggeredAt()), utc(x.getResolvedAt()));
    }
}
