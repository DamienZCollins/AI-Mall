package com.mall.search.service;

import com.mall.product.dto.ProductView;
import com.mall.product.mq.ProductChangedEvent;
import com.mall.product.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 商品变更后同步索引：事务提交后再执行，避免索引到未提交的数据
 *
 * <p>监听方法在 AFTER_COMMIT 阶段派发到索引线程池执行，请求线程不等待 embedding 与 ES 写入。
 */
@Component
public class ProductEventListener {

    private static final Logger log = LoggerFactory.getLogger(ProductEventListener.class);

    /** 索引同步的最大尝试次数，ES 短暂不可用时靠重试兜住 */
    private static final int MAX_ATTEMPTS = 3;

    /** 重试间隔，ES 抖动通常在秒级恢复 */
    private static final long RETRY_DELAY_MILLIS = 1000;

    private final ProductService productService;
    private final ProductIndexService productIndexService;

    public ProductEventListener(ProductService productService, ProductIndexService productIndexService) {
        this.productService = productService;
        this.productIndexService = productIndexService;
    }

    @Async("productIndexExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductChanged(ProductChangedEvent event) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                ProductView product = productService.getById(event.productId());
                productIndexService.sync(product);
                return;
            } catch (RuntimeException e) {
                if (attempt == MAX_ATTEMPTS) {
                    // 仍有全量重建接口兜底：POST /api/admin/search/reindex
                    log.error("商品 {} 索引同步失败，已尝试 {} 次，可调用重建接口恢复", event.productId(), attempt, e);
                    return;
                }
                log.warn("商品 {} 索引同步失败，第 {} 次重试", event.productId(), attempt);
                sleep();
            }
        }
    }

    private void sleep() {
        try {
            Thread.sleep(RETRY_DELAY_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
