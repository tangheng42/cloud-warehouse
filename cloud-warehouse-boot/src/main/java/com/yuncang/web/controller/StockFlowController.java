package com.yuncang.web.controller;

import com.yuncang.biz.service.StockFlowQueryService;
import com.yuncang.common.ApiResponse;
import com.yuncang.common.PageResult;
import com.yuncang.web.Responses;
import com.yuncang.web.vo.StockFlowVO;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@Tag(name = "库存流水")
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/stock-flows")
public class StockFlowController {
    private final StockFlowQueryService service;

    public StockFlowController(StockFlowQueryService service) { this.service = service; }

    @Operation(summary = "分页查询库存流水")
    @GetMapping
    public ApiResponse<PageResult<StockFlowVO>> list(
            @RequestParam(required = false) @Positive Long warehouseId,
            @RequestParam(required = false) @Positive Long skuId,
            @RequestParam(required = false) @Pattern(regexp = "INBOUND|OUTBOUND|TRANSFER|STOCKTAKE") String bizType,
            @RequestParam(required = false) @Positive Long bizId,
            @RequestParam(required = false) @Pattern(regexp = "INBOUND|OUTBOUND_LOCK|OUTBOUND_RELEASE|OUTBOUND|TRANSFER_LOCK|TRANSFER_RELEASE|TRANSFER_OUT|TRANSFER_IN|STOCKTAKE_ADJUST") String flowType,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Responses.ok(Responses.page(service.list(warehouseId, skuId, bizType,
                bizId, flowType, page, size), Views::of));
    }

    @Operation(summary = "库存流水详情")
    @GetMapping("/{id}")
    public ApiResponse<StockFlowVO> get(@PathVariable @Positive Long id) {
        return Responses.ok(Views.of(service.get(id)));
    }
}
