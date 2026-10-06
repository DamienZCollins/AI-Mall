package com.mall.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch._types.Refresh;
import com.mall.common.embedding.EmbeddingClient;
import com.mall.common.result.PageResult;
import com.mall.product.dto.ProductQuery;
import com.mall.product.dto.ProductView;
import com.mall.product.service.ProductService;
import com.mall.search.entity.ProductDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 商品索引同步与重建
 */
@Service
public class ProductIndexService {

    private static final Logger log = LoggerFactory.getLogger(ProductIndexService.class);

    /** 单次批量索引的文档数 */
    private static final int BULK_SIZE = 500;

    private final ElasticsearchClient client;
    private final EmbeddingClient embeddingClient;
    private final ProductTextAssembler textAssembler;
    private final ProductService productService;

    @Value("${mall.search.index-alias}")
    private String indexAlias;

    public ProductIndexService(ElasticsearchClient client, EmbeddingClient embeddingClient,
                               ProductTextAssembler textAssembler, ProductService productService) {
        this.client = client;
        this.embeddingClient = embeddingClient;
        this.textAssembler = textAssembler;
        this.productService = productService;
    }

    /**
     * 同步单个商品，embedding 失败时只写业务字段，保证 BM25 仍能召回
     *
     * @throws IllegalStateException ES 写入失败，由调用方决定重试或兜底
     */
    public void sync(ProductView product) {
        float[] embedding = null;
        try {
            String text = textAssembler.assemble(product.name(), product.brand(), null,
                    product.sellingPoint(), product.description());
            embedding = embeddingClient.embed(text);
        } catch (RuntimeException e) {
            log.warn("商品 {} 向量化失败，本次只同步业务字段：{}", product.id(), e.getMessage());
        }

        ProductDoc doc = toDoc(product, embedding);
        try {
            client.index(i -> i
                    .index(indexAlias)
                    .id(String.valueOf(product.id()))
                    .document(doc)
                    .refresh(Refresh.WaitFor));
            log.debug("商品 {} 已同步索引，hasVector={}", product.id(), doc.hasVector());
        } catch (IOException | RuntimeException e) {
            log.error("商品 {} 同步索引失败", product.id(), e);
            throw new IllegalStateException("商品 " + product.id() + " 同步索引失败", e);
        }
    }

    public void delete(Long productId) {
        try {
            client.delete(d -> d
                    .index(indexAlias)
                    .id(String.valueOf(productId))
                    .refresh(Refresh.WaitFor));
        } catch (IOException | RuntimeException e) {
            log.error("商品 {} 删除索引失败", productId, e);
        }
    }

    /**
     * 全量重建索引，逐页读取商品后按批写入
     */
    public int reindex() {
        List<ProductDoc> batch = new ArrayList<>(BULK_SIZE);
        int total = 0;
        int page = 1;

        while (true) {
            ProductQuery query = new ProductQuery();
            query.setPage(page);
            query.setSize(BULK_SIZE);
            PageResult<ProductView> result = productService.list(query);
            List<ProductView> products = result.list();
            if (products.isEmpty()) {
                break;
            }

            for (ProductView product : products) {
                String text = textAssembler.assemble(product.name(), product.brand(), null,
                        product.sellingPoint(), product.description());
                float[] embedding = null;
                try {
                    embedding = embeddingClient.embed(text);
                } catch (RuntimeException e) {
                    log.warn("商品 {} 向量化失败：{}", product.id(), e.getMessage());
                }
                batch.add(toDoc(product, embedding));
            }
            total += products.size();

            if (products.size() < BULK_SIZE) {
                break;
            }
            page++;
        }

        if (!batch.isEmpty()) {
            submit(batch);
        }
        log.info("索引重建完成，共 {} 条", total);
        return total;
    }

    private void submit(List<ProductDoc> docs) {
        BulkRequest.Builder builder = new BulkRequest.Builder();
        for (ProductDoc doc : docs) {
            builder.operations(op -> op.index(idx -> idx
                    .index(indexAlias)
                    .id(String.valueOf(doc.productId()))
                    .document(doc)));
        }
        try {
            BulkResponse response = client.bulk(builder.build());
            if (response.errors()) {
                response.items().stream()
                        .filter(item -> item.error() != null)
                        .limit(5)
                        .forEach(item -> log.error("文档 {} 写入失败：{}", item.id(), item.error().reason()));
            }
        } catch (IOException | RuntimeException e) {
            log.error("批量写入索引失败", e);
            throw new IllegalStateException("批量写入索引失败", e);
        }
    }

    private ProductDoc toDoc(ProductView product, float[] embedding) {
        return ProductDoc.of(product.id(), product.name(), product.brand(), product.categoryId(),
                product.sellingPoint(), product.description(), product.price(), product.stock(),
                product.status(), product.imageUrl(), embedding);
    }
}
