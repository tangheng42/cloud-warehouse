package com.yuncang.web.controller;

import com.yuncang.biz.service.InboundService;
import com.yuncang.biz.service.command.OrderItemCommand;
import com.yuncang.common.ApiResponse;
import com.yuncang.common.PageResult;
import com.yuncang.web.Responses;
import com.yuncang.web.dto.InboundCreateDTO;
import com.yuncang.web.security.BaseContext;
import com.yuncang.web.service.CreateRequestIdempotencyService;
import com.yuncang.web.vo.InboundVO;
import com.yuncang.web.vo.Views;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "入库管理")
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/inbounds")
public class InboundController {

    private final InboundService service;
    private final CreateRequestIdempotencyService idempotency;

    public InboundController(InboundService service, CreateRequestIdempotencyService idempotency) {
        this.service = service;
        this.idempotency = idempotency;
    }

    @Operation(summary = "分页查询入库单")
    @GetMapping
    public ApiResponse<PageResult<InboundVO>> list(@RequestParam(required = false) @Positive Long warehouseId,
            @RequestParam(required = false) @Pattern(regexp = "PENDING|COMPLETED|CANCELLED") String status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Responses.ok(Responses.page(service.list(warehouseId, status, page, size), Views::of));
    }

    @Operation(summary = "入库单详情")
    @GetMapping("/{id}")
    public ApiResponse<InboundVO> get(@PathVariable @Positive Long id) {
        return Responses.ok(Views.of(service.get(id)));
    }

    @Operation(summary = "取消入库")
    @PostMapping("/{id}/cancel")
    public ApiResponse<InboundVO> cancel(@PathVariable @Positive Long id) {
        return Responses.ok(Views.of(service.cancel(id, BaseContext.getUser().getId())));
    }

    /**
     * 创建单据，支持可选的 Idempotency-Key 请求头。
     * 同一用户、操作和键的相同请求返回已有单据的当前状态；不同请求内容复用该键时报冲突。
     * 未提供该请求头时，每次调用均按新建处理。
     */
    @Operation(summary = "创建入库单")
    @PostMapping
    public ResponseEntity<ApiResponse<InboundVO>> create(
            @Valid @RequestBody InboundCreateDTO dto,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {

        var items = dto.items()
                .stream()
                .map(item ->
                        new OrderItemCommand(
                                item.skuId(),
                                item.quantity()
                        )
                )
                .toList();

        Long currentUserId =
                BaseContext.getUser().getId();

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        Responses.ok(
                                Views.of(
                                        idempotency.create(key, currentUserId, "INBOUND_CREATE", dto,
                                                () -> service.create(dto.warehouseId(), items,
                                                        dto.remark(), currentUserId),
                                                order -> order.getId(), service::get)
                                )
                        )
                );
    }

    @Operation(summary = "确认实际入库")
    @PostMapping("/{id}/confirm")
    public ApiResponse<InboundVO> confirm(
            @PathVariable @Positive Long id) {

        Long currentUserId =
                BaseContext.getUser().getId();

        return Responses.ok(
                Views.of(
                        service.confirm(
                                id,
                                currentUserId
                        )
                )
        );
    }
}
