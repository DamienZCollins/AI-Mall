package com.mall.seckill.service;

import com.mall.common.exception.BusinessException;
import com.mall.common.result.ResultCode;
import com.mall.product.dto.ProductView;
import com.mall.product.service.ProductService;
import com.mall.seckill.dto.SeckillOrderView;
import com.mall.seckill.dto.SeckillResultView;
import com.mall.seckill.entity.SeckillActivity;
import com.mall.seckill.entity.SeckillOrderStatus;
import com.mall.seckill.mq.SeckillMessageProducer;
import com.mall.seckill.mq.SeckillOrderMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillServiceTest {

    @Mock
    private SeckillActivityService activityService;

    @Mock
    private SeckillRedisService redisService;

    @Mock
    private SeckillOrderService orderService;

    @Mock
    private SeckillMessageProducer messageProducer;

    @Mock
    private ProductService productService;

    @InjectMocks
    private SeckillService seckillService;

    @Test
    @DisplayName("预扣成功且消息投递成功后不再回补")
    void deductThenSendMessage() {
        givenPreheated();
        when(redisService.deduct(1L, 9L)).thenReturn(SeckillDeductResult.SUCCESS);
        givenProduct();
        when(activityService.requireActivity(1L)).thenReturn(activity());

        seckillService.placeOrder(1L, 9L);

        ArgumentCaptor<SeckillOrderMessage> captor = ArgumentCaptor.forClass(SeckillOrderMessage.class);
        verify(messageProducer).sendOrderCreate(captor.capture());
        SeckillOrderMessage message = captor.getValue();
        assertThat(message.orderNo()).startsWith("SK");
        assertThat(message.userId()).isEqualTo(9L);
        assertThat(message.activityId()).isEqualTo(1L);
        assertThat(message.productId()).isEqualTo(20L);
        assertThat(message.productName()).isEqualTo("网面透气跑步鞋");
        assertThat(message.seckillPrice()).isEqualTo("199.00");
        assertThat(message.payTimeoutMinutes()).isEqualTo(15);
        verify(redisService, never()).restore(anyLong(), anyLong());
    }

    @Test
    @DisplayName("消息投递失败时回补预扣，并把异常抛给调用方")
    void restoresDeductWhenSendFails() {
        givenPreheated();
        when(redisService.deduct(1L, 9L)).thenReturn(SeckillDeductResult.SUCCESS);
        givenProduct();
        when(activityService.requireActivity(1L)).thenReturn(activity());
        doThrow(new BusinessException(ResultCode.SECKILL_MQ_UNAVAILABLE))
                .when(messageProducer).sendOrderCreate(any());

        assertThatThrownBy(() -> seckillService.placeOrder(1L, 9L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.SECKILL_MQ_UNAVAILABLE.getCode());

        verify(redisService).restore(1L, 9L);
    }

    @Test
    @DisplayName("未预热时先按需预热再预扣")
    void preheatsOnDemand() {
        when(redisService.isPreheated(1L)).thenReturn(false);
        when(redisService.deduct(1L, 9L)).thenReturn(SeckillDeductResult.SOLD_OUT);

        assertThatThrownBy(() -> seckillService.placeOrder(1L, 9L))
                .isInstanceOf(BusinessException.class);

        verify(redisService).preheat(1L);
        verify(messageProducer, never()).sendOrderCreate(any());
    }

    @Test
    @DisplayName("预扣失败结果映射为对应业务错误码")
    void mapsDeductResultToErrorCode() {
        givenPreheated();
        when(redisService.deduct(1L, 9L)).thenReturn(SeckillDeductResult.DUPLICATE);

        assertThatThrownBy(() -> seckillService.placeOrder(1L, 9L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.SECKILL_DUPLICATE_ORDER.getCode());

        verify(messageProducer, never()).sendOrderCreate(any());
        verify(redisService, never()).restore(anyLong(), anyLong());
    }

    @Test
    @DisplayName("未登录时直接拒绝，不触碰 Redis 与消息")
    void rejectsAnonymousUser() {
        assertThatThrownBy(() -> seckillService.placeOrder(1L, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.UNAUTHORIZED.getCode());

        verifyNoInteractions(redisService, messageProducer);
    }

    @Test
    @DisplayName("结果查询：有订单返回成功")
    void resultReturnsOrderWhenCreated() {
        when(activityService.requireActivity(1L)).thenReturn(activity());
        when(orderService.findByUserAndActivity(9L, 1L)).thenReturn(orderView());

        SeckillResultView result = seckillService.getResult(1L, 9L);

        assertThat(result.state()).isEqualTo("SUCCESS");
        assertThat(result.orderNo()).isEqualTo("SK1-9-1");
    }

    @Test
    @DisplayName("结果查询：无订单但占用库存返回排队中")
    void resultReturnsQueuingWhileBuyer() {
        when(activityService.requireActivity(1L)).thenReturn(activity());
        when(orderService.findByUserAndActivity(9L, 1L)).thenReturn(null);
        when(redisService.isBuyer(1L, 9L)).thenReturn(true);

        assertThat(seckillService.getResult(1L, 9L).state()).isEqualTo("QUEUING");
    }

    @Test
    @DisplayName("结果查询：无订单且未占用库存返回未抢到")
    void resultReturnsFailedWhenNotBuyer() {
        when(activityService.requireActivity(1L)).thenReturn(activity());
        when(orderService.findByUserAndActivity(9L, 1L)).thenReturn(null);
        when(redisService.isBuyer(1L, 9L)).thenReturn(false);

        assertThat(seckillService.getResult(1L, 9L).state()).isEqualTo("FAILED");
    }

    private void givenPreheated() {
        when(redisService.isPreheated(1L)).thenReturn(true);
    }

    private SeckillActivity activity() {
        SeckillActivity activity = new SeckillActivity();
        activity.setId(1L);
        activity.setProductId(20L);
        activity.setTitle("限时秒杀");
        activity.setSeckillPrice(new BigDecimal("199.00"));
        activity.setTotalStock(10);
        activity.setStartTime(LocalDateTime.now().minusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));
        activity.setPayTimeoutMinutes(15);
        return activity;
    }

    private void givenProduct() {
        when(productService.getById(20L)).thenReturn(new ProductView(20L, "网面透气跑步鞋", "沃兰", 1004L,
                null, null, new BigDecimal("399.00"), 100, 1, null));
    }

    private SeckillOrderView orderView() {
        return new SeckillOrderView("SK1-9-1", 1L, 20L, "网面透气跑步鞋", new BigDecimal("199.00"),
                SeckillOrderStatus.PENDING_PAYMENT, LocalDateTime.now().plusMinutes(15), null);
    }
}
