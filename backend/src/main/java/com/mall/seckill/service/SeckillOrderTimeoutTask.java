package com.mall.seckill.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 待支付订单超时取消
 *
 * <p>精确触发由订单创建后投递的 RocketMQ 延迟消息完成，本定时任务按 60 秒周期补偿：
 * 延迟消息投递失败、消费者重启丢消息、订单创建时消息未被投递等场景下仍能关单并回补库存
 */
@Component
public class SeckillOrderTimeoutTask {

    private static final long FIXED_DELAY_MS = 60_000L;

    private final SeckillOrderService orderService;

    public SeckillOrderTimeoutTask(SeckillOrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(fixedDelay = FIXED_DELAY_MS, initialDelay = 30_000L)
    public void cancelExpiredOrders() {
        orderService.cancelExpiredOrders();
    }
}
