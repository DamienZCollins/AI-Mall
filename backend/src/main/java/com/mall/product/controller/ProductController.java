package com.mall.product.controller;

import com.mall.common.result.PageResult;
import com.mall.common.result.Result;
import com.mall.product.dto.ProductQuery;
import com.mall.product.dto.ProductView;
import com.mall.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户侧商品查询，仅返回上架商品
 */
@Tag(name = "商品")
@RestController
@RequestMapping("/api/products")
@Validated
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "分页查询上架商品")
    @GetMapping
    public Result<PageResult<ProductView>> list(@Valid ProductQuery query) {
        return Result.success(productService.listOnSale(query));
    }

    @Operation(summary = "查询上架商品详情")
    @GetMapping("/{id}")
    public Result<ProductView> detail(@PathVariable @Positive(message = "商品 ID 必须为正数") Long id) {
        return Result.success(productService.getOnSale(id));
    }
}
