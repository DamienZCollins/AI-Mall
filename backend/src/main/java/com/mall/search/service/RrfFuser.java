package com.mall.search.service;

import com.mall.search.dto.RankedDoc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RRF 融合：score = Σ 1 / (k + rank)，k 取 60
 *
 * <p>Elasticsearch 的原生 rrf retriever 属于付费特性，basic 许可下返回 security_exception，
 * 因此融合改在应用层完成，两路召回仍由 ES 执行。
 */
public final class RrfFuser {

    /** 平滑常数，取值 60 来自 RRF 原论文的通用经验值 */
    public static final int DEFAULT_RANK_CONSTANT = 60;

    private RrfFuser() {
    }

    /**
     * 按名次而非原始分融合：原始分在 BM25 与余弦相似度之间不可比，名次才是可比的
     */
    public static List<RankedDoc> fuse(List<RankedDoc> bm25Hits, List<RankedDoc> knnHits, int rankConstant) {
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, RankedDoc> docs = new LinkedHashMap<>();

        accumulate(bm25Hits, rankConstant, scores, docs);
        accumulate(knnHits, rankConstant, scores, docs);

        List<RankedDoc> fused = new ArrayList<>(scores.size());
        for (Map.Entry<String, Double> entry : scores.entrySet()) {
            RankedDoc source = docs.get(entry.getKey());
            fused.add(new RankedDoc(source.id(), source.doc(), entry.getValue()));
        }
        fused.sort(Comparator.comparingDouble(RankedDoc::score).reversed());
        return fused;
    }

    private static void accumulate(List<RankedDoc> hits, int rankConstant,
                                   Map<String, Double> scores, Map<String, RankedDoc> docs) {
        for (int i = 0; i < hits.size(); i++) {
            RankedDoc hit = hits.get(i);
            double contribution = 1.0 / (rankConstant + i + 1);
            scores.merge(hit.id(), contribution, Double::sum);
            docs.putIfAbsent(hit.id(), hit);
        }
    }
}
