package com.mall.search.dto;

import com.mall.search.entity.ProductDoc;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 搜索结果条目
 */
@Schema(description = "搜索结果条目")
public record SearchHit(

        @Schema(description = "商品 ID")
        Long productId,

        @Schema(description = "商品名称")
        String name,

        @Schema(description = "品牌")
        String brand,

        @Schema(description = "分类 ID")
        Long categoryId,

        @Schema(description = "卖点")
        String sellingPoint,

        @Schema(description = "售价")
        BigDecimal price,

        @Schema(description = "库存")
        Integer stock,

        @Schema(description = "商品图片 URL")
        String imageUrl,

        @Schema(description = "RRF 融合分，仅用于调试观察排序")
        Double score,

        @Schema(description = "本文档是否带语义向量")
        Boolean hasVector
) {

    public static SearchHit from(ProductDoc doc, Double score) {
        return new SearchHit(doc.productId(), doc.name(), doc.brand(), doc.categoryId(), doc.sellingPoint(),
                doc.price(), doc.stock(), doc.imageUrl(), score, doc.hasVector());
    }
}
