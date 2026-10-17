<script setup lang="ts">
import ProductCard from '@/components/ProductCard.vue';
import type { SearchHit } from '@/api/search';

defineProps<{
  hits: SearchHit[];
  total: number;
  keyword: string;
}>();

/** score 是 RRF 融合分，跨查询不可比；仅在开发环境或 URL 带调试开关时显示 */
const showDebug = import.meta.env.DEV || new URLSearchParams(window.location.search).has('debug');
</script>

<template>
  <section aria-labelledby="search-result-title">
    <h2 id="search-result-title" class="sr-only">搜索结果</h2>
    <p class="result-summary" role="status" aria-live="polite">
      关于「<strong>{{ keyword }}</strong>」共找到 <strong>{{ total }}</strong> 条结果，本页显示
      {{ hits.length }} 条。
    </p>
    <div class="result-grid">
      <ProductCard
        v-for="hit in hits"
        :key="hit.productId"
        :product="{
          id: hit.productId,
          name: hit.name,
          brand: hit.brand,
          sellingPoint: hit.sellingPoint,
          price: hit.price,
          stock: hit.stock,
          imageUrl: hit.imageUrl,
        }"
        :detail-id="hit.productId"
        :badge="showDebug ? (hit.hasVector === false ? '仅关键词召回' : undefined) : undefined"
      />
    </div>
    <p v-if="showDebug" class="debug-note">
      调试信息：score 为 RRF 融合分，跨查询不可比；「仅关键词召回」表示
      <code>hasVector === false</code>。
      <span v-for="hit in hits.slice(0, 3)" :key="`debug-${hit.productId}`" class="debug-item">
        #{{ hit.productId }} score={{ hit.score.toFixed(3) }}
      </span>
    </p>
  </section>
</template>

<style scoped>
.result-summary {
  margin-bottom: 18px;
  color: var(--ink-soft);
  font-size: 13px;
}

.result-summary strong {
  color: var(--ink);
}

.result-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.debug-note {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
  padding: 10px 12px;
  border: 1px dashed var(--line-strong);
  border-radius: var(--radius-s);
  color: var(--ink-muted);
  font-size: 11px;
}

.debug-item {
  font-family: var(--font-latin);
}

@media (max-width: 1100px) {
  .result-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 600px) {
  .result-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
  }
}
</style>
