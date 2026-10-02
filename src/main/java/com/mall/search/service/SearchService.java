package com.mall.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.mall.common.embedding.EmbeddingClient;
import com.mall.common.exception.BusinessException;
import com.mall.common.result.PageResult;
import com.mall.common.result.ResultCode;
import com.mall.search.dto.RankedDoc;
import com.mall.search.dto.SearchHit;
import com.mall.search.dto.SearchQuery;
import com.mall.search.entity.ProductDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 混合检索：BM25 关键词召回 + kNN 语义召回，两路结果在应用层做 RRF 融合
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    /** 单路召回的候选倍数，窗口过小会让第二路的好结果进不了融合 */
    private static final int CANDIDATE_MULTIPLIER = 2;

    private static final int MAX_WINDOW = 200;

    /** 商品上架状态 */
    private static final int STATUS_ON_SALE = 1;

    private static final List<String> SEARCH_FIELDS = List.of("name^3", "brand^2", "sellingPoint", "description");

    private final ElasticsearchClient client;
    private final EmbeddingClient embeddingClient;

    @Value("${mall.search.index-alias}")
    private String indexAlias;

    public SearchService(ElasticsearchClient client, EmbeddingClient embeddingClient) {
        this.client = client;
        this.embeddingClient = embeddingClient;
    }

    public PageResult<SearchHit> search(SearchQuery query) {
        int page = query.getPage();
        int size = query.getSize();
        int window = Math.min(Math.max(page * size, 1) * CANDIDATE_MULTIPLIER, MAX_WINDOW);
        String keyword = query.getKeyword().trim();

        float[] queryVector = embeddingClient.embed(keyword);
        List<RankedDoc> bm25Hits = keywordSearch(keyword, window);
        List<RankedDoc> knnHits = knnSearch(queryVector, window);

        List<RankedDoc> fused = RrfFuser.fuse(bm25Hits, knnHits, RrfFuser.DEFAULT_RANK_CONSTANT);
        int from = Math.min((page - 1) * size, fused.size());
        int to = Math.min(from + size, fused.size());

        List<SearchHit> hits = new ArrayList<>(to - from);
        for (RankedDoc doc : fused.subList(from, to)) {
            hits.add(SearchHit.from(doc.doc(), doc.score()));
        }
        return PageResult.of(fused.size(), page, size, hits);
    }

    /**
     * 关键词召回，上架状态由过滤条件保证
     */
    private List<RankedDoc> keywordSearch(String keyword, int size) {
        SearchRequest request = SearchRequest.of(s -> s
                .index(indexAlias)
                .size(size)
                .query(q -> q.bool(b -> b
                        .must(m -> m.multiMatch(mm -> mm.query(keyword).fields(SEARCH_FIELDS)))
                        .filter(Query.of(f -> f.term(t -> t.field("status").value(STATUS_ON_SALE)))))));
        return execute(request, "BM25");
    }

    /**
     * 语义召回，只检索已生成向量的文档
     */
    private List<RankedDoc> knnSearch(float[] vector, int size) {
        SearchRequest request = SearchRequest.of(s -> s
                .index(indexAlias)
                .size(size)
                .knn(k -> k
                        .field("embedding")
                        .queryVector(toList(vector))
                        .k(size)
                        .numCandidates(size * 2)
                        .filter(Query.of(f -> f.term(t -> t.field("status").value(STATUS_ON_SALE))),
                                Query.of(f -> f.term(t -> t.field("hasVector").value(true))))));
        return execute(request, "kNN");
    }

    private List<RankedDoc> execute(SearchRequest request, String path) {
        try {
            SearchResponse<ProductDoc> response = client.search(request, ProductDoc.class);
            List<RankedDoc> hits = new ArrayList<>();
            for (Hit<ProductDoc> hit : response.hits().hits()) {
                ProductDoc doc = hit.source();
                if (doc != null) {
                    hits.add(new RankedDoc(hit.id(), doc, hit.score()));
                }
            }
            return hits;
        } catch (IOException | RuntimeException e) {
            log.error("{} 召回失败", path, e);
            throw new BusinessException(ResultCode.SEARCH_UNAVAILABLE);
        }
    }

    private List<Float> toList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float value : vector) {
            list.add(value);
        }
        return list;
    }
}
