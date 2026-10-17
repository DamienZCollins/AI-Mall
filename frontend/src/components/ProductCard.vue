<script setup lang="ts">
import { computed } from 'vue';
import { RouterLink } from 'vue-router';

import PriceText from './PriceText.vue';
import StockBadge from './StockBadge.vue';
import { handleImageError, resolveImageUrl } from '@/utils/image';

interface CardModel {
  id: number;
  name: string;
  brand: string | null;
  sellingPoint: string | null;
  price: number;
  stock: number;
  imageUrl: string | null;
  /** 调试角标等附加信息，用户侧默认为空 */
  badge?: string;
}

const props = defineProps<{
  product: CardModel;
  /** 结果集使用 productId，商品集使用 id；由调用方传入跳转目标 */
  detailId?: number;
  badge?: string;
}>();

const targetId = computed(() => props.detailId ?? props.product.id);
const meta = computed(() => [props.product.brand, props.product.sellingPoint].filter(Boolean).join(' / '));
</script>

<template>
  <article class="product-card">
    <div class="product-card__media">
      <RouterLink :to="`/products/${targetId}`" tabindex="-1" aria-hidden="true">
        <img
          :src="resolveImageUrl(product.imageUrl)"
          :alt="product.name"
          width="720"
          height="540"
          loading="lazy"
          @error="handleImageError"
        />
      </RouterLink>
      <span v-if="badge ?? props.badge" class="product-tag">{{ badge ?? props.badge }}</span>
    </div>
    <div class="product-card__body">
      <p v-if="meta" class="product-meta">{{ meta }}</p>
      <h3 class="product-name">
        <RouterLink :to="`/products/${targetId}`">{{ product.name }}</RouterLink>
      </h3>
      <div class="price-row">
        <PriceText :value="product.price" />
      </div>
      <div class="product-card__foot">
        <StockBadge :stock="product.stock" />
        <RouterLink class="card-link" :to="`/products/${targetId}`" :aria-label="`查看 ${product.name} 详情`">
          查看详情
          <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M5 12h14M13 6l6 6-6 6" />
          </svg>
        </RouterLink>
      </div>
    </div>
  </article>
</template>

<style scoped>
.product-card {
  position: relative;
  display: flex;
  min-width: 0;
  flex-direction: column;
  border: 1px solid var(--line);
  border-radius: var(--radius-m);
  background: rgba(251, 248, 240, 0.58);
  transition:
    border-color var(--fast) ease,
    background var(--fast) ease;
}

.product-card:hover {
  border-color: rgba(29, 27, 23, 0.28);
  background: var(--paper-light);
}

.product-card__media {
  position: relative;
  overflow: hidden;
  aspect-ratio: 4 / 3;
  border-radius: calc(var(--radius-m) - 1px) calc(var(--radius-m) - 1px) 0 0;
  background: var(--paper-deep);
}

.product-card__media a {
  display: block;
  width: 100%;
  height: 100%;
}

.product-card__media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 620ms var(--ease);
}

.product-card:hover .product-card__media img {
  transform: scale(1.035);
}

.product-tag {
  position: absolute;
  z-index: 2;
  top: 13px;
  left: 13px;
  display: inline-flex;
  min-height: 25px;
  align-items: center;
  padding: 0 9px;
  border-radius: 4px;
  color: var(--white);
  background: var(--ink);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.06em;
  line-height: 1;
}

.product-card__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding: 17px 17px 15px;
}

.product-meta {
  color: var(--ink-muted);
  font-family: var(--font-latin);
  font-size: 9px;
  font-weight: 600;
  letter-spacing: 0.13em;
  line-height: 1.3;
  text-transform: uppercase;
}

.product-name {
  display: -webkit-box;
  min-height: 46px;
  margin-top: 6px;
  overflow: hidden;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: -0.02em;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
}

.price-row {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 7px;
  margin-top: 9px;
  font-family: var(--font-latin);
}

.product-card__foot {
  position: relative;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 14px;
  padding-top: 13px;
  border-top: 1px solid var(--line);
}

.card-link {
  display: inline-flex;
  min-height: 32px;
  align-items: center;
  gap: 5px;
  color: var(--ink-soft);
  font-size: 11px;
  font-weight: 600;
  transition: color var(--fast) ease;
}

.card-link:hover {
  color: var(--persimmon);
}

.card-link .icon {
  width: 13px;
  height: 13px;
}

@media (max-width: 600px) {
  .product-card {
    border-radius: 10px;
  }

  .product-card__media {
    border-radius: 9px 9px 0 0;
  }

  .product-card__body {
    padding: 12px 12px 11px;
  }

  .product-meta {
    font-size: 8px;
  }

  .product-name {
    min-height: 42px;
    margin-top: 4px;
    font-size: 13px;
    line-height: 1.6;
  }

  .price-row {
    gap: 5px;
    margin-top: 6px;
  }

  .product-card__foot {
    gap: 6px;
    margin-top: 8px;
    padding-top: 8px;
  }

  .product-tag {
    top: 9px;
    left: 9px;
    min-height: 22px;
    padding-inline: 7px;
    font-size: 9px;
  }
}
</style>
