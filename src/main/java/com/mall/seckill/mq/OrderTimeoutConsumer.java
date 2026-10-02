package com.mall.seckill.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.config.rocketmq.RocketMqConfig;
import com.mall.config.rocketmq.RocketMqProperties;
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
 * 超时关单消费者：消费延迟消息，到期后检查订单是否仍待支付
 */
@Component
public class OrderTimeoutConsumer implements SeckillMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutConsumer.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ClientServiceProvider provider;
    private final RocketMqProperties properties;
    private final SeckillOrderService orderService;

    public OrderTimeoutConsumer(ClientServiceProvider provider, RocketMqProperties properties,
                                SeckillOrderService orderService) {
        this.provider = provider;
        this.properties = properties;
        this.orderService = orderService;
    }

    @Override
    public PushConsumer start() throws Exception {
        return provider.newPushConsumerBuilder()
                .setClientConfiguration(RocketMqConfig.clientConfiguration(properties))
                .setConsumerGroup(properties.getTimeoutConsumerGroup())
                .setSubscriptionExpressions(Map.of(properties.getTimeoutTopic(), FilterExpression.SUB_ALL))
                .setMessageListener(message -> consume(
                        StandardCharsets.UTF_8.decode(message.getBody()).toString()))
                .build();
    }

    @Override
    public ConsumeResult consume(String body) {
        SeckillTimeoutMessage message = parse(body);
        if (message == null) {
            return ConsumeResult.SUCCESS;
        }
        try {
            orderService.cancelByTimeout(message.orderNo());
            return ConsumeResult.SUCCESS;
        } catch (Exception e) {
            log.error("超时关单失败 orderNo={}", message.orderNo(), e);
            return ConsumeResult.FAILURE;
        }
    }

    private SeckillTimeoutMessage parse(String body) {
        try {
            return OBJECT_MAPPER.readValue(body, SeckillTimeoutMessage.class);
        } catch (JsonProcessingException e) {
            log.error("延迟消息反序列化失败，丢弃：{}", body, e);
            return null;
        }
    }
}
