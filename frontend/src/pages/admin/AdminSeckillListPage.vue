<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { listAdminActivities, updateAdminActivity } from '@/api/admin';
import type { SeckillActivity } from '@/api/seckill';
import { isApiError } from '@/api/types';
import ConfirmDialog from '@/components/ConfirmDialog.vue';
import EmptyState from '@/components/EmptyState.vue';
import ErrorState from '@/components/ErrorState.vue';
import Pagination from '@/components/Pagination.vue';
import SkeletonBlock from '@/components/SkeletonBlock.vue';
import { useToast } from '@/composables/useToast';
import { activityStatusMeta } from '@/features/seckill/seckillLabels';
import { describeError } from '@/utils/errors';
import { formatDateTime } from '@/utils/datetime';

const route = useRoute();
const router = useRouter();
const toast = useToast();

const rows = ref<SeckillActivity[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(10);
const runningOnly = ref(false);
const loading = ref(false);
const error = ref<Error | null>(null);
const busyId = ref<number | null>(null);

const confirmTarget = ref<SeckillActivity | null>(null);
const errorCopy = computed(() => describeError(error.value));

let controller: AbortController | null = null;
let seq = 0;

function readQuery(): void {
  const rawPage = Number(route.query.page);
  page.value = Number.isInteger(rawPage) && rawPage >= 1 ? rawPage : 1;
  runningOnly.value = route.query.runningOnly === 'true';
}

function pushQuery(next: Partial<{ page: number; runningOnly: boolean }>): void {
  const query: Record<string, string> = {};
  const nextPage = next.page ?? page.value;
  const nextRunning = next.runningOnly ?? runningOnly.value;
  if (nextPage > 1) query.page = String(nextPage);
  if (nextRunning) query.runningOnly = 'true';
  void router.push({ name: 'admin-seckill', query });
}

async function load(): Promise<void> {
  controller?.abort();
  controller = new AbortController();
  const current = ++seq;
  loading.value = true;
  error.value = null;

  try {
    // 不传 runningOnly 才是全部
    const result = await listAdminActivities(
      { page: page.value, size: size.value, runningOnly: runningOnly.value },
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

/** 编辑接口的 status 是配置开关：1 启用 / 0 停用；列表的 2、3 不能回填 */
async function toggleEnabled(row: SeckillActivity): Promise<void> {
  confirmTarget.value = null;
  const currentlyEnabled = row.status !== 3;
  busyId.value = row.id;
  try {
    const updated = await updateAdminActivity(row.id, { status: currentlyEnabled ? 0 : 1 });
    rows.value = rows.value.map((item) => (item.id === updated.id ? updated : item));
    toast.success(currentlyEnabled ? '活动已停用' : '活动已启用', row.title);
  } catch (caught) {
    toast.error('操作失败', isApiError(caught) ? caught.message || '请稍后重试' : '请稍后重试');
  } finally {
    busyId.value = null;
  }
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
        <p class="eyebrow">SECKILL</p>
        <h1 class="page-title">秒杀活动管理</h1>
        <p class="page-description">
          列表同时包含未开始、进行中、已结束与已关闭的活动。创建后后端会建立库存行并触发 Redis 预热。
        </p>
      </div>
      <div class="row">
        <button
          class="button button--outline button--small"
          type="button"
          :aria-pressed="runningOnly ? 'true' : 'false'"
          @click="pushQuery({ runningOnly: !runningOnly, page: 1 })"
        >
          {{ runningOnly ? '只看进行中' : '显示全部活动' }}
        </button>
        <RouterLink class="button button--primary button--small" :to="{ name: 'admin-seckill-create' }">
          创建活动
        </RouterLink>
      </div>
    </div>

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
      title="暂无秒杀活动"
      :description="runningOnly ? '当前没有进行中的活动，可以显示全部查看。' : '创建活动后会显示在这里。'"
      :action-text="runningOnly ? '显示全部活动' : '创建活动'"
      @action="runningOnly ? pushQuery({ runningOnly: false, page: 1 }) : router.push({ name: 'admin-seckill-create' })"
    />

    <template v-else>
      <div class="panel table-wrap">
        <table class="admin-table">
          <caption class="sr-only">秒杀活动列表</caption>
          <thead>
            <tr>
              <th scope="col">ID</th>
              <th scope="col">标题</th>
              <th scope="col">商品 ID</th>
              <th scope="col">秒杀价</th>
              <th scope="col">原价</th>
              <th scope="col">剩余 / 总量</th>
              <th scope="col">开始时间</th>
              <th scope="col">结束时间</th>
              <th scope="col">状态</th>
              <th scope="col">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.id">
              <td class="cell-id">{{ row.id }}</td>
              <td class="cell-name">{{ row.title }}</td>
              <td class="cell-id">{{ row.productId }}</td>
              <td class="cell-id">¥{{ Number(row.seckillPrice).toFixed(2) }}</td>
              <td class="cell-id">¥{{ Number(row.originalPrice).toFixed(2) }}</td>
              <td class="cell-id">{{ row.remainingStock }} / {{ row.totalStock }}</td>
              <td class="cell-id">{{ formatDateTime(row.startTime) }}</td>
              <td class="cell-id">{{ formatDateTime(row.endTime) }}</td>
              <td>
                <span class="badge" :class="activityStatusMeta(row.status).badgeClass">
                  {{ activityStatusMeta(row.status).label }}
                </span>
              </td>
              <td>
                <div class="cell-actions">
                  <RouterLink
                    class="button button--outline button--small"
                    :to="{ name: 'admin-seckill-edit', params: { id: String(row.id) } }"
                  >
                    编辑
                  </RouterLink>
                  <button
                    class="button button--quiet button--small"
                    type="button"
                    :disabled="busyId === row.id"
                    :aria-label="`${row.status === 3 ? '启用' : '停用'}「${row.title}」`"
                    @click="confirmTarget = row"
                  >
                    {{ busyId === row.id ? '处理中…' : row.status === 3 ? '启用' : '停用' }}
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
        @update:size="() => pushQuery({ page: 1 })"
      />
    </template>

    <ConfirmDialog
      :open="confirmTarget !== null"
      :title="confirmTarget?.status === 3 ? '确认启用活动' : '确认停用活动'"
      :description="
        confirmTarget
          ? `确定${confirmTarget.status === 3 ? '启用' : '停用'}「${confirmTarget.title}」？${
              confirmTarget.status === 1
                ? '该活动正在进行中，停用会按数据库重建 Redis 状态，在途订单的预扣可能丢失。'
                : ''
            }`
          : ''
      "
      :confirm-text="confirmTarget?.status === 3 ? '启用' : '停用'"
      :busy="busyId !== null"
      tone="danger"
      @confirm="confirmTarget && toggleEnabled(confirmTarget)"
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
}

.admin-table tbody tr:hover {
  background: rgba(29, 27, 23, 0.025);
}

.cell-id {
  font-family: var(--font-latin);
}

.cell-name {
  max-width: 240px;
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-actions {
  display: flex;
  gap: 6px;
}
</style>
