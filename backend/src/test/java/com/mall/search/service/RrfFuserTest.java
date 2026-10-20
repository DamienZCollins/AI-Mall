package com.mall.search.service;

import com.mall.search.dto.RankedDoc;
import com.mall.search.entity.ProductDoc;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RrfFuserTest {

    private static final int K = RrfFuser.DEFAULT_RANK_CONSTANT;

    @Test
    @DisplayName("两路都命中的文档得分相加，名次与原始分无关")
    void sumsContributionsFromBothPaths() {
        List<RankedDoc> bm25 = List.of(doc("a", 99.0), doc("b", 1.0));
        List<RankedDoc> knn = List.of(doc("b", 0.1), doc("a", 0.9));

        List<RankedDoc> fused = RrfFuser.fuse(bm25, knn, K);

        // a: 1/61 + 1/62，b: 1/62 + 1/61，同分时保留先出现的顺序
        assertThat(fused).extracting(RankedDoc::id).containsExactly("a", "b");
        assertThat(fused.get(0).score()).isEqualTo(1.0 / (K + 1) + 1.0 / (K + 2));
        assertThat(fused.get(1).score()).isEqualTo(1.0 / (K + 2) + 1.0 / (K + 1));
    }

    @Test
    @DisplayName("只在单路出现的文档按另一路的缺失计分")
    void scoresSinglePathHits() {
        List<RankedDoc> fused = RrfFuser.fuse(List.of(doc("bm25-only", 50.0)), List.of(doc("knn-only", 0.3)), K);

        assertThat(fused).hasSize(2);
        // 两篇都是单路第一名，得分相同，稳定排序保留 BM25 路的顺序
        assertThat(fused).extracting(RankedDoc::id).containsExactly("bm25-only", "knn-only");
        assertThat(fused.get(0).score()).isEqualTo(1.0 / (K + 1));
        assertThat(fused.get(1).score()).isEqualTo(1.0 / (K + 1));
    }

    @Test
    @DisplayName("排名靠前的文档得分更高，且融合分随名次递减")
    void ranksHigherForEarlierPositions() {
        List<RankedDoc> fused = RrfFuser.fuse(
                List.of(doc("first", 10.0), doc("second", 20.0), doc("third", 30.0)),
                List.of(), K);

        assertThat(fused).extracting(RankedDoc::id).containsExactly("first", "second", "third");
        assertThat(fused.get(0).score()).isGreaterThan(fused.get(1).score());
        assertThat(fused.get(1).score()).isGreaterThan(fused.get(2).score());
    }

    @Test
    @DisplayName("两路交集文档的分值高于只在任何一路排第一的文档")
    void intersectionOutranksSinglePathLeader() {
        List<RankedDoc> bm25 = List.of(doc("solo", 100.0), doc("both", 1.0));
        List<RankedDoc> knn = List.of(doc("both", 0.99));

        List<RankedDoc> fused = RrfFuser.fuse(bm25, knn, K);

        assertThat(fused.get(0).id()).isEqualTo("both");
    }

    @Test
    @DisplayName("空输入返回空结果")
    void returnsEmptyForEmptyInputs() {
        assertThat(RrfFuser.fuse(List.of(), List.of(), K)).isEmpty();
    }

    @Test
    @DisplayName("同一 ID 在两路中出现时只保留一条文档")
    void keepsSingleEntryPerDocument() {
        ProductDoc fromBm25 = docBody("a");
        ProductDoc fromKnn = docBody("a");

        List<RankedDoc> fused = RrfFuser.fuse(
                List.of(new RankedDoc("a", fromBm25, 5.0)),
                List.of(new RankedDoc("a", fromKnn, 0.5)),
                K);

        assertThat(fused).hasSize(1);
        // 以先出现的一路为准，保留其文档内容
        assertThat(fused.get(0).doc()).isSameAs(fromBm25);
    }

    private static RankedDoc doc(String id, double score) {
        return new RankedDoc(id, docBody(id), score);
    }

    private static ProductDoc docBody(String id) {
        return ProductDoc.of(Long.valueOf(id.hashCode()), id, null, null, null, null,
                BigDecimal.ONE, 0, 1, null, null);
    }
}
