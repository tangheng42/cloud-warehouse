package com.yuncang.web.controller;

import com.yuncang.biz.service.StocktakeService;
import com.yuncang.biz.service.command.StocktakeItemCommand;
import com.yuncang.common.ApiResponse;
import com.yuncang.common.PageResult;
import com.yuncang.web.Responses;
import com.yuncang.web.dto.StocktakeCreateDTO;
import com.yuncang.web.security.BaseContext;
import com.yuncang.web.security.RequireRoles;
import com.yuncang.web.service.CreateRequestIdempotencyService;
import com.yuncang.web.vo.StocktakeVO;
import com.yuncang.web.vo.Views;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@Tag(name = "盘点管理")
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/stocktakes")
public class StocktakeController {
    private final StocktakeService service;
    private final CreateRequestIdempotencyService idempotency;

    public StocktakeController(StocktakeService service, CreateRequestIdempotencyService idempotency) {
        this.service = service;
        this.idempotency = idempotency;
    }

    /**
     * 创建单据，支持可选的 Idempotency-Key 请求头。
     * 同一用户、操作和键的相同请求返回已有单据的当前状态；不同请求内容复用该键时报冲突。
     * 未提供该请求头时，每次调用均按新建处理。
     */
    @Operation(summary = "创建盘点单")
    @PostMapping
    public ResponseEntity<ApiResponse<StocktakeVO>> create(@Valid @RequestBody StocktakeCreateDTO dto,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        var items = dto.items().stream()
                .map(x -> new StocktakeItemCommand(x.skuId(), x.countedPhysical())).toList();
        long userId = BaseContext.getUser().getId();
        return ResponseEntity.status(HttpStatus.CREATED).body(Responses.ok(Views.of(
                idempotency.create(key, userId, "STOCKTAKE_CREATE", dto,
                        () -> service.create(dto.warehouseId(), items, dto.remark(), userId),
                        order -> order.getId(), service::get))));
    }

    @Operation(summary = "分页查询盘点单")
    @GetMapping
    public ApiResponse<PageResult<StocktakeVO>> list(@RequestParam(required = false) @Positive Long warehouseId,
            @RequestParam(required = false) @Pattern(regexp = "DRAFT|COMPLETED|CANCELLED") String status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Responses.ok(Responses.page(service.list(warehouseId, status, page, size), Views::of));
    }

    @Operation(summary = "盘点单详情")
    @GetMapping("/{id}")
    public ApiResponse<StocktakeVO> get(@PathVariable @Positive Long id) {
        return Responses.ok(Views.of(service.get(id)));
    }

    @Operation(summary = "确认盘点调整")
    @RequireRoles({"ADMIN", "WAREHOUSE_MANAGER"})
    @PostMapping("/{id}/confirm")
    public ApiResponse<StocktakeVO> confirm(@PathVariable @Positive Long id) {
        return Responses.ok(Views.of(service.confirm(id, BaseContext.getUser().getId())));
    }

    @Operation(summary = "取消盘点")
    @PostMapping("/{id}/cancel")
    public ApiResponse<StocktakeVO> cancel(@PathVariable @Positive Long id) {
        return Responses.ok(Views.of(service.cancel(id, BaseContext.getUser().getId())));
    }
}
