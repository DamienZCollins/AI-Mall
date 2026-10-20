package com.mall.search.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductTextAssemblerTest {

    private final ProductTextAssembler assembler = new ProductTextAssembler();

    @Test
    @DisplayName("按品牌、分类、卖点、描述的顺序拼接，各段以句号分隔")
    void assemblesFieldsInFixedOrder() {
        String text = assembler.assemble("网面透气跑步鞋", "沃兰", "服饰箱包", "网面透气", "适合夏季通勤");

        assertThat(text).isEqualTo("网面透气跑步鞋。品牌：沃兰。分类：服饰箱包。卖点：网面透气。描述：适合夏季通勤");
    }

    @Test
    @DisplayName("空白字段跳过，不留下空段落")
    void skipsBlankFields() {
        String text = assembler.assemble("轻量耳机", "  ", null, "", "续航 32 小时");

        assertThat(text).isEqualTo("轻量耳机。描述：续航 32 小时");
    }

    @Test
    @DisplayName("字段值去除首尾空白")
    void trimsFieldValues() {
        String text = assembler.assemble("  耳机  ", "  声屿  ", null, "  降噪  ", null);

        assertThat(text).isEqualTo("耳机。品牌：声屿。卖点：降噪");
    }

    @Test
    @DisplayName("超出长度上限时截断到 1500 字符")
    void truncatesToMaxLength() {
        String longDescription = "透".repeat(2000);

        String text = assembler.assemble("跑步鞋", null, null, null, longDescription);

        assertThat(text).hasSize(1500);
        assertThat(text).startsWith("跑步鞋。描述：透");
    }

    @Test
    @DisplayName("名称为空时只输出属性段")
    void handlesMissingName() {
        String text = assembler.assemble(null, "青岚", "家居", null, null);

        assertThat(text).isEqualTo("。品牌：青岚。分类：家居");
    }

    @Test
    @DisplayName("全部字段为空时返回空串")
    void returnsEmptyWhenAllBlank() {
        assertThat(assembler.assemble(null, null, null, null, null)).isEmpty();
    }
}
