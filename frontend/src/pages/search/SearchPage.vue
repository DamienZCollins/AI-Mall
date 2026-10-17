<script setup lang="ts">
import { computed } from 'vue';
import { useRouter } from 'vue-router';

import Pagination from '@/components/Pagination.vue';
import SearchInput from '@/components/SearchInput.vue';
import { useSearchQuery } from '@/features/search/useSearchQuery';
import SearchResultList from '@/features/search/SearchResultList.vue';
import SearchStatePanel from '@/features/search/SearchStatePanel.vue';

const router = useRouter();
const { keyword, page, size, hits, total, status, errorKind, errorMessage, run, submitKeyword, goToPage, changeSize } =
  useSearchQuery();

const showPagination = computed(() => (status.value === 'success' || status.value === 'empty') && total.value > 0);
/** page 越界：list 为空但 total > 0 */
const outOfRange = computed(() => status.value === 'empty' && total.value > 0);

function backHome(): void {
  void router.push({ name: 'home' });
}
</script>

<template>
  <div class="page">
    <div class="container">
      <div class="page-head">
        <div>
          <p class="eyebrow">AI HYBRID SEARCH</p>
          <h1 class="page-title">搜索结果</h1>
          <p class="page-description">BM25 关键词召回与向量语义召回融合排序，支持自然语言描述。</p>
        </div>
      </div>

      <div class="search-bar">
        <SearchInput :model-value="keyword" input-id="search-page-input" placeholder="试试「夏天穿的透气鞋」" @submit="submitKeyword" />
      </div>

      <section v-if="status !== 'idle'" class="search-body" aria-label="搜索结果区域">
        <SearchStatePanel
          v-if="status !== 'success'"
          :status="status"
          :error-kind="errorKind"
          :keyword="keyword"
          :error-message="errorMessage"
          @retry="run"
          @back-home="backHome"
        />
        <template v-else>
          <SearchResultList :hits="hits" :total="total" :keyword="keyword" />
        </template>

        <div v-if="outOfRange" class="out-of-range">
          <p>该页没有结果，可能页码超出了结果范围。</p>
          <button class="button button--outline button--small" type="button" @click="goToPage(1)">回到第 1 页</button>
        </div>

        <Pagination
          v-if="showPagination && !outOfRange"
          :page="page"
          :size="size"
          :total="total"
          :size-options="[10, 20, 50]"
          :disabled="status === 'loading'"
          @update:page="goToPage"
          @update:size="changeSize"
        />
      </section>

      <SearchStatePanel
        v-else
        :status="status"
        :error-kind="errorKind"
        :keyword="keyword"
        @retry="run"
        @back-home="backHome"
      />
    </div>
  </div>
</template>

<style scoped>
.search-bar {
  margin-bottom: 26px;
}

.search-bar :deep(.search-form) {
  max-width: none;
  height: 52px;
  margin-left: 0;
  background: var(--white);
}

.search-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.out-of-range {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 18px;
  padding: 14px 16px;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-s);
  background: var(--paper-light);
  color: var(--ink-soft);
  font-size: 12px;
}
</style>
