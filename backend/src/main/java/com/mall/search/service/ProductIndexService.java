package com.mall.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.BulkIndexByScrollFailure;
import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import com.mall.search.embedding.EmbeddingClient;
import com.mall.common.result.PageResult;
import com.mall.product.dto.ProductQuery;
import com.mall.product.dto.ProductView;
import com.mall.product.service.ProductService;
import com.mall.search.entity.ProductCategoryEnum;
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

    /** 单次批量索引的文档数，同时作为重建时的文档缓冲上限 */
    private static final int BULK_SIZE = 500;

    /** 重建时读取商品的页大小，不能超过 ProductQuery 的 size 上限 */
    private static final int PAGE_SIZE = 100;

    /** 单次 embedding 请求的文本条数，受接口批量上限与请求体大小约束 */
    private static final int EMBED_BATCH_SIZE = 32;

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
            embedding = embeddingClient.embed(assembleText(product));
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
     * 全量重建索引：先清空旧文档，再按库内数据逐批写入
     *
     * @return 实际成功写入的文档数
     */
    public int reindex() {
        long cleared = clearIndex();
        log.info("索引清空完成，删除文档 {} 条", cleared);

        List<ProductDoc> buffer = new ArrayList<>(BULK_SIZE);
        int page = 1;
        int processed = 0;
        int indexed = 0;
        while (true) {
            ProductQuery query = new ProductQuery();
            query.setPage(page);
            query.setSize(PAGE_SIZE);
            PageResult<ProductView> result = productService.list(query);
            List<ProductView> products = result.list();
            if (products.isEmpty()) {
                break;
            }

            for (ProductDoc doc : buildDocs(products)) {
                buffer.add(doc);
                if (buffer.size() >= BULK_SIZE) {
                    indexed += submit(buffer);
                    buffer.clear();
                }
            }
            processed += products.size();

            if (products.size() < PAGE_SIZE) {
                break;
            }
            page++;
        }

        if (!buffer.isEmpty()) {
            indexed += submit(buffer);
        }
        refreshIndex();
        log.info("索引重建完成，读取商品 {} 条，成功写入 {} 条", processed, indexed);
        return indexed;
    }

    /**
     * 清空索引内全部文档，refresh=true 保证重建基于空索引
     */
    private long clearIndex() {
        try {
            DeleteByQueryResponse response = client.deleteByQuery(d -> d
                    .index(indexAlias)
                    .query(q -> q.matchAll(m -> m))
                    .conflicts(Conflicts.Proceed)
                    .ignoreUnavailable(true)
                    .allowNoIndices(true)
                    .refresh(true));
            List<BulkIndexByScrollFailure> failures =
                    response.failures() == null ? List.of() : response.failures();
            if (!failures.isEmpty()) {
                log.error("清空索引存在失败：删除 {} 条，失败 {} 条", response.deleted(), failures.size());
                failures.stream()
                        .limit(5)
                        .forEach(failure -> log.error("文档 {} 删除失败：{}", failure.id(),
                                failure.cause() == null ? null : failure.cause().reason()));
            }
            return response.deleted() == null ? 0L : response.deleted();
        } catch (IOException | RuntimeException e) {
            log.error("清空索引失败，索引重建中止", e);
            throw new IllegalStateException("清空索引失败", e);
        }
    }

    /**
     * 重建结束后统一刷新一次，避免每条文档单独 refresh
     */
    private void refreshIndex() {
        try {
            client.indices().refresh(r -> r.index(indexAlias));
        } catch (IOException | RuntimeException e) {
            log.warn("索引刷新失败，新写入的文档可能延迟可见：{}", e.getMessage());
        }
    }

    private List<ProductDoc> buildDocs(List<ProductView> products) {
        List<String> texts = new ArrayList<>(products.size());
        for (ProductView product : products) {
            texts.add(assembleText(product));
        }

        List<float[]> embeddings = embedAll(texts);
        List<ProductDoc> docs = new ArrayList<>(products.size());
        for (int i = 0; i < products.size(); i++) {
            docs.add(toDoc(products.get(i), embeddings.get(i)));
        }
        return docs;
    }

    /**
     * 分批向量化，单批失败只影响该批文档，这些文档退化为纯 BM25 召回
     */
    private List<float[]> embedAll(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int from = 0; from < texts.size(); from += EMBED_BATCH_SIZE) {
            int to = Math.min(from + EMBED_BATCH_SIZE, texts.size());
            List<float[]> embedded;
            try {
                embedded = embeddingClient.embed(texts.subList(from, to));
            } catch (RuntimeException e) {
                log.warn("第 {}-{} 条文本向量化失败，该批文档只同步业务字段：{}", from + 1, to, e.getMessage());
                embedded = null;
            }
            for (int i = from; i < to; i++) {
                vectors.add(embedded == null ? null : embedded.get(i - from));
            }
        }
        return vectors;
    }

    /**
     * 提交一批文档
     *
     * @return 本批成功写入的文档数
     */
    private int submit(List<ProductDoc> docs) {
        BulkRequest.Builder builder = new BulkRequest.Builder();
        for (ProductDoc doc : docs) {
            builder.operations(op -> op.index(idx -> idx
                    .index(indexAlias)
                    .id(String.valueOf(doc.productId()))
                    .document(doc)));
        }
        BulkResponse response;
        try {
            response = client.bulk(builder.build());
        } catch (IOException | RuntimeException e) {
            log.error("批量写入索引失败，本批 {} 条未写入", docs.size(), e);
            throw new IllegalStateException("批量写入索引失败", e);
        }
        if (!response.errors()) {
            return docs.size();
        }

        List<BulkResponseItem> failed = response.items().stream()
                .filter(item -> item.error() != null)
                .toList();
        log.error("批量写入索引存在失败：本批 {} 条，失败 {} 条", docs.size(), failed.size());
        failed.stream()
                .limit(5)
                .forEach(item -> log.error("文档 {} 写入失败：{}", item.id(), item.error().reason()));
        return docs.size() - failed.size();
    }

    /**
     * 组装检索文本，分类名由固定分类字典转换，未知分类按空段跳过
     */
    private String assembleText(ProductView product) {
        return textAssembler.assemble(product.name(), product.brand(),
                ProductCategoryEnum.nameOf(product.categoryId()),
                product.sellingPoint(), product.description());
    }

    private ProductDoc toDoc(ProductView product, float[] embedding) {
        return ProductDoc.of(product.id(), product.name(), product.brand(), product.categoryId(),
                product.sellingPoint(), product.description(), product.price(), product.stock(),
                product.status(), product.imageUrl(), embedding);
    }
}
