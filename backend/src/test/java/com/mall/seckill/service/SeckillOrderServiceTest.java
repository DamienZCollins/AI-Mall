package com.mall.seckill.service;

import com.mall.common.exception.BusinessException;
import com.mall.common.result.ResultCode;
import com.mall.seckill.dto.SeckillOrderView;
import com.mall.seckill.entity.SeckillActivity;
import com.mall.seckill.entity.SeckillOrder;
import com.mall.seckill.entity.SeckillOrderStatus;
import com.mall.seckill.entity.SeckillStock;
import com.mall.seckill.mapper.SeckillOrderMapper;
import com.mall.seckill.mapper.SeckillStockMapper;
import com.mall.seckill.mq.SeckillOrderMessage;
import com.mall.support.MybatisPlusTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillOrderServiceTest {

    @BeforeAll
    static void registerEntity() {
        MybatisPlusTestSupport.registerEntity(SeckillOrder.class);
    }

    @Mock
    private SeckillOrderMapper orderMapper;

    @Mock
    private SeckillStockMapper stockMapper;

    @Mock
    private SeckillActivityService activityService;

    @Mock
    private SeckillRedisService redisService;

    private SeckillOrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new SeckillOrderService(orderMapper, stockMapper, activityService, redisService);
        // restoreRedisAfterCommit 依赖事务同步器，单元测试手动打开
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    @DisplayName("创建订单写入待支付状态，并按超时分钟数计算截止时间")
    void createFromMessagePersistsPendingOrder() {
        when(stockMapper.deduct(1L)).thenReturn(1);

        SeckillOrder order = orderService.createFromMessage(message());

        assertThat(order.getStatus()).isEqualTo(SeckillOrderStatus.PENDING_PAYMENT.getCode());
        assertThat(order.getOrderNo()).isEqualTo("SK-order-1");
        assertThat(order.getSeckillPrice()).isEqualByComparingTo("199.00");
        assertThat(order.getExpireTime()).isAfter(LocalDateTime.now().plusMinutes(14));
        verify(orderMapper).insert(any(SeckillOrder.class));
    }

    @Test
    @DisplayName("数据库库存扣减失败时抛错，让消息重投而不是吞掉")
    void createFromMessageFailsWhenStockExhausted() {
        when(stockMapper.deduct(1L)).thenReturn(0);

        assertThatThrownBy(() -> orderService.createFromMessage(message()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.SECKILL_SOLD_OUT.getCode());
    }

    @Test
    @DisplayName("支付只允许待支付订单，条件更新成功才算完成")
    void payTransitionsPendingOrder() {
        when(orderMapper.selectOne(any())).thenReturn(order(SeckillOrderStatus.PENDING_PAYMENT));
        when(orderMapper.transferStatus(eq("SK-order-1"), eq(1), eq(2), any())).thenReturn(1);

        SeckillOrderView view = orderService.pay("SK-order-1");

        assertThat(view.orderNo()).isEqualTo("SK-order-1");
        verify(orderMapper).transferStatus(eq("SK-order-1"), eq(1), eq(2), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("已支付或已取消订单不能再次支付")
    void payRejectsNonPendingOrder() {
        when(orderMapper.selectOne(any())).thenReturn(order(SeckillOrderStatus.PAID));

        assertThatThrownBy(() -> orderService.pay("SK-order-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.SECKILL_ORDER_STATUS_ILLEGAL.getCode());

        verify(orderMapper, never()).transferStatus(anyString(), anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("条件更新落空时读取加锁后的最新状态，状态已变化则拒绝支付")
    void payRejectsWhenStatusChangedConcurrently() {
        when(orderMapper.selectOne(any())).thenReturn(order(SeckillOrderStatus.PENDING_PAYMENT));
        when(orderMapper.transferStatus(anyString(), anyInt(), anyInt(), any())).thenReturn(0);

        assertThatThrownBy(() -> orderService.pay("SK-order-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.SECKILL_ORDER_STATUS_ILLEGAL.getCode());
    }

    @Test
    @DisplayName("取消订单按订单号与用户查询，非本人订单一律返回订单不存在")
    void cancelHidesForeignOrder() {
        when(orderMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> orderService.cancel("SK-order-1", 99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.SECKILL_ORDER_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("取消成功后回补数据库库存，并在事务提交后回补 Redis 库存")
    void cancelRestoresStockAfterCommit() {
        when(orderMapper.selectOne(any())).thenReturn(order(SeckillOrderStatus.PENDING_PAYMENT));
        when(orderMapper.transferStatus(anyString(), anyInt(), anyInt(), any())).thenReturn(1);
        when(activityService.requireActivity(1L)).thenReturn(activity(10));
        when(stockMapper.restore(1L, 10)).thenReturn(1);
        when(redisService.restoreStockOnly(1L)).thenReturn(true);

        orderService.cancel("SK-order-1", 9L);

        verify(stockMapper).restore(1L, 10);
        // 提交前不触碰 Redis
        verify(redisService, never()).restoreStockOnly(anyLong());
        triggerAfterCommit();
        verify(redisService).restoreStockOnly(1L);
    }

    @Test
    @DisplayName("已支付订单不能取消")
    void cancelRejectsPaidOrder() {
        when(orderMapper.selectOne(any())).thenReturn(order(SeckillOrderStatus.PAID));

        assertThatThrownBy(() -> orderService.cancel("SK-order-1", 9L))
                .isInstanceOf(BusinessException.class);

        verify(stockMapper, never()).restore(anyLong(), anyInt());
    }

    @Test
    @DisplayName("超时关单只处理待支付订单，已支付订单跳过且不回补")
    void cancelByTimeoutSkipsPaidOrder() {
        when(orderMapper.selectOne(any())).thenReturn(order(SeckillOrderStatus.PAID));

        orderService.cancelByTimeout("SK-order-1");

        verify(orderMapper, never()).transferStatus(anyString(), anyInt(), anyInt(), any());
        verify(stockMapper, never()).restore(anyLong(), anyInt());
    }

    @Test
    @DisplayName("超时关单条件更新失败时不重复回补，重复消息安全")
    void cancelByTimeoutIsIdempotent() {
        when(orderMapper.selectOne(any())).thenReturn(order(SeckillOrderStatus.PENDING_PAYMENT));
        when(orderMapper.transferStatus(anyString(), anyInt(), anyInt(), any())).thenReturn(0);

        orderService.cancelByTimeout("SK-order-1");

        verify(stockMapper, never()).restore(anyLong(), anyInt());
        verify(redisService, never()).restoreStockOnly(anyLong());
    }

    @Test
    @DisplayName("超时关单成功后只回补库存，不移除买家标记")
    void cancelByTimeoutRestoresStockOnly() {
        when(orderMapper.selectOne(any())).thenReturn(order(SeckillOrderStatus.PENDING_PAYMENT));
        when(orderMapper.transferStatus(anyString(), anyInt(), anyInt(), any())).thenReturn(1);
        when(activityService.requireActivity(1L)).thenReturn(activity(10));
        when(stockMapper.restore(1L, 10)).thenReturn(1);
        when(redisService.restoreStockOnly(1L)).thenReturn(true);

        orderService.cancelByTimeout("SK-order-1");

        verify(stockMapper).restore(1L, 10);
        triggerAfterCommit();
        verify(redisService).restoreStockOnly(1L);
        verify(redisService, never()).restore(anyLong(), anyLong());
    }

    @Test
    @DisplayName("订单不存在时超时关单直接返回")
    void cancelByTimeoutIgnoresMissingOrder() {
        when(orderMapper.selectOne(any())).thenReturn(null);

        orderService.cancelByTimeout("missing");

        verify(orderMapper, never()).transferStatus(anyString(), anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("超时扫描只对流转成功的订单回补库存")
    void cancelExpiredOrdersRestoresOnlyTransferred() {
        SeckillOrder first = order(SeckillOrderStatus.PENDING_PAYMENT);
        SeckillOrder second = order(SeckillOrderStatus.PENDING_PAYMENT);
        second.setOrderNo("SK-order-2");
        when(orderMapper.selectList(any())).thenReturn(java.util.List.of(first, second));
        when(orderMapper.transferStatus(eq("SK-order-1"), anyInt(), anyInt(), any())).thenReturn(1);
        when(orderMapper.transferStatus(eq("SK-order-2"), anyInt(), anyInt(), any())).thenReturn(0);
        when(activityService.requireActivity(1L)).thenReturn(activity(10));
        when(stockMapper.restore(1L, 10)).thenReturn(1);

        int cancelled = orderService.cancelExpiredOrders();

        assertThat(cancelled).isEqualTo(1);
        verify(stockMapper).restore(1L, 10);
    }

    private void triggerAfterCommit() {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
    }

    private SeckillOrderMessage message() {
        return new SeckillOrderMessage("SK-order-1", 9L, 1L, 20L, "网面透气跑步鞋", "199.00", 15);
    }

    private SeckillOrder order(SeckillOrderStatus status) {
        SeckillOrder order = new SeckillOrder();
        order.setOrderNo("SK-order-1");
        order.setUserId(9L);
        order.setActivityId(1L);
        order.setProductId(20L);
        order.setProductName("网面透气跑步鞋");
        order.setSeckillPrice(new BigDecimal("199.00"));
        order.setStatus(status.getCode());
        order.setExpireTime(LocalDateTime.now().plusMinutes(15));
        return order;
    }

    private SeckillActivity activity(int totalStock) {
        SeckillActivity activity = new SeckillActivity();
        activity.setId(1L);
        activity.setTotalStock(totalStock);
        return activity;
    }
}
