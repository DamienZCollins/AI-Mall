package com.mall.seckill.entity;

import com.fasterxml.jackson.annotation.JsonValue;

import java.time.LocalDateTime;

/**
 * 秒杀活动状态，由当前时间与活动时间窗口推导，不落库
 */
public enum SeckillStatus {

    NOT_STARTED(0, "未开始"),
    RUNNING(1, "进行中"),
    ENDED(2, "已结束"),
    CLOSED(3, "已关闭");

    private final int code;
    private final String description;

    SeckillStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonValue
    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 已关闭优先，其次按时间窗口判断
     */
    public static SeckillStatus of(boolean enabled, LocalDateTime startTime, LocalDateTime endTime, LocalDateTime now) {
        if (!enabled) {
            return CLOSED;
        }
        if (now.isBefore(startTime)) {
            return NOT_STARTED;
        }
        return now.isAfter(endTime) ? ENDED : RUNNING;
    }
}
