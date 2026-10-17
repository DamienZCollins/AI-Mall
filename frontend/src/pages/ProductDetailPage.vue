<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import type { Product } from '@/api/product';
import { fetchProduct } from '@/features/catalog/source';
import { BIZ_CODE, isApiError } from '@/api/types';
import ErrorState from '@/components/ErrorState.vue';
import PriceText from '@/components/PriceText.vue';
import SkeletonBlock from '@/components/SkeletonBlock.vue';
import StatusTag from '@/components/StatusTag.vue';
import StockBadge from '@/components/StockBadge.vue';
import { describeError } from '@/utils/errors';
import { handleImageError, resolveImageUrl } from '@/utils/image';

const props = defineProps<{ id: string }>();
const router = useRouter();

const product = ref<Product | null>(null);
const loading = ref(true);
const error = ref<Error | null>(null);
const unavailable = ref(false);

const errorCopy = computed(() => describeError(error.value));
const stockText = computed(() => (product.value ? `${product.value.stock} 件可售` : '—'));

let controller: AbortController | null = null;

async function load(): Promise<void> {
  controller?.abort();
  controller = new AbortController();
  loading.value = true;
  error.value = null;
  unavailable.value = false;

  try {
    product.value = await fetchProduct(Number(props.id), controller.signal);
  } catch (caught) {
    if (isApiError(caught) && caught.kind === 'canceled') return;
    if (isApiError(caught) && caught.code === BIZ_CODE.PRODUCT_NOT_FOUND) {
      unavailable.value = true;
      product.value = null;
    } else {
      error.value = caught as Error;
    }
  } finally {
    loading.value = false;
  }
}

watch(() => props.id, load);
onMounted(load);
onBeforeUnmount(() => controller?.abort());
</script>

<template>
  <div class="page">
    <div class="container">
      <nav class="crumb" aria-label="面包屑">
        <RouterLink :to="{ name: 'products' }">全部商品</RouterLink>
        <span aria-hidden="true">/</span>
        <span aria-current="page">{{ product?.name ?? '商品详情' }}</span>
      </nav>

      <div v-if="loading" class="detail">
        <SkeletonBlock height="420px" radius="var(--radius-l)" />
        <div class="detail__info">
          <SkeletonBlock width="40%" height="12px" />
          <SkeletonBlock height="34px" />
          <SkeletonBlock width="70%" height="16px" />
          <SkeletonBlock width="30%" height="30px" />
          <SkeletonBlock height="90px" />
        </div>
      </div>

      <ErrorState
        v-else-if="unavailable"
        title="商品不可用"
        description="该商品不存在或已下架，无法继续查看。"
        retry-text="返回商品列表"
        @retry="router.push({ name: 'products' })"
      />

      <ErrorState
        v-else-if="error"
        :error="error as never"
        :title="errorCopy.title"
        :description="errorCopy.description"
        :show-retry="errorCopy.retryable"
        @retry="load"
      />

      <article v-else-if="product" class="detail">
        <div class="detail__media">
          <img
            :src="resolveImageUrl(product.imageUrl)"
            :alt="product.name"
            width="960"
            height="720"
            @error="handleImageError"
          />
        </div>

        <div class="detail__info">
          <div class="row">
            <StatusTag :status="product.status" />
            <StockBadge :stock="product.stock" />
            <span class="badge badge--muted">分类 ID {{ product.categoryId }}</span>
          </div>

          <h1 class="detail__title">{{ product.name }}</h1>
          <p v-if="product.sellingPoint" class="detail__selling">{{ product.sellingPoint }}</p>

          <div class="detail__price">
            <PriceText :value="product.price" size="l" />
            <span class="detail__stock">{{ stockText }}</span>
          </div>

          <dl class="detail__list">
            <div>
              <dt>商品 ID</dt>
              <dd>{{ product.id }}</dd>
            </div>
            <div>
              <dt>品牌</dt>
              <dd>{{ product.brand || '未提供' }}</dd>
            </div>
            <div>
              <dt>分类 ID</dt>
              <dd>{{ product.categoryId }}</dd>
            </div>
            <div>
              <dt>库存</dt>
              <dd>{{ product.stock }}</dd>
            </div>
            <div>
              <dt>状态</dt>
              <dd>{{ product.status === 1 ? '在售' : '已下架' }}</dd>
            </div>
          </dl>

          <section class="detail__block" aria-labelledby="detail-description-title">
            <h2 id="detail-description-title" class="detail__block-title">商品描述</h2>
            <p class="detail__description">{{ product.description || '该商品暂未提供描述。' }}</p>
          </section>

          <div class="detail__actions">
            <RouterLink class="button button--primary" :to="{ name: 'seckill-list' }">
              查看秒杀活动
              <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M5 12h14M13 6l6 6-6 6" />
              </svg>
            </RouterLink>
            <RouterLink class="button button--outline" :to="{ name: 'products' }">返回列表</RouterLink>
          </div>

          <p class="detail__note">
            秒杀活动与商品的关联以后端活动数据为准，本页不自行拼接秒杀价与库存。
          </p>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.crumb {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 22px;
  color: var(--ink-muted);
  font-size: 12px;
}

.crumb a:hover {
  color: var(--ink);
}

.crumb span[aria-current='page'] {
  color: var(--ink-soft);
}

.detail {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  gap: 44px;
  align-items: start;
}

.detail__media {
  overflow: hidden;
  aspect-ratio: 4 / 3;
  border: 1px solid var(--line);
  border-radius: var(--radius-l);
  background: var(--paper-deep);
}

.detail__media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.detail__info {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail__title {
  font-size: clamp(26px, 3.2vw, 38px);
  font-weight: 800;
  letter-spacing: -0.06em;
  line-height: 1.18;
}

.detail__selling {
  color: var(--ink-soft);
  font-size: 14px;
}

.detail__price {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 14px;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--line);
}

.detail__stock {
  color: var(--ink-muted);
  font-size: 12px;
}

.detail__list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 2px 20px;
}

.detail__list > div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 0;
  border-bottom: 1px solid var(--line);
  font-size: 12px;
}

.detail__list dt {
  color: var(--ink-muted);
}

.detail__list dd {
  margin: 0;
  font-family: var(--font-latin);
  text-align: right;
}

.detail__block-title {
  font-size: 13px;
  font-weight: 700;
}

.detail__description {
  margin-top: 8px;
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.85;
  white-space: pre-line;
}

.detail__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 6px;
}

.detail__note {
  color: var(--ink-muted);
  font-size: 11px;
  line-height: 1.7;
}

@media (max-width: 860px) {
  .detail {
    grid-template-columns: 1fr;
    gap: 24px;
  }
}

@media (max-width: 600px) {
  .detail__list {
    grid-template-columns: 1fr;
  }

  .detail__actions {
    flex-direction: column;
  }

  .detail__actions .button {
    width: 100%;
  }
}
</style>
