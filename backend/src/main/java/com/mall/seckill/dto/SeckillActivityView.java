package com.mall.seckill.dto;

import com.mall.seckill.entity.SeckillActivity;
import com.mall.seckill.entity.SeckillStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动信息
 */
@Schema(description = "秒杀活动信息")
public record SeckillActivityView(

        @Schema(description = "活动 ID")
        Long id,

        @Schema(description = "绑定商品 ID")
        Long productId,

        @Schema(description = "活动标题")
        String title,

        @Schema(description = "秒杀价")
        BigDecimal seckillPrice,

        @Schema(description = "商品原价")
        BigDecimal originalPrice,

        @Schema(description = "活动总量")
        Integer totalStock,

        @Schema(description = "剩余库存")
        Integer remainingStock,

        @Schema(description = "每人限购数量，当前实现固定为 1")
        Integer perUserLimit,

        @Schema(description = "开始时间")
        LocalDateTime startTime,

        @Schema(description = "结束时间")
        LocalDateTime endTime,

        @Schema(description = "活动状态：0 未开始，1 进行中，2 已结束，3 已关闭")
        SeckillStatus status
) {

    public static SeckillActivityView from(SeckillActivity activity, Integer remainingStock,
                                           BigDecimal originalPrice, SeckillStatus status) {
        return new SeckillActivityView(activity.getId(), activity.getProductId(), activity.getTitle(),
                activity.getSeckillPrice(), originalPrice, activity.getTotalStock(), remainingStock,
                activity.getPerUserLimit(), activity.getStartTime(), activity.getEndTime(), status);
    }
}
