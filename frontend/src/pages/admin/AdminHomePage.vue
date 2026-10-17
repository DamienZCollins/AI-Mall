<script setup lang="ts">
import { computed, ref } from 'vue';

import { fetchMe } from '@/api/auth';
import { BIZ_CODE, isApiError } from '@/api/types';
import { reindexSearch } from '@/api/admin';
import ConfirmDialog from '@/components/ConfirmDialog.vue';
import { useAuthStore } from '@/stores/auth';
import { useToast } from '@/composables/useToast';
import { onMounted } from 'vue';

const auth = useAuthStore();
const toast = useToast();

const reindexing = ref(false);
const reindexOpen = ref(false);
const reindexResult = ref<{ count: number } | null>(null);
const reindexError = ref('');

const roleLabel = computed(() => (auth.user?.role === 'ADMIN' ? '管理员' : '普通用户'));

async function loadProfile(): Promise<void> {
  try {
    const user = await fetchMe();
    auth.setUser(user);
  } catch {
    // 401 由 HTTP 客户端统一处理
  }
}

async function runReindex(): Promise<void> {
  if (reindexing.value) return;
  reindexOpen.value = false;
  reindexing.value = true;
  reindexResult.value = null;
  reindexError.value = '';

  try {
    const count = await reindexSearch();
    reindexResult.value = { count };
    toast.success('索引重建完成', `已重建 ${count} 条商品索引`);
  } catch (caught) {
    if (isApiError(caught) && caught.code === BIZ_CODE.SEARCH_UNAVAILABLE) {
      reindexError.value = '搜索服务暂不可用，请确认 Elasticsearch 后重试。';
    } else if (isApiError(caught) && caught.kind === 'timeout') {
      // 超时不等于失败：重建可能仍在后端执行
      reindexError.value = '请求超时。重建可能仍在后端执行，稍后用搜索验证结果。';
    } else if (isApiError(caught) && caught.kind === 'network') {
      reindexError.value = '网络异常，请求没有到达服务器，请检查网络后重试。';
    } else {
      reindexError.value = isApiError(caught) ? caught.message || '重建失败，请稍后重试。' : '重建失败，请稍后重试。';
    }
    toast.error('索引重建失败', reindexError.value);
  } finally {
    reindexing.value = false;
  }
}

onMounted(loadProfile);
</script>

<template>
  <div class="admin-page">
    <div class="page-head">
      <div>
        <p class="eyebrow">ADMIN CONSOLE</p>
        <h1 class="page-title">管理端概览</h1>
        <p class="page-description">维护商品与秒杀活动，并在这里执行索引重建等运维操作。</p>
      </div>
    </div>

    <section class="panel admin-card" aria-labelledby="admin-profile-title">
      <h2 id="admin-profile-title" class="admin-card__title">当前管理员</h2>
      <dl class="admin-kv">
        <div>
          <dt>账号</dt>
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
      </dl>
    </section>

    <div class="admin-grid">
      <section class="panel admin-card" aria-labelledby="admin-goods-title">
        <h2 id="admin-goods-title" class="admin-card__title">商品与活动</h2>
        <p class="admin-card__text">新增商品默认下架，需要上架后才会出现在用户侧列表与详情。</p>
        <div class="admin-links">
          <RouterLink class="admin-link-row" :to="{ name: 'admin-products' }">商品管理</RouterLink>
          <RouterLink class="admin-link-row" :to="{ name: 'admin-product-create' }">新增商品</RouterLink>
          <RouterLink class="admin-link-row" :to="{ name: 'admin-seckill' }">秒杀活动管理</RouterLink>
          <RouterLink class="admin-link-row" :to="{ name: 'admin-seckill-create' }">创建秒杀活动</RouterLink>
        </div>
      </section>

      <section class="panel admin-card" aria-labelledby="admin-reindex-title">
        <h2 id="admin-reindex-title" class="admin-card__title">系统维护</h2>
        <p class="admin-card__text">
          全量重建商品搜索索引。耗时与商品数量和向量化接口有关，重建期间搜索结果的完整性可能波动。
        </p>
        <button
          class="button button--outline button--small"
          type="button"
          :disabled="reindexing"
          @click="reindexOpen = true"
        >
          {{ reindexing ? '正在重建…' : '重建搜索索引' }}
        </button>
        <p v-if="reindexResult" class="admin-result" role="status">
          已重建 <strong>{{ reindexResult.count }}</strong> 条商品索引。
        </p>
        <p v-if="reindexError" class="admin-error" role="alert">{{ reindexError }}</p>
      </section>
    </div>

    <ConfirmDialog
      :open="reindexOpen"
      title="确认重建搜索索引"
      description="将对全部商品执行一次全量重建，期间搜索可能返回不完整结果。确认继续？"
      confirm-text="开始重建"
      :busy="reindexing"
      @confirm="runReindex"
      @cancel="reindexOpen = false"
    />
  </div>
</template>

<style scoped>
.admin-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.admin-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 22px;
}

.admin-card__title {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.admin-card__text {
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.75;
}

.admin-kv {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 34px;
}

.admin-kv > div {
  display: flex;
  gap: 10px;
  font-size: 12px;
}

.admin-kv dt {
  color: var(--ink-muted);
}

.admin-kv dd {
  margin: 0;
  font-family: var(--font-latin);
}

.admin-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  align-items: start;
}

.admin-links {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.admin-link-row {
  display: flex;
  min-height: 40px;
  align-items: center;
  padding: 0 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-s);
  color: var(--ink-soft);
  font-size: 12px;
  transition:
    border-color var(--fast) ease,
    color var(--fast) ease;
}

.admin-link-row:hover {
  border-color: var(--ink);
  color: var(--ink);
}

.admin-result {
  color: var(--ink-soft);
  font-size: 12px;
}

.admin-result strong {
  color: var(--ink);
  font-family: var(--font-latin);
}

.admin-error {
  padding: 9px 12px;
  border: 1px solid rgba(189, 67, 40, 0.3);
  border-radius: var(--radius-s);
  color: var(--persimmon-dark);
  background: rgba(189, 67, 40, 0.07);
  font-size: 12px;
}

@media (max-width: 860px) {
  .admin-grid {
    grid-template-columns: 1fr;
  }
}
</style>
