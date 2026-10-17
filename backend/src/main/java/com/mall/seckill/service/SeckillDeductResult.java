package com.mall.seckill.service;

/**
 * 秒杀预扣结果，对应 Lua 脚本的返回值
 */
public enum SeckillDeductResult {

    SUCCESS(1),
    NOT_STARTED(-1),
    ENDED(-2),
    SOLD_OUT(-3),
    DUPLICATE(-4),
    NOT_PREHEATED(-5),
    UNKNOWN(0);

    private final int code;

    SeckillDeductResult(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static SeckillDeductResult fromCode(long code) {
        for (SeckillDeductResult result : values()) {
            if (result.code == code) {
                return result;
            }
        }
        return UNKNOWN;
    }
}
