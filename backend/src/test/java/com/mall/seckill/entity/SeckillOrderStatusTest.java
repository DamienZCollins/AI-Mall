package com.mall.seckill.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeckillOrderStatusTest {

    @Test
    @DisplayName("待支付可以流转到支付、取消与超时取消")
    void pendingPaymentCanTransitionToAllTerminalStates() {
        assertThat(SeckillOrderStatus.PENDING_PAYMENT.canTransitionTo(SeckillOrderStatus.PAID)).isTrue();
        assertThat(SeckillOrderStatus.PENDING_PAYMENT.canTransitionTo(SeckillOrderStatus.CANCELLED)).isTrue();
        assertThat(SeckillOrderStatus.PENDING_PAYMENT.canTransitionTo(SeckillOrderStatus.TIMEOUT_CANCELLED)).isTrue();
    }

    @Test
    @DisplayName("待支付不能流转到自身")
    void pendingPaymentCannotTransitionToItself() {
        assertThat(SeckillOrderStatus.PENDING_PAYMENT.canTransitionTo(SeckillOrderStatus.PENDING_PAYMENT)).isFalse();
    }

    @Test
    @DisplayName("终态不能流转到任何状态")
    void terminalStatesAreFinal() {
        for (SeckillOrderStatus terminal : new SeckillOrderStatus[]{
                SeckillOrderStatus.PAID, SeckillOrderStatus.CANCELLED, SeckillOrderStatus.TIMEOUT_CANCELLED}) {
            for (SeckillOrderStatus target : SeckillOrderStatus.values()) {
                assertThat(terminal.canTransitionTo(target)).as("%s -> %s", terminal, target).isFalse();
            }
        }
    }

    @Test
    @DisplayName("取消与超时取消视为库存已释放，已支付不是")
    void releasedOnlyCoversCancelledStates() {
        assertThat(SeckillOrderStatus.CANCELLED.isReleased()).isTrue();
        assertThat(SeckillOrderStatus.TIMEOUT_CANCELLED.isReleased()).isTrue();
        assertThat(SeckillOrderStatus.PAID.isReleased()).isFalse();
        assertThat(SeckillOrderStatus.PENDING_PAYMENT.isReleased()).isFalse();
    }

    @Test
    @DisplayName("按状态码解析，未知状态码返回 null")
    void fromCodeReturnsNullForUnknown() {
        assertThat(SeckillOrderStatus.fromCode(1)).isEqualTo(SeckillOrderStatus.PENDING_PAYMENT);
        assertThat(SeckillOrderStatus.fromCode(4)).isEqualTo(SeckillOrderStatus.TIMEOUT_CANCELLED);
        assertThat(SeckillOrderStatus.fromCode(99)).isNull();
        assertThat(SeckillOrderStatus.fromCode(null)).isNull();
    }
}
