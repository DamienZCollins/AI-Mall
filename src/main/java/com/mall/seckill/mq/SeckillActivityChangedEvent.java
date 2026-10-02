package com.mall.seckill.mq;

/**
 * 秒杀活动变更事件，创建或编辑后触发库存预热
 */
public record SeckillActivityChangedEvent(Long activityId) {
}
