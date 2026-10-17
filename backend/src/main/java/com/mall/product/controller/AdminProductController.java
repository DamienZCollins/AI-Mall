package com.mall.product.controller;

import com.mall.common.result.PageResult;
import com.mall.common.result.Result;
import com.mall.product.dto.ProductCreateRequest;
import com.mall.product.dto.ProductQuery;
import com.mall.product.dto.ProductStatusRequest;
import com.mall.product.dto.ProductUpdateRequest;
import com.mall.product.dto.ProductView;
import com.mall.product.service.ProductImageService;
import com.mall.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 管理员商品维护
 */
@Tag(name = "管理员-商品")
@RestController
@RequestMapping("/api/admin/products")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminProductController {

    private final ProductService productService;
    private final ProductImageService productImageService;

    public AdminProductController(ProductService productService, ProductImageService productImageService) {
        this.productService = productService;
        this.productImageService = productImageService;
    }

    @Operation(summary = "新增商品，初始为下架状态")
    @PostMapping
    public Result<ProductView> create(@Valid @RequestBody ProductCreateRequest request) {
        return Result.success(productService.create(request));
    }

    @Operation(summary = "编辑商品")
    @PutMapping("/{id}")
    public Result<ProductView> update(@PathVariable @Positive(message = "商品 ID 必须为正数") Long id,
                                      @Valid @RequestBody ProductUpdateRequest request) {
        return Result.success(productService.update(id, request));
    }

    @Operation(summary = "上架或下架商品")
    @PatchMapping("/{id}/status")
    public Result<ProductView> changeStatus(@PathVariable @Positive(message = "商品 ID 必须为正数") Long id,
                                            @Valid @RequestBody ProductStatusRequest request) {
        return Result.success(productService.changeStatus(id, request));
    }

    @Operation(summary = "分页查询商品，可按状态过滤")
    @GetMapping
    public Result<PageResult<ProductView>> list(@Valid ProductQuery query) {
        return Result.success(productService.list(query));
    }

    @Operation(summary = "上传商品图片，返回可访问的 URL")
    @PostMapping("/images")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file) {
        return Result.success(productImageService.upload(file));
    }
}
