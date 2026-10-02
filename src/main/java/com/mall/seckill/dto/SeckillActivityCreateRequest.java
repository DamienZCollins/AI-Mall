package com.mall.seckill.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创建秒杀活动请求
 */
@Schema(description = "创建秒杀活动请求")
public record SeckillActivityCreateRequest(

        @Schema(description = "绑定商品 ID", example = "1")
        @NotNull(message = "商品不能为空")
        Long productId,

        @Schema(description = "活动标题", example = "网面透气跑步鞋 限时秒杀")
        @NotBlank(message = "活动标题不能为空")
        @Size(max = 128, message = "活动标题长度不能超过 128")
        String title,

        @Schema(description = "秒杀价", example = "199.00")
        @NotNull(message = "秒杀价不能为空")
        @DecimalMin(value = "0.01", message = "秒杀价必须大于 0")
        @Digits(integer = 8, fraction = 2, message = "秒杀价格式不正确")
        BigDecimal seckillPrice,

        @Schema(description = "活动总量", example = "10")
        @NotNull(message = "活动总量不能为空")
        @Min(value = 1, message = "活动总量必须大于 0")
        Integer totalStock,

        @Schema(description = "每人限购数量", example = "1")
        @Min(value = 1, message = "限购数量必须大于 0")
        Integer perUserLimit,

        @Schema(description = "开始时间", example = "2026-10-03T10:00:00")
        @NotNull(message = "开始时间不能为空")
        LocalDateTime startTime,

        @Schema(description = "结束时间", example = "2026-10-03T12:00:00")
        @NotNull(message = "结束时间不能为空")
        LocalDateTime endTime,

        @Schema(description = "待支付超时分钟数，不传默认 15", example = "15")
        @Min(value = 1, message = "超时分钟数必须大于 0")
        Integer payTimeoutMinutes
) {
}
