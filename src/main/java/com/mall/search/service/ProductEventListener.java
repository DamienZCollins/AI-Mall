package com.mall.search.service;

import com.mall.product.dto.ProductView;
import com.mall.product.mq.ProductChangedEvent;
import com.mall.product.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 商品变更后同步索引：事务提交后再执行，避免索引到未提交的数据
 */
@Component
public class ProductEventListener {

    private static final Logger log = LoggerFactory.getLogger(ProductEventListener.class);

    private final ProductService productService;
    private final ProductIndexService productIndexService;

    public ProductEventListener(ProductService productService, ProductIndexService productIndexService) {
        this.productService = productService;
        this.productIndexService = productIndexService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductChanged(ProductChangedEvent event) {
        try {
            ProductView product = productService.getById(event.productId());
            productIndexService.sync(product);
        } catch (RuntimeException e) {
            log.error("商品 {} 索引同步失败", event.productId(), e);
        }
    }
}
