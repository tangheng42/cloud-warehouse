package com.yuncang.biz.service;

import com.yuncang.biz.entity.StockAlert;
import com.yuncang.biz.entity.WarehouseStock;
import com.yuncang.biz.mapper.StockAlertMapper;
import com.yuncang.biz.mapper.StockMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Called inside the same transaction as the inventory or safety stock change. */
@Service
public class StockAlertService {
    private final StockAlertMapper alerts;
    private final StockMapper stocks;
    private final StockDetailCache cache;
    private final ObjectProvider<StockAlertEventSink> eventSink;

    public StockAlertService(StockAlertMapper alerts, StockMapper stocks, StockDetailCache cache,
            ObjectProvider<StockAlertEventSink> eventSink) {
        this.alerts = alerts;
        this.stocks = stocks;
        this.cache = cache;
        this.eventSink = eventSink;
    }

    public void refresh(Long warehouseId, Long skuId) {
        WarehouseStock stock = stocks.findByWarehouseAndSku(warehouseId, skuId);
        if (stock == null) return;
        StockAlert alert = alerts.findByWarehouseAndSku(warehouseId, skuId);
        boolean active = stock.getSafetyStock() > 0
                && stock.getAvailableStock() < stock.getSafetyStock();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (alert == null) {
            alert = new StockAlert();
            alert.setWarehouseId(warehouseId);
            alert.setSkuId(skuId);
            alert.setTriggeredAt(now);
            if (!active) alert.setResolvedAt(now);
            alert.setStatus(active ? "ACTIVE" : "RESOLVED");
            alert.setAvailableStock(stock.getAvailableStock());
            alert.setSafetyStock(stock.getSafetyStock());
            alerts.insert(alert);
            if (active) emit(alert, null);
            cache.invalidateAfterCommit(warehouseId, skuId);
            return;
        }
        String oldStatus = alert.getStatus();
        if (active && !"ACTIVE".equals(alert.getStatus())) {
            alert.setTriggeredAt(now);
            alert.setResolvedAt(null);
        } else if (!active && "ACTIVE".equals(alert.getStatus())) {
            alert.setResolvedAt(now);
        }
        alert.setStatus(active ? "ACTIVE" : "RESOLVED");
        alert.setAvailableStock(stock.getAvailableStock());
        alert.setSafetyStock(stock.getSafetyStock());
        alerts.update(alert);
        if (!alert.getStatus().equals(oldStatus)) emit(alert, oldStatus);
        cache.invalidateAfterCommit(warehouseId, skuId);
    }

    private void emit(StockAlert alert, String oldStatus) {
        eventSink.ifAvailable(sink -> sink.changed(alert.getId(), alert.getWarehouseId(),
                alert.getSkuId(), oldStatus, alert.getStatus(), alert.getAvailableStock(),
                alert.getSafetyStock()));
    }

    public void refreshSku(Long skuId) {
        for (WarehouseStock stock : stocks.listBySku(skuId)) {
            // Serializes alert changes with inventory changes on this stock row.
            stocks.findForUpdate(stock.getWarehouseId(), skuId);
            refresh(stock.getWarehouseId(), skuId);
        }
    }
}
