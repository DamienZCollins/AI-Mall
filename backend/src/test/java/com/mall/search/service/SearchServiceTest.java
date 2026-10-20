package com.mall.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import com.mall.common.exception.BusinessException;
import com.mall.common.result.PageResult;
import com.mall.common.result.ResultCode;
import com.mall.search.dto.SearchHit;
import com.mall.search.dto.SearchQuery;
import com.mall.search.embedding.EmbeddingClient;
import com.mall.search.entity.ProductDoc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private ElasticsearchClient client;

    @Mock
    private EmbeddingClient embeddingClient;

    private ThreadPoolTaskExecutor recallExecutor;
    private SearchService searchService;

    @BeforeEach
    void setUp() {
        recallExecutor = new ThreadPoolTaskExecutor();
        recallExecutor.setCorePoolSize(2);
        recallExecutor.setMaxPoolSize(4);
        recallExecutor.initialize();
        searchService = new SearchService(client, embeddingClient, recallExecutor);
        ReflectionTestUtils.setField(searchService, "indexAlias", "mall_product");
    }

    @AfterEach
    void tearDown() {
        recallExecutor.shutdown();
    }

    @Test
    @DisplayName("两路召回结果按 RRF 融合，两路都命中的文档排在前面")
    void fusesBothRecallPaths() throws Exception {
        when(embeddingClient.embed("透气跑步鞋")).thenReturn(new float[]{0.1f, 0.2f});
        when(client.search(any(SearchRequest.class), eq(ProductDoc.class)))
                .thenReturn(response(hit("1", 12.0), hit("2", 8.0)))   // BM25
                .thenReturn(response(hit("2", 0.95)));                  // kNN

        PageResult<SearchHit> result = searchService.search(query("透气跑步鞋", 1, 10));

        assertThat(result.list()).extracting(SearchHit::productId).containsExactly(2L, 1L);
        assertThat(result.list().get(0).degraded()).isFalse();
        assertThat(result.list().get(0).score()).isGreaterThan(result.list().get(1).score());
    }

    @Test
    @DisplayName("向量化失败时降级为纯 BM25，结果标记 degraded")
    void degradesToBm25WhenEmbeddingFails() throws Exception {
        when(embeddingClient.embed("跑步鞋")).thenThrow(new BusinessException(ResultCode.EMBEDDING_FAILED));
        when(client.search(any(SearchRequest.class), eq(ProductDoc.class)))
                .thenReturn(response(hit("1", 12.0)));

        PageResult<SearchHit> result = searchService.search(query("跑步鞋", 1, 10));

        assertThat(result.list()).hasSize(1);
        assertThat(result.list().get(0).degraded()).isTrue();
        // 只发起 BM25 一路召回
        verify(client, times(1)).search(any(SearchRequest.class), eq(ProductDoc.class));
    }

    @Test
    @DisplayName("向量化结果为空时也按降级处理")
    void degradesWhenEmbeddingReturnsNothing() throws Exception {
        when(embeddingClient.embed("跑步鞋")).thenReturn(null);
        when(client.search(any(SearchRequest.class), eq(ProductDoc.class)))
                .thenReturn(response(hit("1", 1.0)));

        assertThat(searchService.search(query("跑步鞋", 1, 10)).list().get(0).degraded()).isTrue();
    }

    @Test
    @DisplayName("ES 检索失败返回 3002，不把底层异常暴露给调用方")
    void failsWithSearchUnavailableWhenEsFails() throws Exception {
        when(embeddingClient.embed(anyString())).thenReturn(new float[]{0.1f});
        when(client.search(any(SearchRequest.class), eq(ProductDoc.class)))
                .thenThrow(new IOException("connection refused"));

        assertThatThrownBy(() -> searchService.search(query("跑步鞋", 1, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.SEARCH_UNAVAILABLE.getCode());
    }

    @Test
    @DisplayName("分页在融合结果上切片，索引窗口随页码扩大且不超过 200")
    void paginatesFusedResults() throws Exception {
        when(embeddingClient.embed(anyString())).thenReturn(new float[]{0.1f});
        when(client.search(any(SearchRequest.class), eq(ProductDoc.class)))
                .thenReturn(response(hit("1", 1.0), hit("2", 1.0), hit("3", 1.0)))
                .thenReturn(response());

        PageResult<SearchHit> result = searchService.search(query("跑步鞋", 2, 2));
        assertThat(result.list()).extracting(SearchHit::productId).containsExactly(3L);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client, times(2)).search(captor.capture(), eq(ProductDoc.class));
        // 第 2 页每页 2 条，候选窗口为 2 * 2 * 2 = 8
        assertThat(captor.getAllValues()).allSatisfy(request -> assertThat(request.size()).isEqualTo(8));
    }

    @Test
    @DisplayName("页码超出融合结果时返回空列表而不是越界")
    void returnsEmptyListBeyondLastPage() throws Exception {
        when(embeddingClient.embed(anyString())).thenReturn(new float[]{0.1f});
        when(client.search(any(SearchRequest.class), eq(ProductDoc.class)))
                .thenReturn(response(hit("1", 1.0)))
                .thenReturn(response());

        assertThat(searchService.search(query("跑步鞋", 5, 10)).list()).isEmpty();
    }

    @Test
    @DisplayName("检索词去除首尾空白后参与召回")
    void trimsKeyword() throws Exception {
        when(embeddingClient.embed("跑步鞋")).thenReturn(new float[]{0.1f});
        when(client.search(any(SearchRequest.class), eq(ProductDoc.class))).thenReturn(response());

        searchService.search(query("  跑步鞋  ", 1, 10));

        verify(embeddingClient).embed("跑步鞋");
    }

    @Test
    @DisplayName("非向量化异常原样抛出，不误判为降级")
    void rethrowsUnrelatedBusinessException() throws Exception {
        when(embeddingClient.embed(anyString()))
                .thenThrow(new BusinessException(ResultCode.SEARCH_UNAVAILABLE));

        assertThatThrownBy(() -> searchService.search(query("跑步鞋", 1, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.SEARCH_UNAVAILABLE.getCode());

        verify(client, never()).search(any(SearchRequest.class), eq(ProductDoc.class));
    }

    private SearchQuery query(String keyword, int page, int size) {
        SearchQuery query = new SearchQuery();
        query.setKeyword(keyword);
        query.setPage(page);
        query.setSize(size);
        return query;
    }

    private Hit<ProductDoc> hit(String id, double score) {
        return Hit.of(h -> h.id(id).index("mall_product").score(score).source(doc(id)));
    }

    private ProductDoc doc(String id) {
        return ProductDoc.of(Long.valueOf(id), "商品 " + id, "品牌", 1001L, "卖点", "描述",
                new BigDecimal("199.00"), 10, 1, null, null);
    }

    @SafeVarargs
    private SearchResponse<ProductDoc> response(Hit<ProductDoc>... hits) {
        return SearchResponse.of(r -> r
                .took(1)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0))
                .hits(HitsMetadata.of(h -> h.hits(List.of(hits)))));
    }
}
