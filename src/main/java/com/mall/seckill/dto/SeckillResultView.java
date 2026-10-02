package com.mall.seckill.dto;

import com.mall.seckill.entity.SeckillOrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 抢购结果，供前端轮询
 */
@Schema(description = "抢购结果")
public record SeckillResultView(

        @Schema(description = "结果：QUEUING 排队中，SUCCESS 抢购成功，FAILED 未抢到")
        String state,

        @Schema(description = "订单号，仅成功时有值")
        String orderNo,

        @Schema(description = "商品名称，仅成功时有值")
        String productName,

        @Schema(description = "成交价，仅成功时有值")
        BigDecimal seckillPrice,

        @Schema(description = "订单状态，仅成功时有值")
        SeckillOrderStatus status,

        @Schema(description = "支付截止时间，仅成功时有值")
        LocalDateTime expireTime
) {

    public static SeckillResultView queuing() {
        return new SeckillResultView("QUEUING", null, null, null, null, null);
    }

    public static SeckillResultView failed() {
        return new SeckillResultView("FAILED", null, null, null, null, null);
    }

    public static SeckillResultView success(SeckillOrderView order) {
        return new SeckillResultView("SUCCESS", order.orderNo(), order.productName(),
                order.seckillPrice(), order.status(), order.expireTime());
    }
}
