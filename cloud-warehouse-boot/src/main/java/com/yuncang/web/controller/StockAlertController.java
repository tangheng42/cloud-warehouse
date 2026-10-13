package com.yuncang.web.controller;

import com.yuncang.biz.service.StockAlertQueryService;
import com.yuncang.common.ApiResponse;
import com.yuncang.common.PageResult;
import com.yuncang.web.Responses;
import com.yuncang.web.vo.StockAlertVO;
import com.yuncang.web.vo.Views;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@Tag(name = "库存预警")
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/stock-alerts")
public class StockAlertController {
    private final StockAlertQueryService service;

    public StockAlertController(StockAlertQueryService service) { this.service = service; }

    @Operation(summary = "分页查询当前预警")
    @GetMapping
    public ApiResponse<PageResult<StockAlertVO>> list(
            @RequestParam(required = false) @Positive Long warehouseId,
            @RequestParam(required = false) @Positive Long skuId,
            @RequestParam(defaultValue = "ACTIVE") @Pattern(regexp = "ACTIVE|RESOLVED") String status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Responses.ok(Responses.page(service.list(warehouseId, skuId, status, page, size), Views::of));
    }
}
