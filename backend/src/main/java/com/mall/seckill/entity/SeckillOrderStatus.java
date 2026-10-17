package com.mall.seckill.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 秒杀订单状态机
 *
 * <pre>
 * 待支付 -> 已支付
 * 待支付 -> 已取消
 * 待支付 -> 超时取消
 * </pre>
 */
public enum SeckillOrderStatus {

    PENDING_PAYMENT(1, "待支付"),
    PAID(2, "已支付"),
    CANCELLED(3, "已取消"),
    TIMEOUT_CANCELLED(4, "超时取消");

    private final int code;
    private final String description;

    SeckillOrderStatus(int code, String description) {
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
     * 只有待支付状态可以流转，其余状态是终态
     */
    public boolean canTransitionTo(SeckillOrderStatus target) {
        return this == PENDING_PAYMENT && target != PENDING_PAYMENT;
    }

    /**
     * 已支付以外都可视为已释放库存
     */
    public boolean isReleased() {
        return this == CANCELLED || this == TIMEOUT_CANCELLED;
    }

    @JsonCreator
    public static SeckillOrderStatus fromCode(Integer code) {
        if (code != null) {
            for (SeckillOrderStatus status : values()) {
                if (status.code == code) {
                    return status;
                }
            }
        }
        return null;
    }
}
