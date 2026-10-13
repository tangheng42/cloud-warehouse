package com.yuncang.web.controller;

import com.yuncang.biz.service.SkuService;
import com.yuncang.common.ApiResponse;
import com.yuncang.common.PageResult;
import com.yuncang.web.Responses;
import com.yuncang.web.dto.SkuCreateDTO;
import com.yuncang.web.dto.SkuUpdateDTO;
import com.yuncang.web.security.RequireAdmin;
import com.yuncang.web.vo.SkuVO;
import com.yuncang.web.vo.Views;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@Tag(name = "SKU 管理")
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/skus")
public class SkuController {
    private final SkuService service;

    public SkuController(SkuService service) { this.service = service; }

    @RequireAdmin
    @Operation(summary = "新增 SKU")
    @PostMapping
    public ResponseEntity<ApiResponse<SkuVO>> create(@Valid @RequestBody SkuCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Responses.ok(Views.of(
                service.create(dto.code(), dto.name(), dto.categoryId(), dto.unit(), dto.safetyStock()))));
    }

    @Operation(summary = "分页查询 SKU")
    @GetMapping
    public ApiResponse<PageResult<SkuVO>> list(@RequestParam(required = false) String code,
            @RequestParam(required = false) String name, @RequestParam(required = false) @Positive Long categoryId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Responses.ok(Responses.page(service.list(code, name, categoryId, enabled, page, size), Views::of));
    }

    @Operation(summary = "查看 SKU 详情")
    @GetMapping("/{id}")
    public ApiResponse<SkuVO> get(@PathVariable @Positive Long id) {
        return Responses.ok(Views.of(service.get(id)));
    }

    @RequireAdmin
    @Operation(summary = "修改 SKU")
    @PutMapping("/{id}")
    public ApiResponse<SkuVO> update(@PathVariable @Positive Long id, @Valid @RequestBody SkuUpdateDTO dto) {
        return Responses.ok(Views.of(service.update(id, dto.name(), dto.categoryId(), dto.unit(), dto.safetyStock())));
    }

    @RequireAdmin
    @Operation(summary = "启用 SKU")
    @PostMapping("/{id}/enable")
    public ApiResponse<SkuVO> enable(@PathVariable @Positive Long id) {
        return Responses.ok(Views.of(service.setEnabled(id, true)));
    }

    @RequireAdmin
    @Operation(summary = "停用 SKU")
    @PostMapping("/{id}/disable")
    public ApiResponse<SkuVO> disable(@PathVariable @Positive Long id) {
        return Responses.ok(Views.of(service.setEnabled(id, false)));
    }
}
