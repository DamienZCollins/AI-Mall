package com.mall.seckill.service;

import com.mall.common.exception.BusinessException;
import com.mall.common.result.ResultCode;
import com.mall.product.dto.ProductView;
import com.mall.product.service.ProductService;
import com.mall.seckill.dto.SeckillActivityView;
import com.mall.seckill.dto.SeckillOrderView;
import com.mall.seckill.dto.SeckillResultView;
import com.mall.seckill.entity.SeckillActivity;
import com.mall.seckill.mq.SeckillMessageProducer;
import com.mall.seckill.mq.SeckillOrderMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 抢购入口：Redis 原子预扣 + 投递 MQ，数据库只承担最终落库
 */
@Service
public class SeckillService {

    private static final Logger log = LoggerFactory.getLogger(SeckillService.class);

    private final SeckillActivityService activityService;
    private final SeckillRedisService redisService;
    private final SeckillOrderService orderService;
    private final SeckillMessageProducer messageProducer;
    private final ProductService productService;

    public SeckillService(SeckillActivityService activityService, SeckillRedisService redisService,
                          SeckillOrderService orderService, SeckillMessageProducer messageProducer,
                          ProductService productService) {
        this.activityService = activityService;
        this.redisService = redisService;
        this.orderService = orderService;
        this.messageProducer = messageProducer;
        this.productService = productService;
    }

    /**
     * 抢购：先在 Redis 原子预扣，再投递消息，发送失败时回补预扣
     */
    public void placeOrder(Long activityId, Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!redisService.isPreheated(activityId)) {
            // 应用重启或 Redis 淘汰后按需预热，让进行中的活动仍可下单
            redisService.preheat(activityId);
        }

        SeckillDeductResult result = redisService.deduct(activityId, userId);
        if (result != SeckillDeductResult.SUCCESS) {
            throw toException(result);
        }

        try {
            SeckillOrderMessage message = buildMessage(activityService.requireActivity(activityId), userId);
            messageProducer.sendOrderCreate(message);
            log.info("抢购预扣成功并已投递 orderNo={} userId={} activityId={}",
                    message.orderNo(), userId, activityId);
        } catch (RuntimeException e) {
            // 消息没进 MQ，预扣的库存和买家标记必须还原，否则资格会凭空丢失
            redisService.restore(activityId, userId);
            log.error("消息投递失败已回补预扣 activityId={} userId={}", activityId, userId, e);
            throw e;
        }
    }

    /**
     * 轮询抢购结果：先看订单，没有再按买家标记区分排队中与未参与
     */
    public SeckillResultView getResult(Long activityId, Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        activityService.requireActivity(activityId);

        SeckillOrderView order = orderService.findByUserAndActivity(userId, activityId);
        if (order != null) {
            return SeckillResultView.success(order);
        }
        if (redisService.isBuyer(activityId, userId)) {
            return SeckillResultView.queuing();
        }
        return SeckillResultView.failed();
    }

    private SeckillOrderMessage buildMessage(SeckillActivity activity, Long userId) {
        ProductView product = productService.getById(activity.getProductId());
        return new SeckillOrderMessage(
                SeckillOrderNo.generate(userId, activity.getId()),
                userId,
                activity.getId(),
                product.id(),
                product.name(),
                activity.getSeckillPrice().toPlainString(),
                activity.getPayTimeoutMinutes());
    }

    private BusinessException toException(SeckillDeductResult result) {
        return switch (result) {
            case NOT_STARTED -> new BusinessException(ResultCode.SECKILL_NOT_STARTED);
            case ENDED -> new BusinessException(ResultCode.SECKILL_ENDED);
            case SOLD_OUT -> new BusinessException(ResultCode.SECKILL_SOLD_OUT);
            case DUPLICATE -> new BusinessException(ResultCode.SECKILL_DUPLICATE_ORDER);
            case NOT_PREHEATED -> new BusinessException(ResultCode.SECKILL_ACTIVITY_NOT_FOUND);
            default -> new BusinessException(ResultCode.INTERNAL_ERROR);
        };
    }
}
