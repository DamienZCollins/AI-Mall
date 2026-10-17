package com.mall.search.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * 商品检索文档，对应 ES 文档结构
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProductDoc(
        Long productId,
        String name,
        String brand,
        Long categoryId,
        String sellingPoint,
        String description,
        BigDecimal price,
        Integer stock,
        Integer status,
        String imageUrl,
        Boolean hasVector,
        float[] embedding
) {

    public static ProductDoc of(Long productId, String name, String brand, Long categoryId,
                                String sellingPoint, String description, BigDecimal price, Integer stock,
                                Integer status, String imageUrl, float[] embedding) {
        return new ProductDoc(productId, name, brand, categoryId, sellingPoint, description,
                price, stock, status, imageUrl, embedding != null, embedding);
    }
}
