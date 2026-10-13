package com.yuncang.web.controller;

import com.yuncang.biz.service.CategoryService;
import com.yuncang.common.ApiResponse;
import com.yuncang.common.PageResult;
import com.yuncang.web.Responses;
import com.yuncang.web.dto.CategoryCreateDTO;
import com.yuncang.web.dto.CategoryUpdateDTO;
import com.yuncang.web.security.RequireAdmin;
import com.yuncang.web.vo.CategoryVO;
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
@Tag(name = "SKU 分类管理")
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/sku-categories")
public class CategoryController {
    private final CategoryService service;

    public CategoryController(CategoryService service) { this.service = service; }

    @RequireAdmin
    @Operation(summary = "新增 SKU 分类")
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryVO>> create(@Valid @RequestBody CategoryCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Responses.ok(Views.of(service.create(dto.code(), dto.name()))));
    }

    @Operation(summary = "分页查询 SKU 分类")
    @GetMapping
    public ApiResponse<PageResult<CategoryVO>> list(@RequestParam(required = false) String name,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Responses.ok(Responses.page(service.list(name, enabled, page, size), Views::of));
    }

    @Operation(summary = "查看 SKU 分类详情")
    @GetMapping("/{id}")
    public ApiResponse<CategoryVO> get(@PathVariable @Positive Long id) {
        return Responses.ok(Views.of(service.get(id)));
    }

    @RequireAdmin
    @Operation(summary = "修改 SKU 分类")
    @PutMapping("/{id}")
    public ApiResponse<CategoryVO> update(@PathVariable @Positive Long id, @Valid @RequestBody CategoryUpdateDTO dto) {
        return Responses.ok(Views.of(service.update(id, dto.name())));
    }

    @RequireAdmin
    @Operation(summary = "启用 SKU 分类")
    @PostMapping("/{id}/enable")
    public ApiResponse<CategoryVO> enable(@PathVariable @Positive Long id) {
        return Responses.ok(Views.of(service.setEnabled(id, true)));
    }

    @RequireAdmin
    @Operation(summary = "停用 SKU 分类")
    @PostMapping("/{id}/disable")
    public ApiResponse<CategoryVO> disable(@PathVariable @Positive Long id) {
        return Responses.ok(Views.of(service.setEnabled(id, false)));
    }
}
