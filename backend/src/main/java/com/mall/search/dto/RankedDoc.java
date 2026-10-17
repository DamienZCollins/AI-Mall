package com.mall.search.dto;

import com.mall.search.entity.ProductDoc;

/**
 * 单路检索的候选文档，保留命中文档与用于排序的原始分
 */
public record RankedDoc(String id, ProductDoc doc, Double score) {
}
