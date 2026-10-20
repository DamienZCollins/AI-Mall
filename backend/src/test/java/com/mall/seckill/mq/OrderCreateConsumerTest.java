package com.mall.seckill.mq;

import com.mall.seckill.service.SeckillActivityService;
import com.mall.seckill.service.SeckillOrderService;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCreateConsumerTest {

    @Mock
    private SeckillOrderService orderService;

    @Mock
    private SeckillActivityService activityService;

    @Mock
    private SeckillMessageProducer messageProducer;

    private OrderCreateConsumer consumer() {
        RocketMqProperties properties = new RocketMqProperties();
        properties.setOrderTopic("order-topic");
        return new OrderCreateConsumer(null, properties, orderService, activityService, messageProducer);
    }

    @Test
    @DisplayName("正常消息创建订单后按活动配置投递延迟关单消息")
    void consumesAndSchedulesTimeout() {
        when(orderService.createFromMessage(any())).thenReturn(order("SK-1"));
        when(activityService.requireActivity(1L)).thenReturn(activity(15));

        ConsumeResult result = consumer().consume(messageBody("SK-1"));

        assertThat(result).isEqualTo(ConsumeResult.SUCCESS);
        verify(messageProducer).sendOrderTimeout(any(SeckillTimeoutMessage.class), eq(15 * 60_000L));
    }

    @Test
    @DisplayName("唯一键冲突视为重复消息，返回成功不再重投")
    void duplicateKeyIsAcknowledged() {
        when(orderService.createFromMessage(any())).thenThrow(new DuplicateKeyException("uk_user_activity"));

        assertThat(consumer().consume(messageBody("SK-1"))).isEqualTo(ConsumeResult.SUCCESS);
    }

    @Test
    @DisplayName("其它异常返回失败，交给 MQ 重投")
    void otherFailuresAreRetried() {
        when(orderService.createFromMessage(any())).thenThrow(new IllegalStateException("数据库不可用"));

        assertThat(consumer().consume(messageBody("SK-1"))).isEqualTo(ConsumeResult.FAILURE);
    }

    @Test
    @DisplayName("非法 JSON 直接丢弃，避免阻塞消费")
    void malformedJsonIsDiscarded() {
        assertThat(consumer().consume("{not-json")).isEqualTo(ConsumeResult.SUCCESS);
        verify(orderService, never()).createFromMessage(any());
    }

    @Test
    @DisplayName("延迟关单消息投递失败不影响订单消费结果")
    void timeoutSendFailureDoesNotFailConsumption() {
        when(orderService.createFromMessage(any())).thenReturn(order("SK-1"));
        when(activityService.requireActivity(1L)).thenReturn(activity(15));
        doThrow(new IllegalStateException("代理不可用")).when(messageProducer)
                .sendOrderTimeout(any(SeckillTimeoutMessage.class), anyLong());

        assertThat(consumer().consume(messageBody("SK-1"))).isEqualTo(ConsumeResult.SUCCESS);
    }

    private String messageBody(String orderNo) {
        return "{\"orderNo\":\"" + orderNo + "\",\"userId\":9,\"activityId\":1,\"productId\":20,"
                + "\"productName\":\"跑步鞋\",\"seckillPrice\":\"199.00\",\"payTimeoutMinutes\":15}";
    }

    private com.mall.seckill.entity.SeckillOrder order(String orderNo) {
        com.mall.seckill.entity.SeckillOrder order = new com.mall.seckill.entity.SeckillOrder();
        order.setOrderNo(orderNo);
        order.setUserId(9L);
        order.setActivityId(1L);
        return order;
    }

    private com.mall.seckill.entity.SeckillActivity activity(int payTimeoutMinutes) {
        com.mall.seckill.entity.SeckillActivity activity = new com.mall.seckill.entity.SeckillActivity();
        activity.setId(1L);
        activity.setPayTimeoutMinutes(payTimeoutMinutes);
        return activity;
    }
}
