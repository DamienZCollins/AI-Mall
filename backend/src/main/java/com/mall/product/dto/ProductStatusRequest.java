package com.mall.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 上下架请求
 */
@Schema(description = "上下架请求")
public record ProductStatusRequest(

        @Schema(description = "状态：1 上架，0 下架", example = "1")
        @NotNull(message = "状态不能为空")
        Integer status
) {
}
