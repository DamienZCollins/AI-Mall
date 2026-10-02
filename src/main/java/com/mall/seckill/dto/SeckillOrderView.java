package com.mall.seckill.dto;

import com.mall.seckill.entity.SeckillOrder;
import com.mall.seckill.entity.SeckillOrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀订单信息
 */
@Schema(description = "秒杀订单信息")
public record SeckillOrderView(

        @Schema(description = "订单号")
        String orderNo,

        @Schema(description = "活动 ID")
        Long activityId,

        @Schema(description = "商品 ID")
        Long productId,

        @Schema(description = "商品名称")
        String productName,

        @Schema(description = "成交价")
        BigDecimal seckillPrice,

        @Schema(description = "订单状态：1 待支付，2 已支付，3 已取消，4 超时取消")
        SeckillOrderStatus status,

        @Schema(description = "支付截止时间")
        LocalDateTime expireTime,

        @Schema(description = "支付时间")
        LocalDateTime payTime
) {

    public static SeckillOrderView from(SeckillOrder order) {
        return new SeckillOrderView(order.getOrderNo(), order.getActivityId(), order.getProductId(),
                order.getProductName(), order.getSeckillPrice(),
                SeckillOrderStatus.fromCode(order.getStatus()), order.getExpireTime(), order.getPayTime());
    }
}
