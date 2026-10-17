package com.mall.seckill.mq;

/**
 * 超时关单消息，以延迟消息投递，到期后检查订单是否仍待支付
 */
public record SeckillTimeoutMessage(
        String orderNo,
        Long userId,
        Long activityId
) {
}
