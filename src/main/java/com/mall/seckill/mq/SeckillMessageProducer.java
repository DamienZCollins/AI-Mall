package com.mall.seckill.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.common.exception.BusinessException;
import com.mall.common.result.ResultCode;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.message.MessageBuilder;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 秒杀消息生产者
 *
 * <p>普通消息与延迟消息分属两个主题，各自的 Producer 按主题缓存
 *
 * <p>生产者懒创建：代理不可用时构造会失败，放在启动阶段会拖垮应用；
 * 改到首次发送时创建，失败只让当前请求返回可重试的错误
 */
@Component
public class SeckillMessageProducer {

    private static final Logger log = LoggerFactory.getLogger(SeckillMessageProducer.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ClientServiceProvider provider;
    private final RocketMqProperties properties;
    private final Map<String, Producer> producers = new HashMap<>();

    /** 保护 producers：构造生产者含网络 I/O，用 ReentrantLock 避免虚拟线程被钉在载体线程上 */
    private final Lock producersLock = new ReentrantLock();

    public SeckillMessageProducer(ClientServiceProvider provider, RocketMqProperties properties) {
        this.provider = provider;
        this.properties = properties;
    }

    /**
     * 投递创建订单消息
     */
    public void sendOrderCreate(SeckillOrderMessage message) {
        send(properties.getOrderTopic(), message, 0L);
    }

    /**
     * 投递延迟关单消息，delayMillis 为支付超时时长
     */
    public void sendOrderTimeout(SeckillTimeoutMessage message, long delayMillis) {
        send(properties.getTimeoutTopic(), message, delayMillis);
    }

    private void send(String topic, Object payload, long delayMillis) {
        Producer producer = producerOf(topic);
        MessageBuilder builder = provider.newMessageBuilder()
                .setTopic(topic)
                .setBody(toJson(payload).getBytes(StandardCharsets.UTF_8));
        if (delayMillis > 0) {
            builder.setDeliveryTimestamp(System.currentTimeMillis() + delayMillis);
        }

        try {
            producer.send(builder.build());
        } catch (Exception e) {
            log.error("消息投递失败，topic={}", topic, e);
            // 让调用方回补 Redis 并提示用户重试
            throw new BusinessException(ResultCode.SECKILL_MQ_UNAVAILABLE);
        }
    }

    private Producer producerOf(String topic) {
        producersLock.lock();
        try {
            Producer producer = producers.get(topic);
            if (producer != null) {
                return producer;
            }
            try {
                producer = provider.newProducerBuilder()
                        .setClientConfiguration(RocketMqConfig.clientConfiguration(properties))
                        .setTopics(topic)
                        .build();
            } catch (Exception e) {
                log.error("RocketMQ 生产者创建失败，topic={}", topic, e);
                throw new BusinessException(ResultCode.SECKILL_MQ_UNAVAILABLE);
            }
            producers.put(topic, producer);
            return producer;
        } finally {
            producersLock.unlock();
        }
    }

    private String toJson(Object payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化秒杀消息失败", e);
        }
    }
}
