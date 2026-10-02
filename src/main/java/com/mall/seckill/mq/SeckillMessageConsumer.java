package com.mall.seckill.mq;

import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.consumer.PushConsumer;

/**
 * 秒杀消息消费者，由注册器统一启动
 */
public interface SeckillMessageConsumer {

    PushConsumer start() throws Exception;

    /**
     * 消费一条消息，返回结果决定 MQ 是否重投
     */
    ConsumeResult consume(String body);
}
