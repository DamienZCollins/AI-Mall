<script setup lang="ts">
import { computed, ref } from 'vue';
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router';

import ConfirmDialog from '@/components/ConfirmDialog.vue';
import { useAuthStore } from '@/stores/auth';

const auth = useAuthStore();
const route = useRoute();
const router = useRouter();

const drawerOpen = ref(false);
const logoutOpen = ref(false);

const navItems = computed(() => [
  { label: '商品管理', to: { name: 'admin-products' }, match: ['admin-products', 'admin-product-create', 'admin-product-edit'] },
  { label: '秒杀活动', to: { name: 'admin-seckill' }, match: ['admin-seckill', 'admin-seckill-create', 'admin-seckill-edit'] },
  { label: '概览与维护', to: { name: 'admin-home' }, match: ['admin-home'] },
]);

interface Crumb {
  text: string;
  to?: { name: string };
}

const breadcrumb = computed<Crumb[]>(() => {
  const label = route.meta.title ?? '管理端';
  const root: Crumb = { text: '管理端', to: { name: 'admin-home' } };

  if (route.name === 'admin-home') {
    return [{ text: '管理端' }];
  }

  const name = String(route.name ?? '');
  if (name.startsWith('admin-product')) {
    return [root, { text: '商品管理', to: { name: 'admin-products' } }, { text: label }];
  }
  if (name.startsWith('admin-seckill')) {
    return [root, { text: '秒杀活动', to: { name: 'admin-seckill' } }, { text: label }];
  }
  return [root, { text: label }];
});

function isActive(match: string[]): boolean {
  return match.includes(String(route.name ?? ''));
}

function confirmLogout(): void {
  auth.logout();
  logoutOpen.value = false;
  void router.push({ name: 'login' });
}
</script>

<template>
  <div class="admin-shell">
    <aside class="admin-side" :class="{ 'is-open': drawerOpen }">
      <div class="admin-side__head">
        <RouterLink class="brand" :to="{ name: 'admin-home' }" aria-label="序集管理端首页">
          <span class="brand__zh">序集</span>
          <span class="brand__en">ADMIN</span>
        </RouterLink>
        <button class="admin-side__close" type="button" aria-label="关闭导航" @click="drawerOpen = false">
          <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
            <path d="m6 6 12 12M18 6 6 18" />
          </svg>
        </button>
      </div>

      <nav class="admin-nav" aria-label="管理端主导航">
        <RouterLink
          v-for="item in navItems"
          :key="item.label"
          class="admin-nav__item"
          :to="item.to"
          :aria-current="isActive(item.match) ? 'page' : undefined"
          @click="drawerOpen = false"
        >
          {{ item.label }}
        </RouterLink>
      </nav>

      <div class="admin-side__foot">
        <p class="admin-user">{{ auth.user?.nickname || auth.user?.username }}</p>
        <p class="admin-role">{{ auth.user?.role === 'ADMIN' ? '管理员' : '普通用户' }}</p>
        <RouterLink class="admin-link" :to="{ name: 'home' }">返回商城</RouterLink>
        <button class="admin-link" type="button" @click="logoutOpen = true">退出登录</button>
      </div>
    </aside>

    <div v-if="drawerOpen" class="admin-backdrop" @click="drawerOpen = false" />

    <div class="admin-main">
      <header class="admin-topbar">
        <button
          class="admin-menu"
          type="button"
          aria-label="打开导航"
          :aria-expanded="drawerOpen ? 'true' : 'false'"
          aria-controls="admin-side-nav"
          @click="drawerOpen = true"
        >
          <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M4 6h16M4 12h16M4 18h16" />
          </svg>
        </button>
        <nav class="admin-breadcrumb" aria-label="面包屑">
          <template v-for="(item, index) in breadcrumb" :key="index">
            <RouterLink v-if="item.to && index < breadcrumb.length - 1" :to="item.to">{{ item.text }}</RouterLink>
            <span v-else aria-current="page">{{ item.text }}</span>
            <span v-if="index < breadcrumb.length - 1" class="admin-breadcrumb__sep" aria-hidden="true">/</span>
          </template>
        </nav>
        <RouterLink class="text-link admin-back" :to="{ name: 'home' }">返回商城</RouterLink>
      </header>

      <main id="main-content" class="admin-content">
        <RouterView />
      </main>
    </div>

    <ConfirmDialog
      :open="logoutOpen"
      title="退出登录"
      description="退出后需要重新登录才能进入管理端。"
      confirm-text="退出登录"
      @confirm="confirmLogout"
      @cancel="logoutOpen = false"
    />
  </div>
</template>

<style scoped>
.admin-shell {
  display: flex;
  min-height: 100vh;
  background: var(--paper);
}

.admin-side {
  position: sticky;
  top: 0;
  display: flex;
  width: 232px;
  height: 100vh;
  flex: 0 0 232px;
  flex-direction: column;
  gap: 22px;
  padding: 22px 18px;
  border-right: 1px solid var(--line);
  background: var(--paper-light);
}

.admin-side__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.admin-side__close {
  display: none;
  width: 36px;
  height: 36px;
  place-items: center;
  border-radius: var(--radius-s);
  color: var(--ink-soft);
}

.admin-side__close .icon,
.admin-menu .icon {
  width: 18px;
  height: 18px;
}

.admin-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.admin-nav__item {
  display: flex;
  min-height: 42px;
  align-items: center;
  padding: 0 12px;
  border-radius: var(--radius-s);
  color: var(--ink-soft);
  font-size: 13px;
  transition:
    color var(--fast) ease,
    background var(--fast) ease;
}

.admin-nav__item:hover {
  color: var(--ink);
  background: rgba(29, 27, 23, 0.05);
}

.admin-nav__item[aria-current='page'] {
  color: var(--persimmon);
  background: rgba(189, 67, 40, 0.09);
  font-weight: 600;
}

.admin-side__foot {
  margin-top: auto;
  padding-top: 18px;
  border-top: 1px solid var(--line);
}

.admin-user {
  font-size: 13px;
  font-weight: 600;
}

.admin-role {
  color: var(--ink-muted);
  font-size: 11px;
}

.admin-link {
  display: inline-flex;
  min-height: 36px;
  align-items: center;
  color: var(--ink-soft);
  font-size: 12px;
}

.admin-link:hover {
  color: var(--persimmon);
}

.admin-main {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.admin-topbar {
  display: flex;
  min-height: 60px;
  align-items: center;
  gap: 14px;
  padding: 0 26px;
  border-bottom: 1px solid var(--line);
  background: rgba(251, 248, 240, 0.8);
}

.admin-menu {
  display: none;
  width: 36px;
  height: 36px;
  place-items: center;
  border-radius: var(--radius-s);
  color: var(--ink-soft);
}

.admin-breadcrumb {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  color: var(--ink-muted);
  font-size: 12px;
}

.admin-breadcrumb a:hover {
  color: var(--ink);
}

.admin-breadcrumb span[aria-current='page'] {
  color: var(--ink);
  font-weight: 600;
}

.admin-breadcrumb__sep {
  color: var(--line-strong);
}

.admin-back {
  margin-left: auto;
  font-size: 12px;
}

.admin-content {
  flex: 1;
  padding: 26px;
}

.admin-backdrop {
  display: none;
}

@media (max-width: 860px) {
  .admin-side {
    position: fixed;
    z-index: 95;
    top: 0;
    bottom: 0;
    left: 0;
    height: auto;
    transform: translateX(-100%);
    transition: transform var(--normal) var(--ease);
  }

  .admin-side.is-open {
    transform: none;
  }

  .admin-side__close {
    display: grid;
  }

  .admin-menu {
    display: grid;
  }

  .admin-backdrop {
    position: fixed;
    z-index: 90;
    inset: 0;
    display: block;
    background: rgba(29, 27, 23, 0.36);
  }

  .admin-topbar {
    padding: 0 16px;
  }

  .admin-content {
    padding: 18px 16px 40px;
  }
}
</style>
