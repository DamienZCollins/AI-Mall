package com.mall.seckill.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeckillOrderNoTest {

    @Test
    @DisplayName("订单号由前缀、时间戳、用户与活动拼接")
    void buildsReadableOrderNo() {
        String orderNo = SeckillOrderNo.generate(12L, 34L);

        assertThat(orderNo).matches("SK\\d{13}-12-34");
    }

    @Test
    @DisplayName("同一用户与活动再次下单时时间戳不同，订单号不重复")
    void regeneratesOnEachCall() throws InterruptedException {
        String first = SeckillOrderNo.generate(1L, 1L);
        Thread.sleep(2);
        String second = SeckillOrderNo.generate(1L, 1L);

        assertThat(second).isNotEqualTo(first);
    }

    @Test
    @DisplayName("不同用户或活动的订单号不会撞号")
    void distinctAcrossUsersAndActivities() {
        assertThat(SeckillOrderNo.generate(1L, 2L)).isNotEqualTo(SeckillOrderNo.generate(2L, 2L));
        assertThat(SeckillOrderNo.generate(1L, 2L)).isNotEqualTo(SeckillOrderNo.generate(1L, 3L));
    }
}
