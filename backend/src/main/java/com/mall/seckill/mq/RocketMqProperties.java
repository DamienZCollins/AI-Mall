package com.mall.seckill.mq;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RocketMQ 配置项，通过 gRPC 代理接入
 */
@ConfigurationProperties(prefix = "mall.rocketmq")
public class RocketMqProperties {

    /** 代理接入点，例如 localhost:8081 */
    private String endpoints;

    /** 订单创建消息主题，普通消息 */
    private String orderTopic;

    /** 超时关单消息主题，延迟消息 */
    private String timeoutTopic;

    private String orderConsumerGroup;

    private String timeoutConsumerGroup;

    /** 请求超时，单位秒 */
    private int requestTimeoutSeconds = 10;

    public String getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(String endpoints) {
        this.endpoints = endpoints;
    }

    public String getOrderTopic() {
        return orderTopic;
    }

    public void setOrderTopic(String orderTopic) {
        this.orderTopic = orderTopic;
    }

    public String getTimeoutTopic() {
        return timeoutTopic;
    }

    public void setTimeoutTopic(String timeoutTopic) {
        this.timeoutTopic = timeoutTopic;
    }

    public String getOrderConsumerGroup() {
        return orderConsumerGroup;
    }

    public void setOrderConsumerGroup(String orderConsumerGroup) {
        this.orderConsumerGroup = orderConsumerGroup;
    }

    public String getTimeoutConsumerGroup() {
        return timeoutConsumerGroup;
    }

    public void setTimeoutConsumerGroup(String timeoutConsumerGroup) {
        this.timeoutConsumerGroup = timeoutConsumerGroup;
    }

    public int getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }
}
