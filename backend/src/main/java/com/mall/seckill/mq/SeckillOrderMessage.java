package com.mall.seckill.mq;

/**
 * 创建订单消息，字段是下单时刻的快照，消费时不回查活动与商品
 */
public record SeckillOrderMessage(
        String orderNo,
        Long userId,
        Long activityId,
        Long productId,
        String productName,
        String seckillPrice,
        Integer payTimeoutMinutes
) {
}
