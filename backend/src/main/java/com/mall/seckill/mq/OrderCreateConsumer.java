package com.mall.seckill.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Map;

/**
 * 订单创建消费者：把抢到的资格异步落成订单，并投递延迟关单消息
 */
@Component
public class OrderCreateConsumer implements SeckillMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderCreateConsumer.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** MySQL 唯一键冲突错误码 */
    private static final int MYSQL_DUPLICATE_ENTRY = 1062;

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
     * 唯一键冲突视为重复消息，其它异常交给 MQ 重投
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
            if (isDuplicateKey(e)) {
                log.info("订单已存在，幂等返回 orderNo={} activityId={}",
                        message.orderNo(), message.activityId());
                return ConsumeResult.SUCCESS;
            }
            log.error("创建订单失败，消息将重投 orderNo={} activityId={}",
                    message.orderNo(), message.activityId(), e);
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
            log.error("延迟关单消息投递失败，该订单将由超时定时任务兜底 orderNo={} activityId={}",
                    order.getOrderNo(), order.getActivityId(), e);
        }
    }

    /**
     * 只有唯一键冲突（uk_user_activity / uk_order_no）才是重复消息
     *
     * <p>数据库库存扣减失败等异常不是重复，必须按失败重投，否则预扣的库存永远不会落库
     */
    private boolean isDuplicateKey(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof DuplicateKeyException) {
                return true;
            }
            // 未经 Spring 异常转换时按 MySQL 唯一键冲突错误码识别，避免把其它完整性约束违例当成重复
            if (cause instanceof SQLIntegrityConstraintViolationException sql
                    && sql.getErrorCode() == MYSQL_DUPLICATE_ENTRY) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
            cause = cause.getCause();
        }
        return false;
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
