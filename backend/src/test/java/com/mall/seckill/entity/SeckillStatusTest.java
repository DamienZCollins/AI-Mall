package com.mall.seckill.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SeckillStatusTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 12, 0);

    @Test
    @DisplayName("活动停用时优先返回已关闭，与时间窗口无关")
    void closedTakesPrecedence() {
        LocalDateTime start = NOW.minusHours(1);

        assertThat(SeckillStatus.of(false, start, NOW.plusHours(1), NOW)).isEqualTo(SeckillStatus.CLOSED);
        assertThat(SeckillStatus.of(false, start, NOW.minusMinutes(1), NOW)).isEqualTo(SeckillStatus.CLOSED);
        assertThat(SeckillStatus.of(false, NOW.plusHours(1), NOW.plusHours(2), NOW)).isEqualTo(SeckillStatus.CLOSED);
    }

    @Test
    @DisplayName("当前时间早于开始时间为未开始")
    void notStartedBeforeStartTime() {
        assertThat(SeckillStatus.of(true, NOW.plusSeconds(1), NOW.plusHours(1), NOW))
                .isEqualTo(SeckillStatus.NOT_STARTED);
    }

    @Test
    @DisplayName("当前时间晚于结束时间为已结束")
    void endedAfterEndTime() {
        assertThat(SeckillStatus.of(true, NOW.minusHours(1), NOW.minusSeconds(1), NOW))
                .isEqualTo(SeckillStatus.ENDED);
    }

    @Test
    @DisplayName("开始与结束时刻本身都算进行中，边界闭合")
    void runningIncludesBothBoundaries() {
        assertThat(SeckillStatus.of(true, NOW, NOW.plusHours(1), NOW)).isEqualTo(SeckillStatus.RUNNING);
        assertThat(SeckillStatus.of(true, NOW.minusHours(1), NOW, NOW)).isEqualTo(SeckillStatus.RUNNING);
    }
}
