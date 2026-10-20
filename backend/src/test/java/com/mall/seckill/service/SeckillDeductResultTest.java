package com.mall.seckill.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeckillDeductResultTest {

    @Test
    @DisplayName("Lua 脚本返回码映射到对应结果")
    void mapsScriptCodes() {
        assertThat(SeckillDeductResult.fromCode(1)).isEqualTo(SeckillDeductResult.SUCCESS);
        assertThat(SeckillDeductResult.fromCode(-1)).isEqualTo(SeckillDeductResult.NOT_STARTED);
        assertThat(SeckillDeductResult.fromCode(-2)).isEqualTo(SeckillDeductResult.ENDED);
        assertThat(SeckillDeductResult.fromCode(-3)).isEqualTo(SeckillDeductResult.SOLD_OUT);
        assertThat(SeckillDeductResult.fromCode(-4)).isEqualTo(SeckillDeductResult.DUPLICATE);
        assertThat(SeckillDeductResult.fromCode(-5)).isEqualTo(SeckillDeductResult.NOT_PREHEATED);
    }

    @Test
    @DisplayName("未识别的返回码视为未知，避免误判为成功")
    void unknownCodesMapToUnknown() {
        assertThat(SeckillDeductResult.fromCode(0)).isEqualTo(SeckillDeductResult.UNKNOWN);
        assertThat(SeckillDeductResult.fromCode(404)).isEqualTo(SeckillDeductResult.UNKNOWN);
    }
}
