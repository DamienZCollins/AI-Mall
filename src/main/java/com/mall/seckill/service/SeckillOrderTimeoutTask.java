package com.mall.seckill.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 待支付订单超时取消
 *
 * <p>阶段 5 会改用 RocketMQ 延迟消息做精确触发，这个定时任务作为兜底保留
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
