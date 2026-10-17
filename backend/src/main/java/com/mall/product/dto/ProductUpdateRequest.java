package com.mall.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 编辑商品请求，字段为 null 表示不修改
 */
@Schema(description = "编辑商品请求")
public record ProductUpdateRequest(

        @Schema(description = "商品名称")
        @Size(max = 128, message = "商品名称长度不能超过 128")
        String name,

        @Schema(description = "品牌")
        @Size(max = 64, message = "品牌长度不能超过 64")
        String brand,

        @Schema(description = "分类 ID")
        Long categoryId,

        @Schema(description = "商品描述")
        @Size(max = 2000, message = "商品描述长度不能超过 2000")
        String description,

        @Schema(description = "卖点")
        @Size(max = 255, message = "卖点长度不能超过 255")
        String sellingPoint,

        @Schema(description = "售价")
        @DecimalMin(value = "0.01", message = "售价必须大于 0")
        @Digits(integer = 8, fraction = 2, message = "售价格式不正确")
        BigDecimal price,

        @Schema(description = "库存")
        @PositiveOrZero(message = "库存不能为负数")
        Integer stock,

        @Schema(description = "商品图片 URL")
        @Size(max = 512, message = "图片 URL 长度不能超过 512")
        String imageUrl
) {
}
