package com.mall.seckill.mq;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.config.rocketmq.RocketMqConfig;
import com.mall.config.rocketmq.RocketMqProperties;
import com.mall.seckill.entity.SeckillActivity;
import com.mall.seckill.entity.SeckillOrder;
import com.mall.seckill.service.SeckillActivityService;
import com.mall.seckill.service.SeckillOrderService;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.consumer.FilterExpression;
import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 订单创建消费者：把抢到的资格异步落成订单，并投递延迟关单消息
 */
@Component
public class OrderCreateConsumer implements SeckillMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderCreateConsumer.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ClientServiceProvider provider;
    private final RocketMqProperties properties;
    private final SeckillOrderService orderService;
    private final SeckillActivityService activityService;
    private final SeckillMessageProducer messageProducer;

    public OrderCreateConsumer(ClientServiceProvider provider, RocketMqProperties properties,
                               SeckillOrderService orderService, SeckillActivityService activityService,
                               SeckillMessageProducer messageProducer) {
        this.provider = provider;
        this.properties = properties;
        this.orderService = orderService;
        this.activityService = activityService;
        this.messageProducer = messageProducer;
    }

    @Override
    public PushConsumer start() throws Exception {
        return provider.newPushConsumerBuilder()
                .setClientConfiguration(RocketMqConfig.clientConfiguration(properties))
                .setConsumerGroup(properties.getOrderConsumerGroup())
                .setSubscriptionExpressions(Map.of(properties.getOrderTopic(), FilterExpression.SUB_ALL))
                .setMessageListener(message -> consume(
                        StandardCharsets.UTF_8.decode(message.getBody()).toString()))
                .build();
    }

    /**
     * 订单已存在或唯一索引冲突都视为处理成功，避免重复投递时反复重试
     */
    @Override
    public ConsumeResult consume(String body) {
        SeckillOrderMessage message = parse(body);
        if (message == null) {
            return ConsumeResult.SUCCESS;
        }

        try {
            SeckillOrder order = orderService.createFromMessage(message);
            sendTimeoutMessage(order);
            return ConsumeResult.SUCCESS;
        } catch (Exception e) {
            if (isDuplicate(message)) {
                log.info("订单已存在，幂等返回 orderNo={}", message.orderNo());
                return ConsumeResult.SUCCESS;
            }
            log.error("创建订单失败 orderNo={}", message.orderNo(), e);
            return ConsumeResult.FAILURE;
        }
    }

    /**
     * 延迟消息投递失败不重试本消息，订单已经落库，兜底定时任务会处理超时
     */
    private void sendTimeoutMessage(SeckillOrder order) {
        try {
            SeckillActivity activity = activityService.requireActivity(order.getActivityId());
            SeckillTimeoutMessage timeout = new SeckillTimeoutMessage(
                    order.getOrderNo(), order.getUserId(), order.getActivityId());
            messageProducer.sendOrderTimeout(timeout, activity.getPayTimeoutMinutes() * 60_000L);
            log.info("已投递延迟关单消息 orderNo={} 延迟 {} 分钟",
                    order.getOrderNo(), activity.getPayTimeoutMinutes());
        } catch (Exception e) {
            log.error("投递延迟关单消息失败，改由定时任务兜底 orderNo={}", order.getOrderNo(), e);
        }
    }

    private boolean isDuplicate(SeckillOrderMessage message) {
        Long count = orderService.countByUserAndActivity(message.userId(), message.activityId());
        return count > 0;
    }

    private SeckillOrderMessage parse(String body) {
        try {
            return OBJECT_MAPPER.readValue(body, SeckillOrderMessage.class);
        } catch (JsonProcessingException e) {
            log.error("消息反序列化失败，丢弃：{}", body, e);
            return null;
        }
    }
}
