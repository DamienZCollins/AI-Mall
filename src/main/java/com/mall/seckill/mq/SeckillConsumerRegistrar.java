package com.mall.seckill.mq;

import com.mall.config.rocketmq.RocketMqProperties;
import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 秒杀消费者注册：代理不可用时只记录告警，不阻止应用启动
 */
@Component
public class SeckillConsumerRegistrar implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SeckillConsumerRegistrar.class);

    private final List<SeckillMessageConsumer> consumers;
    private final RocketMqProperties properties;
    private final List<PushConsumer> started = new CopyOnWriteArrayList<>();

    private volatile boolean running;

    public SeckillConsumerRegistrar(List<SeckillMessageConsumer> consumers, RocketMqProperties properties) {
        this.consumers = consumers;
        this.properties = properties;
    }

    @Override
    public void start() {
        for (SeckillMessageConsumer consumer : consumers) {
            try {
                started.add(consumer.start());
                log.info("{} 已启动，接入点 {}，订单主题 {}，关单主题 {}",
                        consumer.getClass().getSimpleName(), properties.getEndpoints(),
                        properties.getOrderTopic(), properties.getTimeoutTopic());
            } catch (Exception e) {
                // 消息中间件不可用不应阻止应用启动，抢购接口届时会返回可重试的错误
                log.warn("{} 启动失败，抢购将不可用：{}", consumer.getClass().getSimpleName(), rootCause(e));
            }
        }
        running = true;
    }

    @Override
    public void stop() {
        for (PushConsumer consumer : started) {
            try {
                consumer.close();
            } catch (Exception e) {
                log.warn("关闭消费者失败：{}", e.getMessage());
            }
        }
        started.clear();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * 启动失败常见于代理不可达或主题未创建，取出最内层原因便于定位
     */
    private String rootCause(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
