package com.mall.seckill.service;

import com.mall.seckill.mq.SeckillActivityChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 活动创建或编辑后重新预热库存
 */
@Component
public class SeckillActivityChangedListener {

    private static final Logger log = LoggerFactory.getLogger(SeckillActivityChangedListener.class);

    private final SeckillRedisService redisService;

    public SeckillActivityChangedListener(SeckillRedisService redisService) {
        this.redisService = redisService;
    }

    /**
     * 事务提交后预热，避免读到未提交的库存
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onActivityChanged(SeckillActivityChangedEvent event) {
        try {
            redisService.preheat(event.activityId());
        } catch (Exception e) {
            log.warn("活动 {} 变更后预热失败：{}", event.activityId(), e.getMessage());
        }
    }
}
