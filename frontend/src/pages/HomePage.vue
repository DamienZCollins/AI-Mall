<script setup lang="ts">
import { onMounted, ref } from 'vue';

import type { Product } from '@/api/product';
import { queryProducts } from '@/features/catalog/source';
import EmptyState from '@/components/EmptyState.vue';
import ErrorState from '@/components/ErrorState.vue';
import ProductCard from '@/components/ProductCard.vue';
import SkeletonBlock from '@/components/SkeletonBlock.vue';
import SearchInput from '@/components/SearchInput.vue';
import type { ApiError } from '@/api/types';
import { isApiError } from '@/api/types';
import { handleImageError } from '@/utils/image';

const weekly = ref<Product[]>([]);
const popular = ref<Product[]>([]);
const loading = ref(true);
const error = ref<ApiError | Error | null>(null);

const categories = [
  { label: '数码', to: '/products?categoryId=1001', img: '01_头戴式耳机_黑色黄底.jpg' },
  { label: '家居', to: '/products?categoryId=1002', img: '10_墨绿绒面双人沙发.jpg' },
  { label: '个护', to: '/products?categoryId=1003', img: '17_香氛蜡烛.jpg' },
  { label: '服饰箱包', to: '/products?categoryId=1004', img: '26_深蓝通勤双肩包.jpg' },
  { label: '食品饮品', to: '/products?categoryId=1005', img: '28_烘焙咖啡豆.jpg' },
  { label: '生活方式', to: '/products?categoryId=1006', img: '40_桌面多肉盆栽.jpg' },
];

async function load(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    const result = await queryProducts({ page: 1, size: 16 });
    const list = result.list;
    weekly.value = list.slice(0, 4);
    popular.value = list.slice(4, 12);
  } catch (caught) {
    if (isApiError(caught) && caught.kind === 'canceled') return;
    error.value = caught as Error;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="home">
    <section class="hero" aria-labelledby="hero-title">
      <div class="container">
        <div class="hero__frame">
          <div class="hero__image">
            <img
              src="/images/products/36_%E7%B1%B3%E7%99%BD%E5%AE%A2%E5%8E%85%E5%9C%BA%E6%99%AF.jpg"
              alt="自然光下的米白客厅，浅色沙发与木质边几围出安静的日常空间"
              width="1600"
              height="1280"
              fetchpriority="high"
              @error="handleImageError"
            />
            <div class="hero__issue" aria-hidden="true">
              <span>Seasonal Notes</span>
              <strong>Vol. 07</strong>
            </div>
            <p class="hero__image-caption">Living room / 09:26 AM / Hangzhou</p>
          </div>

          <div class="hero__type">
            <p class="eyebrow">XUJI EDIT · AUTUMN 2026</p>
            <h1 id="hero-title" class="hero__title">把喜欢的，<span>过成日常</span></h1>
            <p class="hero__subtitle">精选数码、家居、个护与服饰，为每一天挑一件真正愿意留下的好物。</p>
            <div class="hero__actions">
              <RouterLink class="button button--primary" :to="{ path: '/products' }">
                开始选购
                <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M5 12h14M13 6l6 6-6 6" />
                </svg>
              </RouterLink>
              <RouterLink class="button button--outline" :to="{ name: 'seckill-list' }">查看秒杀活动</RouterLink>
            </div>
          </div>

          <div class="hero__footnote">
            <span>
              <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
                <path d="m12 3 2.2 5.2L20 10l-5.8 1.8L12 17l-2.2-5.2L4 10l5.8-1.8Z" />
              </svg>
              本期编辑精选 {{ weekly.length + popular.length }} 件
            </span>
            <span class="hero__scroll">Scroll to browse</span>
          </div>
        </div>
      </div>
    </section>

    <section class="category-section" aria-label="分类快捷入口">
      <div class="container">
        <nav class="category-rail" aria-label="商品分类">
          <RouterLink
            v-for="item in categories"
            :key="item.label"
            class="category-link"
            :to="item.to"
          >
            <span class="category-link__icon">
              <img
                :src="`/images/products/${encodeURIComponent(item.img)}`"
                alt=""
                width="76"
                height="76"
                loading="lazy"
                @error="handleImageError"
              />
            </span>
            <span>{{ item.label }}</span>
          </RouterLink>
        </nav>
      </div>
    </section>

    <section id="weekly" class="section weekly" aria-labelledby="weekly-title">
      <div class="container">
        <div class="section-head">
          <div class="section-head__copy">
            <p class="eyebrow">WEEKLY EDIT</p>
            <h2 id="weekly-title" class="section-title">本周精选</h2>
            <p class="section-description">从触感、声音到气味，挑一些每天都会用到，也愿意多看一眼的东西。</p>
          </div>
          <RouterLink class="text-link" :to="{ name: 'products' }">
            查看全部
            <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
              <path d="M5 12h14M13 6l6 6-6 6" />
            </svg>
          </RouterLink>
        </div>

        <div v-if="loading" class="grid grid--4">
          <div v-for="index in 4" :key="index" class="card-skeleton">
            <SkeletonBlock height="180px" radius="var(--radius-m) var(--radius-m) 0 0" />
            <div class="card-skeleton__body">
              <SkeletonBlock width="42%" height="10px" />
              <SkeletonBlock height="16px" />
              <SkeletonBlock width="30%" height="20px" />
            </div>
          </div>
        </div>

        <ErrorState v-else-if="error" :error="error" title="精选商品加载失败" @retry="load" />

        <EmptyState
          v-else-if="weekly.length === 0"
          title="暂时没有上架商品"
          description="商品上架后会出现在这里。"
          action-text="前往商品列表"
          @action="$router.push('/products')"
        />

        <div v-else class="grid grid--4">
          <ProductCard v-for="product in weekly" :key="product.id" :product="product" />
        </div>
      </div>
    </section>

    <section id="popular" class="section" aria-labelledby="popular-title">
      <div class="container">
        <div class="section-head">
          <div class="section-head__copy">
            <p class="eyebrow">MOST LOVED</p>
            <h2 id="popular-title" class="section-title">人气好物</h2>
            <p class="section-description">真实库存与价格来自商品接口，点开即可看到完整信息。</p>
          </div>
        </div>

        <div v-if="loading" class="grid grid--4">
          <div v-for="index in 8" :key="index" class="card-skeleton">
            <SkeletonBlock height="180px" radius="var(--radius-m) var(--radius-m) 0 0" />
            <div class="card-skeleton__body">
              <SkeletonBlock width="42%" height="10px" />
              <SkeletonBlock height="16px" />
              <SkeletonBlock width="30%" height="20px" />
            </div>
          </div>
        </div>

        <ErrorState v-else-if="error" :error="error" title="商品加载失败" @retry="load" />

        <EmptyState
          v-else-if="popular.length === 0"
          title="暂时没有更多商品"
          description="换一个分类或关键字试试。"
          action-text="浏览全部商品"
          @action="$router.push('/products')"
        />

        <div v-else class="grid grid--4">
          <ProductCard v-for="product in popular" :key="product.id" :product="product" />
        </div>
      </div>
    </section>

    <section id="search-entry" class="search-entry" aria-labelledby="search-entry-title">
      <div class="container">
        <div class="search-entry__panel">
          <div>
            <p class="eyebrow">AI HYBRID SEARCH</p>
            <h2 id="search-entry-title" class="search-entry__title">用一句话，找到那件东西</h2>
            <p class="search-entry__description">
              关键词召回与语义向量召回融合排序，试试「夏天穿的透气鞋」这类自然语言描述。
            </p>
          </div>
          <div class="search-entry__form">
            <SearchInput input-id="home-search" placeholder="描述你想找的东西" />
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* ---------- Hero ---------- */
.hero {
  padding: 24px 0 26px;
}

.hero__frame {
  position: relative;
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  align-items: stretch;
  padding-bottom: 74px;
}

.hero__image {
  position: relative;
  grid-column: 2 / 12;
  grid-row: 1;
  min-height: 590px;
  overflow: hidden;
  border-radius: var(--radius-l);
  background: var(--paper-deep);
  isolation: isolate;
}

.hero__image::after {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(90deg, rgba(20, 18, 15, 0.18) 0%, transparent 42%),
    linear-gradient(0deg, rgba(20, 18, 15, 0.2) 0%, transparent 35%);
  content: '';
  pointer-events: none;
}

.hero__image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center;
  transition: transform 1200ms var(--ease);
}

.hero:hover .hero__image img {
  transform: scale(1.025);
}

.hero__issue {
  position: absolute;
  z-index: 2;
  top: 28px;
  right: 28px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  color: rgba(255, 253, 248, 0.84);
  font-family: var(--font-latin);
  font-size: 9px;
  letter-spacing: 0.19em;
  text-align: right;
  text-transform: uppercase;
}

.hero__issue strong {
  color: var(--white);
  font-size: 12px;
  font-weight: 600;
}

.hero__image-caption {
  position: absolute;
  z-index: 2;
  right: 28px;
  bottom: 24px;
  color: rgba(255, 253, 248, 0.72);
  font-family: var(--font-latin);
  font-size: 10px;
  letter-spacing: 0.1em;
}

.hero__type {
  position: relative;
  z-index: 3;
  grid-column: 1 / 7;
  grid-row: 1;
  align-self: center;
  margin-top: 108px;
  padding: 38px 34px 36px;
  border-radius: var(--radius-m);
  background: var(--paper-light);
  box-shadow: 0 12px 36px rgba(29, 27, 23, 0.08);
}

.hero__title {
  max-width: 570px;
  margin-top: 21px;
  font-size: clamp(56px, 6.35vw, 92px);
  font-weight: 900;
  letter-spacing: -0.09em;
  line-height: 0.99;
}

.hero__title span {
  display: block;
  color: var(--persimmon);
}

.hero__subtitle {
  max-width: 380px;
  margin-top: 22px;
  color: var(--ink-soft);
  font-size: 15px;
  line-height: 1.8;
}

.hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 28px;
}

.hero__footnote {
  position: absolute;
  z-index: 2;
  right: var(--gutter);
  bottom: 26px;
  left: var(--gutter);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  color: var(--ink-soft);
  font-size: 11px;
  letter-spacing: 0.04em;
}

.hero__footnote span {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.hero__footnote .icon {
  width: 14px;
  height: 14px;
  color: var(--persimmon);
}

.hero__scroll {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  font-family: var(--font-latin);
  font-size: 9px;
  letter-spacing: 0.15em;
  text-transform: uppercase;
}

.hero__scroll::before {
  width: 42px;
  height: 1px;
  background: var(--line-strong);
  content: '';
}

/* ---------- Category rail ---------- */
.category-section {
  padding: 0 0 84px;
}

.category-rail {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  overflow-x: auto;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  scrollbar-width: thin;
  overscroll-behavior-inline: contain;
}

.category-link {
  position: relative;
  display: flex;
  min-height: 106px;
  align-items: center;
  justify-content: center;
  gap: 14px;
  border-right: 1px solid var(--line);
  color: var(--ink-soft);
  font-size: 14px;
  font-weight: 500;
  transition:
    color var(--fast) ease,
    background var(--fast) ease;
}

.category-link:last-child {
  border-right: 0;
}

.category-link::after {
  position: absolute;
  right: 24px;
  bottom: -1px;
  left: 24px;
  height: 2px;
  background: var(--persimmon);
  content: '';
  transform: scaleX(0);
  transition: transform var(--fast) var(--ease);
}

.category-link:hover {
  color: var(--ink);
  background: rgba(255, 253, 248, 0.42);
}

.category-link:hover::after {
  transform: scaleX(1);
}

.category-link__icon {
  display: grid;
  width: 38px;
  height: 38px;
  flex: 0 0 38px;
  overflow: hidden;
  place-items: center;
  border-radius: 50%;
  color: var(--ink);
  background: var(--paper-deep);
  transition:
    color var(--fast) ease,
    background var(--fast) ease,
    transform var(--fast) var(--ease);
}

.category-link__icon img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.category-link:hover .category-link__icon {
  transform: scale(1.06);
}

.category-link .icon {
  width: 19px;
  height: 19px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.4;
}

/* ---------- Section heads ---------- */
.section {
  padding-bottom: 104px;
}

.section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 32px;
  margin-bottom: 30px;
}

.section-head__copy {
  max-width: 700px;
}

.section-title {
  margin-top: 10px;
  font-size: clamp(30px, 3.3vw, 44px);
  font-weight: 800;
  letter-spacing: -0.065em;
  line-height: 1.14;
}

.section-description {
  max-width: 460px;
  margin-top: 10px;
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.8;
}

/* ---------- Grid ---------- */
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

/* ---------- Search entry ---------- */
.search-entry {
  padding-bottom: 96px;
}

.search-entry__panel {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  align-items: center;
  gap: 40px;
  padding: 36px 40px;
  border: 1px solid var(--line);
  border-radius: var(--radius-l);
  background: var(--paper-light);
}

.search-entry__title {
  margin-top: 10px;
  font-size: clamp(24px, 2.6vw, 34px);
  font-weight: 800;
  letter-spacing: -0.055em;
}

.search-entry__description {
  max-width: 460px;
  margin-top: 10px;
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.8;
}

.search-entry__form :deep(.search-form) {
  max-width: none;
  margin-left: 0;
}

@media (max-width: 1280px) {
  .hero__type {
    grid-column: 1 / 7;
    padding-inline: 30px;
  }

  .hero__image {
    grid-column: 2 / 12;
  }
}

@media (max-width: 1100px) {
  .hero__frame {
    padding-bottom: 58px;
  }

  .hero__image {
    grid-column: 2 / 13;
    min-height: 530px;
  }

  .hero__type {
    grid-column: 1 / 8;
    margin-top: 92px;
  }

  .hero__title {
    font-size: clamp(50px, 7vw, 76px);
  }

  .grid--4 {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .search-entry__panel {
    grid-template-columns: 1fr;
    gap: 24px;
  }
}

@media (max-width: 860px) {
  .hero {
    padding-top: 16px;
  }

  .hero__frame {
    display: flex;
    flex-direction: column;
    padding-bottom: 58px;
  }

  .hero__image {
    order: 1;
    width: 100%;
    min-height: 0;
    aspect-ratio: 4 / 3;
  }

  .hero__type {
    order: 2;
    width: min(88%, 590px);
    margin: -74px 0 0;
    padding: 30px;
  }

  .hero__title {
    font-size: clamp(46px, 9.4vw, 70px);
  }

  .hero__subtitle {
    margin-top: 16px;
  }

  .hero__footnote {
    right: var(--gutter);
    bottom: 22px;
    left: var(--gutter);
  }

  .hero__footnote > span:first-child {
    display: none;
  }

  .category-section {
    padding-bottom: 66px;
  }

  .category-rail {
    grid-template-columns: repeat(6, 134px);
    margin-right: calc(var(--gutter) * -1);
    margin-left: calc(var(--gutter) * -1);
    padding-right: var(--gutter);
    padding-left: var(--gutter);
    border: 0;
  }

  .category-link {
    min-height: 88px;
    flex-direction: column;
    gap: 6px;
    border-top: 1px solid var(--line);
    border-bottom: 1px solid var(--line);
    font-size: 12px;
  }

  .category-link__icon {
    width: 32px;
    height: 32px;
    flex-basis: 32px;
  }

  .section {
    padding-bottom: 76px;
  }

  .grid--4 {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .search-entry__panel {
    padding: 28px 24px;
  }
}

@media (max-width: 600px) {
  .hero {
    padding-top: 10px;
  }

  .hero__frame {
    padding-bottom: 56px;
  }

  .hero__image {
    /* 竖屏下按图片自身比例收高，避免固定高度留出空白带 */
    min-height: 0;
    aspect-ratio: 4 / 3;
    border-radius: 12px;
  }

  .hero__image img {
    object-position: center;
  }

  .hero__issue,
  .hero__image-caption {
    display: none;
  }

  .hero__type {
    width: calc(100% - 16px);
    margin-top: -58px;
    padding: 23px 20px 21px;
    border-radius: 10px;
  }

  .hero__title {
    margin-top: 15px;
    font-size: clamp(42px, 12.1vw, 53px);
    letter-spacing: -0.095em;
    line-height: 1.03;
  }

  .hero__subtitle {
    max-width: 300px;
    margin-top: 14px;
    font-size: 12px;
    line-height: 1.75;
  }

  .hero__actions {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px;
    margin-top: 20px;
  }

  .hero__footnote {
    right: var(--gutter);
    bottom: 17px;
    left: var(--gutter);
  }

  .hero__scroll {
    gap: 8px;
    font-size: 8px;
  }

  .category-section {
    padding-bottom: 56px;
  }

  .category-rail {
    grid-template-columns: repeat(6, 92px);
    scrollbar-width: none;
  }

  .category-rail::-webkit-scrollbar {
    display: none;
  }

  .category-link {
    min-height: 78px;
    border-right: 0;
    font-size: 11px;
  }

  .category-link__icon {
    width: 29px;
    height: 29px;
    flex-basis: 29px;
  }

  .section {
    padding-bottom: 62px;
  }

  .section-head {
    align-items: flex-start;
    gap: 12px;
    margin-bottom: 20px;
  }

  .section-title {
    margin-top: 8px;
    font-size: 29px;
    letter-spacing: -0.06em;
  }

  .section-description {
    display: none;
  }

  .eyebrow {
    font-size: 9px;
  }

  .grid {
    gap: 10px;
  }

  .search-entry {
    padding-bottom: 56px;
  }

  .search-entry__panel {
    padding: 22px 18px;
  }
}

@media (max-width: 370px) {
  .hero__actions {
    grid-template-columns: 1fr;
  }

  .hero__title {
    font-size: 40px;
  }
}
</style>
