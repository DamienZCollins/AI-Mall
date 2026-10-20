package com.mall.seckill.service;

import com.mall.common.exception.BusinessException;
import com.mall.common.result.ResultCode;
import com.mall.product.dto.ProductView;
import com.mall.product.service.ProductService;
import com.mall.seckill.dto.SeckillActivityCreateRequest;
import com.mall.seckill.dto.SeckillActivityUpdateRequest;
import com.mall.seckill.dto.SeckillActivityView;
import com.mall.seckill.entity.SeckillActivity;
import com.mall.seckill.entity.SeckillStatus;
import com.mall.seckill.entity.SeckillStock;
import com.mall.seckill.mapper.SeckillActivityMapper;
import com.mall.seckill.mapper.SeckillStockMapper;
import com.mall.seckill.mq.SeckillActivityChangedEvent;
import com.mall.support.MybatisPlusTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillActivityServiceTest {

    @BeforeAll
    static void registerEntity() {
        MybatisPlusTestSupport.registerEntity(SeckillActivity.class);
    }

    @Mock
    private SeckillActivityMapper activityMapper;

    @Mock
    private SeckillStockMapper stockMapper;

    @Mock
    private ProductService productService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SeckillActivityService activityService;

    @Test
    @DisplayName("创建活动同时建立库存行，默认限购 1 件、超时 15 分钟")
    void createInitializesStockAndDefaults() {
        givenOnSaleProduct(new BigDecimal("399.00"));
        when(activityMapper.insert(any(SeckillActivity.class))).thenAnswer(invocation -> {
            SeckillActivity activity = invocation.getArgument(0);
            activity.setId(5L);
            return 1;
        });
        ArgumentCaptor<SeckillStock> stockCaptor = ArgumentCaptor.forClass(SeckillStock.class);

        SeckillActivityView view = activityService.create(request(new BigDecimal("199.00"), 10));

        verify(stockMapper).insert(stockCaptor.capture());
        assertThat(stockCaptor.getValue().getActivityId()).isEqualTo(5L);
        assertThat(stockCaptor.getValue().getStock()).isEqualTo(10);
        assertThat(view.id()).isEqualTo(5L);
        assertThat(view.perUserLimit()).isEqualTo(1);
        assertThat(view.originalPrice()).isEqualByComparingTo("399.00");
        assertThat(view.status()).isEqualTo(SeckillStatus.RUNNING);
        verify(eventPublisher).publishEvent(any(SeckillActivityChangedEvent.class));
    }

    @Test
    @DisplayName("秒杀价不低于原价时拒绝创建")
    void createRejectsPriceNotLowerThanOriginal() {
        givenOnSaleProduct(new BigDecimal("199.00"));

        assertThatThrownBy(() -> activityService.create(request(new BigDecimal("199.00"), 10)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.SECKILL_PRICE_ILLEGAL.getCode());

        verify(activityMapper, never()).insert(any(SeckillActivity.class));
    }

    @Test
    @DisplayName("结束时间不晚于开始时间时拒绝创建")
    void createRejectsInvalidTimeWindow() {
        givenOnSaleProduct(new BigDecimal("399.00"));
        LocalDateTime now = LocalDateTime.now();
        SeckillActivityCreateRequest request = new SeckillActivityCreateRequest(20L, "限时秒杀",
                new BigDecimal("199.00"), 10, 1, now, now, 15);

        assertThatThrownBy(() -> activityService.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.SECKILL_ACTIVITY_TIME_ILLEGAL.getCode());

        verify(stockMapper, never()).insert(any(SeckillStock.class));
    }

    @Test
    @DisplayName("编辑后结束时间早于开始时间时拒绝")
    void updateRejectsInvalidTimeWindow() {
        SeckillActivity activity = activity();
        when(activityMapper.selectById(1L)).thenReturn(activity);
        LocalDateTime now = LocalDateTime.now();
        SeckillActivityUpdateRequest request = new SeckillActivityUpdateRequest(
                "新标题", 1, now.plusHours(2), now.plusHours(1), 1);

        assertThatThrownBy(() -> activityService.update(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.SECKILL_ACTIVITY_TIME_ILLEGAL.getCode());

        verify(activityMapper, never()).updateById(any(SeckillActivity.class));
    }

    @Test
    @DisplayName("活动不存在时查询抛出 4001")
    void getByIdFailsForMissingActivity() {
        when(activityMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> activityService.getById(99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.SECKILL_ACTIVITY_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("状态由启用标记与时间窗口共同决定")
    void statusFollowsEnabledFlagAndTimeWindow() {
        SeckillActivity activity = activity();

        assertThat(activityService.statusOf(activity)).isEqualTo(SeckillStatus.RUNNING);
        activity.setStatus(0);
        assertThat(activityService.statusOf(activity)).isEqualTo(SeckillStatus.CLOSED);
    }

    private void givenOnSaleProduct(BigDecimal price) {
        when(productService.getOnSale(20L)).thenReturn(new ProductView(20L, "网面透气跑步鞋", "沃兰", 1004L,
                null, null, price, 100, 1, null));
    }

    private SeckillActivityCreateRequest request(BigDecimal seckillPrice, int totalStock) {
        LocalDateTime now = LocalDateTime.now();
        return new SeckillActivityCreateRequest(20L, "限时秒杀", seckillPrice, totalStock, 1,
                now.minusHours(1), now.plusHours(1), 15);
    }

    private SeckillActivity activity() {
        SeckillActivity activity = new SeckillActivity();
        activity.setId(1L);
        activity.setProductId(20L);
        activity.setTitle("限时秒杀");
        activity.setSeckillPrice(new BigDecimal("199.00"));
        activity.setTotalStock(10);
        activity.setPerUserLimit(1);
        activity.setStartTime(LocalDateTime.now().minusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));
        activity.setPayTimeoutMinutes(15);
        activity.setStatus(1);
        return activity;
    }
}
