package com.mall.seckill.mq;

import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * RocketMQ gRPC 客户端配置
 *
 * <p>生产者不在这里创建，代理不可用时创建会失败并拖垮启动；
 * 改由 {@code SeckillMessageProducer} 在首次发送时懒创建
 */
@Configuration
@EnableConfigurationProperties(RocketMqProperties.class)
public class RocketMqConfig {

    @Bean
    public ClientServiceProvider rocketMqClientServiceProvider() {
        return ClientServiceProvider.loadService();
    }

    public static ClientConfiguration clientConfiguration(RocketMqProperties properties) {
        return ClientConfiguration.newBuilder()
                .setEndpoints(properties.getEndpoints())
                .setRequestTimeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                .build();
    }
}
