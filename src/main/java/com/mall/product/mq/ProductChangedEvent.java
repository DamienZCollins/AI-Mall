package com.mall.product.mq;

/**
 * 商品变更事件，search 模块监听后同步索引
 */
public record ProductChangedEvent(Long productId) {
}
