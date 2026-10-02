package com.mall.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 新增商品请求
 */
@Schema(description = "新增商品请求")
public record ProductCreateRequest(

        @Schema(description = "商品名称", example = "轻量透气跑步鞋")
        @NotBlank(message = "商品名称不能为空")
        @Size(max = 128, message = "商品名称长度不能超过 128")
        String name,

        @Schema(description = "品牌", example = "沃兰")
        @Size(max = 64, message = "品牌长度不能超过 64")
        String brand,

        @Schema(description = "分类 ID", example = "1001")
        @NotNull(message = "分类不能为空")
        Long categoryId,

        @Schema(description = "商品描述")
        @Size(max = 2000, message = "商品描述长度不能超过 2000")
        String description,

        @Schema(description = "卖点", example = "网面透气 日常通勤")
        @Size(max = 255, message = "卖点长度不能超过 255")
        String sellingPoint,

        @Schema(description = "售价", example = "399.00")
        @NotNull(message = "售价不能为空")
        @DecimalMin(value = "0.01", message = "售价必须大于 0")
        @Digits(integer = 8, fraction = 2, message = "售价格式不正确")
        BigDecimal price,

        @Schema(description = "库存", example = "100")
        @NotNull(message = "库存不能为空")
        @PositiveOrZero(message = "库存不能为负数")
        Integer stock,

        @Schema(description = "商品图片 URL")
        @Size(max = 512, message = "图片 URL 长度不能超过 512")
        String imageUrl
) {
}
