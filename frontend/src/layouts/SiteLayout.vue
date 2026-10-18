<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { RouterLink, RouterView, useRoute } from 'vue-router';

import SearchInput from '@/components/SearchInput.vue';
import { useAuthStore } from '@/stores/auth';

const auth = useAuthStore();
const route = useRoute();

const scrolled = ref(false);
const mobileSearchOpen = ref(false);
const mobileSearchTrigger = ref<HTMLElement | null>(null);
const dialogRef = ref<HTMLElement | null>(null);

/** 登录回跳只接受站内非登录路径，避免嵌套出 /login?redirect=/login?redirect=... */
function loginTarget(): { name: 'login'; query: { redirect?: string } } {
  const full = route.fullPath;
  if (route.name === 'login' || full.startsWith('/login')) {
    return { name: 'login', query: {} };
  }
  return { name: 'login', query: { redirect: full } };
}

const accountTarget = computed(() => (auth.isLoggedIn ? { name: 'me' as const } : loginTarget()));

const sectionIds = ['seckill', 'weekly', 'popular'] as const;
type HomeSection = (typeof sectionIds)[number];

const navItems: Array<{
  label: string;
  section: HomeSection;
  to: { name: 'home'; hash: string };
}> = [
  {
    label: '秒杀活动',
    section: 'seckill',
    to: { name: 'home', hash: '#seckill' },
  },
  {
    label: '本周精选',
    section: 'weekly',
    to: { name: 'home', hash: '#weekly' },
  },
  {
    label: '人气好物',
    section: 'popular',
    to: { name: 'home', hash: '#popular' },
  },
];

const activeSection = ref<HomeSection | null>(null);
let sectionFrame = 0;
let sectionObserver: ResizeObserver | null = null;

const mobileNav = computed(() => [
  {
    label: '首页',
    to: { name: 'home' },
    active: route.name === 'home',
    icon: 'home',
  },
  {
    label: '商品',
    to: { name: 'products' },
    active: route.name === 'products',
    icon: 'grid',
  },
  {
    label: '搜索',
    to: { name: 'search' },
    active: route.name === 'search',
    icon: 'search',
  },
  {
    label: '我的',
    to: auth.isLoggedIn ? { name: 'me' } : loginTarget(),
    active: route.name === 'me',
    icon: 'user',
  },
]);

function onScroll(): void {
  scrolled.value = window.scrollY > 8;
}

function isNavActive(item: (typeof navItems)[number]): boolean {
  if (route.name === 'home') {
    return activeSection.value === item.section;
  }
  if (item.section === 'seckill') {
    return ['seckill-list', 'seckill-detail', 'seckill-result'].includes(String(route.name));
  }
  return false;
}

function updateActiveSection(): void {
  if (route.name !== 'home') {
    activeSection.value = null;
    return;
  }

  const header = document.querySelector<HTMLElement>('.site-header');
  const headerBottom = header?.getBoundingClientRect().bottom ?? 0;
  const threshold = headerBottom + Math.min(160, window.innerHeight * 0.22);
  let current: HomeSection | null = null;

  for (const id of sectionIds) {
    const section = document.getElementById(id);
    if (section && section.getBoundingClientRect().top <= threshold) {
      current = id;
    }
  }

  activeSection.value = current;
}

function scheduleActiveSection(): void {
  if (sectionFrame) return;
  sectionFrame = window.requestAnimationFrame(() => {
    sectionFrame = 0;
    updateActiveSection();
  });
}

function openMobileSearch(event: Event): void {
  mobileSearchTrigger.value = event.currentTarget as HTMLElement;
  mobileSearchOpen.value = true;
}

function closeMobileSearch(): void {
  mobileSearchOpen.value = false;
}

function trapFocus(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    event.preventDefault();
    closeMobileSearch();
    return;
  }
  if (event.key !== 'Tab' || !dialogRef.value) return;

  const list = Array.from(
    dialogRef.value.querySelectorAll<HTMLElement>(
      'button:not([disabled]), [href], input:not([disabled]), [tabindex]:not([tabindex="-1"])',
    ),
  );
  if (list.length === 0) return;
  const first = list[0] as HTMLElement;
  const last = list[list.length - 1] as HTMLElement;
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}

watch(mobileSearchOpen, async (open) => {
  if (open) {
    document.body.classList.add('has-overlay');
    document.addEventListener('keydown', trapFocus, true);
    await nextTick();
    dialogRef.value?.querySelector<HTMLInputElement>('input')?.focus();
  } else {
    document.body.classList.remove('has-overlay');
    document.removeEventListener('keydown', trapFocus, true);
    mobileSearchTrigger.value?.focus({ preventScroll: true });
  }
});

// 路由变化后关闭弹层，避免弹层跨页残留
watch(
  () => route.fullPath,
  () => {
    closeMobileSearch();
    void nextTick(scheduleActiveSection);
  },
);

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true });
  window.addEventListener('scroll', scheduleActiveSection, { passive: true });
  window.addEventListener('resize', scheduleActiveSection);
  onScroll();
  scheduleActiveSection();

  const main = document.getElementById('main-content');
  if (main && 'ResizeObserver' in window) {
    sectionObserver = new ResizeObserver(scheduleActiveSection);
    sectionObserver.observe(main);
  }
});

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll);
  window.removeEventListener('scroll', scheduleActiveSection);
  window.removeEventListener('resize', scheduleActiveSection);
  if (sectionFrame) window.cancelAnimationFrame(sectionFrame);
  sectionObserver?.disconnect();
  document.removeEventListener('keydown', trapFocus, true);
  document.body.classList.remove('has-overlay');
});

const year = new Date().getFullYear();
</script>

<template>
  <a class="skip-link" href="#main-content">跳到主要内容</a>

  <header class="site-header" :class="{ 'is-scrolled': scrolled }">
    <div class="container">
      <div class="header-main">
        <RouterLink class="brand" :to="{ name: 'home' }" aria-label="序集 XUJI 首页">
          <span class="brand__zh">序集</span>
          <span class="brand__en">XUJI</span>
        </RouterLink>

        <nav class="primary-nav" aria-label="主导航">
          <RouterLink
            v-for="item in navItems"
            :key="item.label"
            custom
            :to="item.to"
            v-slot="{ href, navigate }"
          >
            <a :href="href" :aria-current="isNavActive(item) ? 'location' : undefined" @click="navigate">
              {{ item.label }}
            </a>
          </RouterLink>
        </nav>

        <SearchInput :model-value="(route.query.keyword as string) ?? ''" />

        <div class="header-actions">
          <RouterLink
            class="icon-button is-account"
            :to="accountTarget"
            :aria-label="auth.isLoggedIn ? '我的账户' : '登录'"
          >
            <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="12" cy="8" r="3.4" />
              <path d="M5.2 20c.6-4 3-6 6.8-6s6.2 2 6.8 6" />
            </svg>
            <span class="action-label">{{ auth.isLoggedIn ? '账户' : '登录' }}</span>
          </RouterLink>
          <RouterLink
            v-if="auth.isAdmin"
            class="icon-button"
            :to="{ name: 'admin-home' }"
            aria-label="管理端"
          >
            <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
              <path d="M4 5h7v7H4zM13 5h7v7h-7zM4 14h7v7H4zM13 14h7v7h-7z" />
            </svg>
          </RouterLink>
          <div class="mobile-search">
            <button
              class="search-submit"
              type="button"
              aria-label="打开搜索"
              :aria-expanded="mobileSearchOpen ? 'true' : 'false'"
              @click="openMobileSearch"
            >
              <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
                <circle cx="11" cy="11" r="6.5" />
                <path d="m16 16 4 4" />
              </svg>
            </button>
          </div>
        </div>
      </div>
    </div>
  </header>

  <main id="main-content">
    <RouterView />
  </main>

  <footer class="site-footer">
    <div class="container footer-main">
      <div class="footer-brand">
        <RouterLink class="brand" :to="{ name: 'home' }" aria-label="返回序集首页">
          <span class="brand__zh">序集</span>
          <span class="brand__en">XUJI</span>
        </RouterLink>
        <p>为日常挑选经得起使用的东西。<br />Less, but better lived.</p>
      </div>
      <nav class="footer-links" aria-label="页脚导航">
        <div class="footer-link-row">
          <h3>逛一逛</h3>
          <ul>
            <li>
              <RouterLink :to="{ name: 'home', hash: '#seckill' }">限时秒杀</RouterLink>
            </li>
            <li>
              <RouterLink :to="{ name: 'products' }">全部商品</RouterLink>
            </li>
            <li>
              <RouterLink :to="{ name: 'home', hash: '#weekly' }">本周精选</RouterLink>
            </li>
          </ul>
        </div>
        <div class="footer-link-row">
          <h3>账户与服务</h3>
          <ul>
            <li>
              <RouterLink :to="auth.isLoggedIn ? { name: 'me' } : loginTarget()">
                {{ auth.isLoggedIn ? '账户信息' : '登录 / 注册' }}
              </RouterLink>
            </li>
            <li><span>配送范围</span></li>
            <li><span>退换货政策</span></li>
            <li><span>尺码与保养</span></li>
          </ul>
        </div>
      </nav>
    </div>
    <div class="container footer-bottom">
      <p>© {{ year }} 序集 XUJI. 保留所有权利。</p>
    </div>
  </footer>

  <nav class="mobile-nav" aria-label="移动端底部导航">
    <RouterLink
      v-for="item in mobileNav"
      :key="item.label"
      class="mobile-nav__item"
      :to="item.to"
      :aria-current="item.active ? 'page' : undefined"
    >
      <svg v-if="item.icon === 'home'" class="icon" viewBox="0 0 24 24" aria-hidden="true">
        <path d="M4 11.2 12 4l8 7.2V20a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1z" />
        <path d="M9 21v-6h6v6" />
      </svg>
      <svg v-else-if="item.icon === 'grid'" class="icon" viewBox="0 0 24 24" aria-hidden="true">
        <path d="M4 5h7v7H4zM13 5h7v7h-7zM4 14h7v7H4zM13 14h7v7h-7z" />
      </svg>
      <svg v-else-if="item.icon === 'search'" class="icon" viewBox="0 0 24 24" aria-hidden="true">
        <circle cx="11" cy="11" r="6.5" />
        <path d="m16 16 4 4" />
      </svg>
      <svg v-else class="icon" viewBox="0 0 24 24" aria-hidden="true">
        <circle cx="12" cy="8" r="3.4" />
        <path d="M5.2 20c.6-4 3-6 6.8-6s6.2 2 6.8 6" />
      </svg>
      <span>{{ item.label }}</span>
    </RouterLink>
  </nav>

  <div
    v-if="mobileSearchOpen"
    ref="dialogRef"
    class="search-dialog is-open"
    role="dialog"
    aria-modal="true"
    aria-labelledby="mobile-search-title"
    @click.self="closeMobileSearch"
  >
    <div class="search-dialog__sheet">
      <div class="search-dialog__top">
        <p id="mobile-search-title">搜索序集</p>
        <button class="search-dialog__close" type="button" aria-label="关闭搜索" @click="closeMobileSearch">
          <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
            <path d="m6 6 12 12M18 6 6 18" />
          </svg>
        </button>
      </div>
      <SearchInput
        input-id="mobile-global-search"
        placeholder="搜索数码、家居与生活好物"
        autofocus
        @submit="closeMobileSearch"
      />
      <div class="search-dialog__quick" aria-label="热门搜索">
        <RouterLink
          v-for="word in ['香氛', '原木家具', '桌面音箱', '护肤精华']"
          :key="word"
          class="search-quick-button"
          :to="{ name: 'search', query: { keyword: word } }"
          @click="closeMobileSearch"
        >
          {{ word }}
        </RouterLink>
      </div>
    </div>
  </div>
</template>

<style scoped>
.search-dialog {
  position: fixed;
  z-index: 90;
  inset: 0;
  display: flex;
  align-items: flex-start;
  padding: max(20px, env(safe-area-inset-top)) 20px 20px;
  background: rgba(29, 27, 23, 0.38);
  -webkit-backdrop-filter: blur(4px);
  backdrop-filter: blur(4px);
}

.search-dialog.is-open {
  animation: fadeIn 180ms ease forwards;
}

.search-dialog__sheet {
  width: min(100%, 620px);
  margin-inline: auto;
  padding: 12px;
  border-radius: var(--radius-m);
  background: var(--paper-light);
  box-shadow: 0 22px 60px rgba(20, 18, 15, 0.22);
  animation: fadeSlideIn 220ms var(--ease);
}

.search-dialog__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 4px 6px 10px;
}

.search-dialog__top p {
  color: var(--ink-muted);
  font-size: 10px;
  letter-spacing: 0.09em;
}

.search-dialog__close {
  display: grid;
  width: 44px;
  height: 44px;
  place-items: center;
  margin: -4px -4px -4px 0;
  border-radius: var(--radius-s);
  color: var(--ink-soft);
}

.search-dialog__close:hover {
  color: var(--ink);
  background: rgba(29, 27, 23, 0.06);
}

.search-dialog__close .icon {
  width: 18px;
  height: 18px;
}

.search-dialog :deep(.search-form) {
  width: 100%;
  max-width: none;
  height: 48px;
  margin: 0;
  border-color: var(--line-strong);
  background: var(--white);
}

.search-dialog__quick {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 16px 4px 4px;
}

.search-quick-button {
  display: inline-flex;
  min-height: 38px;
  align-items: center;
  padding: 0 12px;
  border: 1px solid var(--line);
  border-radius: 6px;
  color: var(--ink-soft);
  font-size: 11px;
  transition:
    color var(--fast) ease,
    border-color var(--fast) ease;
}

.search-quick-button:hover {
  border-color: var(--ink);
  color: var(--ink);
}

@media (min-width: 601px) {
  .search-dialog {
    display: none !important;
  }
}
</style>
