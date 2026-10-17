<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { PRODUCT_KEYWORD_MAX, PRODUCT_SIZE_MAX, queryProducts, type Product } from '@/api/product';
import EmptyState from '@/components/EmptyState.vue';
import ErrorState from '@/components/ErrorState.vue';
import Pagination from '@/components/Pagination.vue';
import ProductCard from '@/components/ProductCard.vue';
import SkeletonBlock from '@/components/SkeletonBlock.vue';
import { isApiError } from '@/api/types';
import { describeError } from '@/utils/errors';

const route = useRoute();
const router = useRouter();

const list = ref<Product[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(12);
const keyword = ref('');
const categoryId = ref<number | undefined>(undefined);
const loading = ref(false);
const error = ref<Error | null>(null);

let controller: AbortController | null = null;
let requestSeq = 0;

const errorCopy = computed(() => describeError(error.value));

function readQuery(): void {
  const rawPage = Number(route.query.page);
  page.value = Number.isInteger(rawPage) && rawPage >= 1 ? rawPage : 1;

  const rawSize = Number(route.query.size);
  size.value = Number.isInteger(rawSize) && rawSize >= 1 && rawSize <= PRODUCT_SIZE_MAX ? rawSize : 12;

  const rawKeyword = route.query.keyword;
  keyword.value = typeof rawKeyword === 'string' ? rawKeyword.slice(0, PRODUCT_KEYWORD_MAX) : '';

  const rawCategory = Number(route.query.categoryId);
  categoryId.value = Number.isInteger(rawCategory) && rawCategory > 0 ? rawCategory : undefined;
}

/** 条件与页码只写 URL，页面不再另存一份筛选状态 */
function pushQuery(next: { page?: number; size?: number; keyword?: string; categoryId?: number }): void {
  const query: Record<string, string> = {};
  const nextPage = next.page ?? page.value;
  const nextSize = next.size ?? size.value;
  const nextKeyword = next.keyword ?? keyword.value;
  const nextCategory = next.categoryId ?? categoryId.value;

  if (nextPage > 1) query.page = String(nextPage);
  if (nextSize !== 12) query.size = String(nextSize);
  if (nextKeyword) query.keyword = nextKeyword;
  if (nextCategory) query.categoryId = String(nextCategory);

  void router.push({ name: 'products', query });
}

async function load(): Promise<void> {
  controller?.abort();
  controller = new AbortController();
  const seq = ++requestSeq;

  loading.value = true;
  error.value = null;

  try {
    const result = await queryProducts(
      {
        page: page.value,
        size: size.value,
        keyword: keyword.value,
        categoryId: categoryId.value,
      },
      controller.signal
    );
    if (seq !== requestSeq) return;
    list.value = result.list.filter((item) => item.status === 1);
    total.value = result.total;
  } catch (caught) {
    if (seq !== requestSeq) return;
    if (isApiError(caught) && caught.kind === 'canceled') return;
    error.value = caught as Error;
  } finally {
    if (seq === requestSeq) {
      loading.value = false;
    }
  }
}

function submitKeyword(value: string): void {
  const trimmed = value.trim().slice(0, PRODUCT_KEYWORD_MAX);
  void router.push({
    name: 'products',
    query: {
      ...(trimmed ? { keyword: trimmed } : {}),
      ...(categoryId.value ? { categoryId: String(categoryId.value) } : {}),
      ...(size.value !== 12 ? { size: String(size.value) } : {}),
    },
  });
}

function clearFilters(): void {
  void router.push({ name: 'products' });
}

function changePage(next: number): void {
  pushQuery({ page: next });
}

function changeSize(next: number): void {
  // 切换每页数量时回到第 1 页
  void router.push({
    name: 'products',
    query: {
      ...(next !== 12 ? { size: String(next) } : {}),
      ...(keyword.value ? { keyword: keyword.value } : {}),
      ...(categoryId.value ? { categoryId: String(categoryId.value) } : {}),
    },
  });
}

watch(
  () => route.query,
  () => {
    readQuery();
    void load();
  }
);

onMounted(() => {
  readQuery();
  void load();
});

onBeforeUnmount(() => {
  controller?.abort();
});
</script>

<template>
  <div class="page">
    <div class="container">
      <div class="page-head">
        <div>
          <p class="eyebrow">ALL PRODUCTS</p>
          <h1 class="page-title">全部商品</h1>
          <p class="page-description">只展示已上架商品，支持关键字与分类 ID 筛选。</p>
        </div>
      </div>

      <form class="filters" role="search" @submit.prevent="submitKeyword(keyword)">
        <div class="field filters__keyword">
          <label for="product-keyword">关键字</label>
          <input
            id="product-keyword"
            v-model="keyword"
            class="input"
            type="search"
            name="keyword"
            :maxlength="PRODUCT_KEYWORD_MAX"
            placeholder="搜索商品名称或品牌"
          />
        </div>
        <div class="field filters__category">
          <label for="product-category">分类 ID</label>
          <input
            id="product-category"
            :value="categoryId ?? ''"
            class="input"
            type="number"
            min="1"
            placeholder="例如 1001"
            @change="
              (event) => {
                const value = Number((event.target as HTMLInputElement).value);
                categoryId = Number.isInteger(value) && value > 0 ? value : undefined;
              }
            "
          />
        </div>
        <div class="filters__actions">
          <button class="button button--primary button--small" type="submit">筛选</button>
          <button class="button button--outline button--small" type="button" @click="clearFilters">清空</button>
        </div>
      </form>

      <div v-if="loading" class="grid grid--4">
        <div v-for="index in size" :key="index" class="card-skeleton">
          <SkeletonBlock height="180px" radius="var(--radius-m) var(--radius-m) 0 0" />
          <div class="card-skeleton__body">
            <SkeletonBlock width="42%" height="10px" />
            <SkeletonBlock height="16px" />
            <SkeletonBlock width="30%" height="20px" />
          </div>
        </div>
      </div>

      <ErrorState
        v-else-if="error"
        :error="error as never"
        :title="errorCopy.title"
        :description="errorCopy.description"
        :show-retry="errorCopy.retryable"
        @retry="load"
      />

      <EmptyState
        v-else-if="total === 0"
        title="没有找到商品"
        description="换一个关键字或清空筛选条件再试试。"
        action-text="清空筛选"
        @action="clearFilters"
      />

      <template v-else>
        <div class="grid grid--4">
          <ProductCard v-for="product in list" :key="product.id" :product="product" />
        </div>
        <Pagination
          :page="page"
          :size="size"
          :total="total"
          :size-options="[12, 24, 48]"
          :disabled="loading"
          @update:page="changePage"
          @update:size="changeSize"
        />
      </template>
    </div>
  </div>
</template>

<style scoped>
.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 12px;
  margin-bottom: 24px;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: var(--radius-m);
  background: var(--paper-light);
}

.filters__keyword {
  min-width: 220px;
  flex: 1 1 260px;
}

.filters__category {
  width: 160px;
}

.filters__actions {
  display: flex;
  gap: 8px;
}

.grid {
  display: grid;
  gap: 16px;
}

.grid--4 {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.card-skeleton {
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: var(--radius-m);
  background: rgba(251, 248, 240, 0.58);
}

.card-skeleton__body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 17px;
}

@media (max-width: 1100px) {
  .grid--4 {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 860px) {
  .grid--4 {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .filters__category {
    width: 120px;
  }
}

@media (max-width: 600px) {
  .filters {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
    padding: 14px;
  }

  .filters__category {
    width: 100%;
  }

  .filters__actions {
    justify-content: flex-end;
  }

  .grid {
    gap: 10px;
  }
}
</style>
