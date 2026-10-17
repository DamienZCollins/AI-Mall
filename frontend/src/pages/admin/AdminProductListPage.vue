<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { ADMIN_PRODUCT_KEYWORD_MAX, changeProductStatus, listAdminProducts, type AdminProductRow } from '@/api/admin';
import { BIZ_CODE, isApiError } from '@/api/types';
import ConfirmDialog from '@/components/ConfirmDialog.vue';
import EmptyState from '@/components/EmptyState.vue';
import ErrorState from '@/components/ErrorState.vue';
import Pagination from '@/components/Pagination.vue';
import SkeletonBlock from '@/components/SkeletonBlock.vue';
import StatusTag from '@/components/StatusTag.vue';
import { useToast } from '@/composables/useToast';
import { describeError } from '@/utils/errors';
import { handleImageError, resolveImageUrl } from '@/utils/image';
import { formatAmount } from '@/utils/datetime';

const route = useRoute();
const router = useRouter();
const toast = useToast();

const rows = ref<AdminProductRow[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(10);
const keyword = ref('');
const status = ref<0 | 1 | undefined>(undefined);
const loading = ref(false);
const error = ref<Error | null>(null);
const busyId = ref<number | null>(null);

const confirmTarget = ref<AdminProductRow | null>(null);
const errorCopy = computed(() => describeError(error.value));

let controller: AbortController | null = null;
let seq = 0;

function readQuery(): void {
  const rawPage = Number(route.query.page);
  page.value = Number.isInteger(rawPage) && rawPage >= 1 ? rawPage : 1;
  const rawSize = Number(route.query.size);
  size.value = Number.isInteger(rawSize) && rawSize >= 1 && rawSize <= 100 ? rawSize : 10;
  keyword.value = typeof route.query.keyword === 'string' ? route.query.keyword.slice(0, ADMIN_PRODUCT_KEYWORD_MAX) : '';
  const rawStatus = route.query.status;
  status.value = rawStatus === '0' ? 0 : rawStatus === '1' ? 1 : undefined;
}

function pushQuery(next: Partial<{ page: number; size: number; keyword: string; status: 0 | 1 | undefined }>): void {
  const nextPage = next.page ?? page.value;
  const nextSize = next.size ?? size.value;
  const nextKeyword = next.keyword ?? keyword.value;
  const nextStatus = 'status' in next ? next.status : status.value;

  const query: Record<string, string> = {};
  if (nextPage > 1) query.page = String(nextPage);
  if (nextSize !== 10) query.size = String(nextSize);
  if (nextKeyword) query.keyword = nextKeyword;
  if (nextStatus === 0 || nextStatus === 1) query.status = String(nextStatus);

  void router.push({ name: 'admin-products', query });
}

async function load(): Promise<void> {
  controller?.abort();
  controller = new AbortController();
  const current = ++seq;
  loading.value = true;
  error.value = null;

  try {
    const result = await listAdminProducts(
      { page: page.value, size: size.value, keyword: keyword.value, status: status.value },
      controller.signal
    );
    if (current !== seq) return;
    rows.value = result.list;
    total.value = result.total;
  } catch (caught) {
    if (current !== seq) return;
    if (isApiError(caught) && caught.kind === 'canceled') return;
    error.value = caught as Error;
  } finally {
    if (current === seq) loading.value = false;
  }
}

function askToggle(row: AdminProductRow): void {
  confirmTarget.value = row;
}

async function toggleStatus(): Promise<void> {
  const row = confirmTarget.value;
  if (!row) return;
  const next: 0 | 1 = row.status === 1 ? 0 : 1;
  confirmTarget.value = null;
  busyId.value = row.id;

  try {
    const updated = await changeProductStatus(row.id, next);
    // 只用响应替换该行，不重载整页、不重置筛选
    rows.value = rows.value.map((item) => (item.id === updated.id ? updated : item));
    toast.success(next === 1 ? '商品已上架' : '商品已下架', row.name);
  } catch (caught) {
    if (isApiError(caught) && caught.code === BIZ_CODE.PRODUCT_STATUS_INVALID) {
      toast.error('状态未改变', caught.message || '商品状态不合法，已刷新当前页');
      await load();
    } else if (isApiError(caught) && caught.code === BIZ_CODE.PRODUCT_NOT_FOUND) {
      toast.error('商品不存在', '该商品可能已被删除，已刷新当前页');
      await load();
    } else {
      toast.error('操作失败', isApiError(caught) ? caught.message : '请稍后重试');
    }
  } finally {
    busyId.value = null;
  }
}

function submitFilters(): void {
  pushQuery({ page: 1 });
}

function resetFilters(): void {
  void router.push({ name: 'admin-products' });
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

onBeforeUnmount(() => controller?.abort());
</script>

<template>
  <div class="admin-page">
    <div class="page-head">
      <div>
        <p class="eyebrow">PRODUCTS</p>
        <h1 class="page-title">商品管理</h1>
        <p class="page-description">新增商品默认下架，上架后用户侧列表与详情才可见。</p>
      </div>
      <RouterLink class="button button--primary button--small" :to="{ name: 'admin-product-create' }">
        新增商品
      </RouterLink>
    </div>

    <form class="admin-filters" role="search" @submit.prevent="submitFilters">
      <div class="field">
        <label for="admin-keyword">关键字</label>
        <input
          id="admin-keyword"
          v-model="keyword"
          class="input"
          type="search"
          :maxlength="ADMIN_PRODUCT_KEYWORD_MAX"
          placeholder="商品名称或品牌"
        />
      </div>
      <div class="field">
        <label for="admin-status">状态</label>
        <select id="admin-status" class="select" :value="status === undefined ? '' : String(status)" @change="(event) => { const value = (event.target as HTMLSelectElement).value; status = value === '0' ? 0 : value === '1' ? 1 : undefined; }">
          <option value="">全部</option>
          <option value="1">上架</option>
          <option value="0">下架</option>
        </select>
      </div>
      <div class="admin-filters__actions">
        <button class="button button--primary button--small" type="submit">筛选</button>
        <button class="button button--outline button--small" type="button" @click="resetFilters">重置</button>
      </div>
    </form>

    <div v-if="loading" class="panel admin-loading">
      <SkeletonBlock v-for="index in 6" :key="index" height="44px" />
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
      v-else-if="rows.length === 0"
      title="没有符合条件的商品"
      description="换一个关键字或状态筛选再试试。"
      action-text="重置筛选"
      @action="resetFilters"
    />

    <template v-else>
      <div class="panel table-wrap">
        <table class="admin-table">
          <caption class="sr-only">商品列表</caption>
          <thead>
            <tr>
              <th scope="col">ID</th>
              <th scope="col">图片</th>
              <th scope="col">名称</th>
              <th scope="col">品牌</th>
              <th scope="col">分类 ID</th>
              <th scope="col">售价</th>
              <th scope="col">库存</th>
              <th scope="col">状态</th>
              <th scope="col">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.id">
              <td class="cell-id">{{ row.id }}</td>
              <td>
                <span class="thumb">
                  <img
                    :src="resolveImageUrl(row.imageUrl)"
                    alt=""
                    width="56"
                    height="42"
                    loading="lazy"
                    @error="handleImageError"
                  />
                </span>
              </td>
              <td class="cell-name">{{ row.name }}</td>
              <td>{{ row.brand || '—' }}</td>
              <td class="cell-id">{{ row.categoryId }}</td>
              <td class="cell-id">¥{{ formatAmount(row.price) }}</td>
              <td class="cell-id">{{ row.stock }}</td>
              <td><StatusTag :status="row.status" /></td>
              <td>
                <div class="cell-actions">
                  <RouterLink
                    class="button button--outline button--small"
                    :to="{ name: 'admin-product-edit', params: { id: String(row.id) } }"
                  >
                    编辑
                  </RouterLink>
                  <button
                    class="button button--quiet button--small"
                    type="button"
                    :disabled="busyId === row.id"
                    :aria-label="`为「${row.name}」${row.status === 1 ? '下架' : '上架'}`"
                    @click="askToggle(row)"
                  >
                    {{ busyId === row.id ? '处理中…' : row.status === 1 ? '下架' : '上架' }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <Pagination
        :page="page"
        :size="size"
        :total="total"
        :size-options="[10, 20, 50]"
        :disabled="loading"
        @update:page="(next) => pushQuery({ page: next })"
        @update:size="(next) => pushQuery({ page: 1, size: next })"
      />
    </template>

    <ConfirmDialog
      :open="confirmTarget !== null"
      :title="confirmTarget?.status === 1 ? '确认下架商品' : '确认上架商品'"
      :description="confirmTarget ? `确定将「${confirmTarget.name}」${confirmTarget.status === 1 ? '下架' : '上架'}？` : ''"
      :confirm-text="confirmTarget?.status === 1 ? '下架' : '上架'"
      :busy="busyId !== null"
      @confirm="toggleStatus"
      @cancel="confirmTarget = null"
    />
  </div>
</template>

<style scoped>
.admin-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.admin-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid var(--line);
  border-radius: var(--radius-m);
  background: var(--paper-light);
}

.admin-filters .field {
  min-width: 160px;
  flex: 1 1 180px;
}

.admin-filters__actions {
  display: flex;
  gap: 8px;
}

.admin-loading {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 18px;
}

.table-wrap {
  overflow-x: auto;
}

.admin-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
  white-space: nowrap;
}

.admin-table th,
.admin-table td {
  padding: 11px 14px;
  border-bottom: 1px solid var(--line);
  text-align: left;
}

.admin-table th {
  color: var(--ink-muted);
  font-weight: 600;
  letter-spacing: 0.02em;
}

.admin-table tbody tr:hover {
  background: rgba(29, 27, 23, 0.025);
}

.cell-id {
  font-family: var(--font-latin);
}

.cell-name {
  max-width: 260px;
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.thumb {
  display: block;
  width: 56px;
  height: 42px;
  overflow: hidden;
  border-radius: 4px;
  background: var(--paper-deep);
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cell-actions {
  display: flex;
  gap: 6px;
}

@media (max-width: 600px) {
  .admin-filters {
    flex-direction: column;
    align-items: stretch;
  }

  .admin-filters__actions {
    justify-content: flex-end;
  }
}
</style>
