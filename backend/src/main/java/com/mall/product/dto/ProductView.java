package com.mall.product.dto;

import com.mall.product.entity.Product;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 商品信息
 */
@Schema(description = "商品信息")
public record ProductView(

        @Schema(description = "商品 ID")
        Long id,

        @Schema(description = "商品名称")
        String name,

        @Schema(description = "品牌")
        String brand,

        @Schema(description = "分类 ID")
        Long categoryId,

        @Schema(description = "商品描述")
        String description,

        @Schema(description = "卖点")
        String sellingPoint,

        @Schema(description = "售价")
        BigDecimal price,

        @Schema(description = "库存")
        Integer stock,

        @Schema(description = "状态：1 上架，0 下架")
        Integer status,

        @Schema(description = "商品图片 URL")
        String imageUrl
) {

    public static ProductView from(Product product) {
        return new ProductView(
                product.getId(),
                product.getName(),
                product.getBrand(),
                product.getCategoryId(),
                product.getDescription(),
                product.getSellingPoint(),
                product.getPrice(),
                product.getStock(),
                product.getStatus(),
                product.getImageUrl());
    }
}
