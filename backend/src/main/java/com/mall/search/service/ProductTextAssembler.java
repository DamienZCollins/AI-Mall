package com.mall.search.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 检索文本组装：商品入库与查询共用，保证两边的语义空间一致
 */
@Component
public class ProductTextAssembler {

    /** 向量模型的输入长度上限，超长直接截断 */
    private static final int MAX_LENGTH = 1500;

    /** 卖点优先，描述靠后，符合中文描述习惯 */
    public String assemble(String name, String brand, String categoryName, String sellingPoint, String description) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(brand)) {
            parts.add("品牌：" + brand.trim());
        }
        if (StringUtils.hasText(categoryName)) {
            parts.add("分类：" + categoryName.trim());
        }
        if (StringUtils.hasText(sellingPoint)) {
            parts.add("卖点：" + sellingPoint.trim());
        }
        if (StringUtils.hasText(description)) {
            parts.add("描述：" + description.trim());
        }

        String text = StringUtils.hasText(name) ? name.trim() : "";
        if (!parts.isEmpty()) {
            text = text + "。" + String.join("。", parts);
        }
        return text.length() > MAX_LENGTH ? text.substring(0, MAX_LENGTH) : text;
    }
}
