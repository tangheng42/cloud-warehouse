package com.yuncang.web.controller;

import com.yuncang.biz.service.StockQueryService;
import com.yuncang.common.ApiResponse;
import com.yuncang.common.PageResult;
import com.yuncang.web.Responses;
import com.yuncang.web.vo.StockVO;
import com.yuncang.web.vo.Views;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@Tag(name = "库存查询")
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/stocks")
public class StockController {
    private final StockQueryService service;

    public StockController(StockQueryService service) { this.service = service; }

    @Operation(summary = "分页查询库存")
    @GetMapping
    public ApiResponse<PageResult<StockVO>> list(@RequestParam(required = false) @Positive Long warehouseId,
            @RequestParam(required = false) @Positive Long skuId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Responses.ok(Responses.page(service.list(warehouseId, skuId, page, size), Views::of));
    }

    @Operation(summary = "查看仓库 SKU 库存详情")
    @GetMapping("/warehouses/{warehouseId}/skus/{skuId}")
    public ApiResponse<StockVO> get(@PathVariable @Positive Long warehouseId, @PathVariable @Positive Long skuId) {
        return Responses.ok(Views.of(service.get(warehouseId, skuId)));
    }
}
