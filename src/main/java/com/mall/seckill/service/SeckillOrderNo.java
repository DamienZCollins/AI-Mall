package com.mall.seckill.service;

/**
 * 秒杀订单号：SK + 毫秒时间戳 + 用户 ID + 活动 ID
 *
 * <p>同用户同活动同时最多一笔在途订单（Redis 买家去重 + 唯一索引），
 * 时间戳段保证取消后重新下单拿到新单号，拼接段保证不同用户、不同活动不会撞号
 */
final class SeckillOrderNo {

    private SeckillOrderNo() {
    }

    static String generate(Long userId, Long activityId) {
        return "SK" + System.currentTimeMillis() + "-" + userId + "-" + activityId;
    }
}
