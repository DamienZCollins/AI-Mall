<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { fetchMe } from '@/api/auth';
import { BIZ_CODE, isApiError } from '@/api/types';
import ConfirmDialog from '@/components/ConfirmDialog.vue';
import ErrorState from '@/components/ErrorState.vue';
import SkeletonBlock from '@/components/SkeletonBlock.vue';
import { useAuthStore } from '@/stores/auth';
import { describeError } from '@/utils/errors';

const auth = useAuthStore();
const router = useRouter();

const loading = ref(true);
const error = ref<Error | null>(null);
const logoutOpen = ref(false);

const roleLabel = computed(() => {
  if (auth.user?.role === 'ADMIN') return '管理员';
  if (auth.user?.role === 'USER') return '普通用户';
  return '—';
});

const errorCopy = computed(() => describeError(error.value));

async function load(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    const user = await fetchMe();
    auth.setUser(user);
  } catch (caught) {
    if (isApiError(caught) && caught.kind === 'canceled') return;
    if (isApiError(caught) && caught.code === BIZ_CODE.USER_NOT_FOUND) {
      auth.logout();
      await router.replace({ name: 'login', query: { redirect: '/me' } });
      return;
    }
    error.value = caught as Error;
  } finally {
    loading.value = false;
  }
}

function doLogout(): void {
  logoutOpen.value = false;
  auth.logout();
  void router.push({ name: 'home' });
}

onMounted(() => {
  if (auth.user) {
    loading.value = false;
    void fetchMe()
      .then((user) => auth.setUser(user))
      .catch(() => undefined);
    return;
  }
  void load();
});
</script>

<template>
  <div class="page">
    <div class="container">
      <div class="page-head">
        <div>
          <p class="eyebrow">MY ACCOUNT</p>
          <h1 class="page-title">当前用户</h1>
          <p class="page-description">登录态保存在本地，刷新页面后仍可继续使用。</p>
        </div>
        <div class="row">
          <RouterLink class="button button--outline button--small" :to="{ name: 'home' }">返回首页</RouterLink>
          <button class="button button--primary button--small" type="button" @click="logoutOpen = true">
            退出登录
          </button>
        </div>
      </div>

      <div v-if="loading" class="panel me-panel">
        <SkeletonBlock width="120px" height="14px" />
        <SkeletonBlock width="60%" height="26px" />
        <SkeletonBlock width="40%" height="14px" />
      </div>

      <ErrorState
        v-else-if="error"
        :error="error as never"
        :title="errorCopy.title"
        :description="errorCopy.description"
        :show-retry="errorCopy.retryable"
        @retry="load"
      />

      <div v-else class="me-grid">
        <section class="panel me-panel" aria-labelledby="profile-title">
          <h2 id="profile-title" class="me-panel__title">账户信息</h2>
          <dl class="me-list">
            <div>
              <dt>用户 ID</dt>
              <dd>{{ auth.user?.id ?? '—' }}</dd>
            </div>
            <div>
              <dt>用户名</dt>
              <dd>{{ auth.user?.username ?? '—' }}</dd>
            </div>
            <div>
              <dt>昵称</dt>
              <dd>{{ auth.user?.nickname || '—' }}</dd>
            </div>
            <div>
              <dt>角色</dt>
              <dd>{{ roleLabel }}</dd>
            </div>
            <div>
              <dt>登录有效期至</dt>
              <dd>{{ auth.expiresAt ? new Date(auth.expiresAt).toLocaleString('zh-CN') : '—' }}</dd>
            </div>
          </dl>
        </section>

        <section class="panel me-panel" aria-labelledby="actions-title">
          <h2 id="actions-title" class="me-panel__title">快捷入口</h2>
          <div class="me-actions">
            <RouterLink class="me-action" :to="{ name: 'products' }">
              <span>浏览商品</span>
              <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M5 12h14M13 6l6 6-6 6" />
              </svg>
            </RouterLink>
            <RouterLink class="me-action" :to="{ name: 'seckill-list' }">
              <span>秒杀活动</span>
              <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M5 12h14M13 6l6 6-6 6" />
              </svg>
            </RouterLink>
            <RouterLink v-if="auth.isAdmin" class="me-action" :to="{ name: 'admin-home' }">
              <span>进入管理端</span>
              <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M5 12h14M13 6l6 6-6 6" />
              </svg>
            </RouterLink>
            <p class="me-note">
              当前后端仅提供秒杀订单接口，订单列表、地址与收藏等能力暂未开放。
            </p>
          </div>
        </section>
      </div>
    </div>

    <ConfirmDialog
      :open="logoutOpen"
      title="确认退出登录"
      description="退出后需要重新登录才能参与秒杀与查看订单。"
      confirm-text="退出登录"
      @confirm="doLogout"
      @cancel="logoutOpen = false"
    />
  </div>
</template>

<style scoped>
.me-grid {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  gap: 18px;
  align-items: start;
}

.me-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 24px;
}

.me-panel__title {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.me-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.me-list > div {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 11px 0;
  border-bottom: 1px solid var(--line);
  font-size: 13px;
}

.me-list > div:last-child {
  border-bottom: 0;
}

.me-list dt {
  color: var(--ink-muted);
}

.me-list dd {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-latin);
  text-align: right;
}

.me-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.me-action {
  display: flex;
  min-height: 46px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius-s);
  color: var(--ink-soft);
  font-size: 13px;
  transition:
    border-color var(--fast) ease,
    color var(--fast) ease;
}

.me-action:hover {
  border-color: var(--ink);
  color: var(--ink);
}

.me-action .icon {
  width: 15px;
  height: 15px;
}

.me-note {
  margin-top: 4px;
  color: var(--ink-muted);
  font-size: 11px;
  line-height: 1.7;
}

@media (max-width: 860px) {
  .me-grid {
    grid-template-columns: 1fr;
  }
}
</style>
