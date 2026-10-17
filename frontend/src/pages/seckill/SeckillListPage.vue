<script setup lang="ts">
import { computed } from 'vue';
import { useRouter } from 'vue-router';

import EmptyState from '@/components/EmptyState.vue';
import ErrorState from '@/components/ErrorState.vue';
import Pagination from '@/components/Pagination.vue';
import SkeletonBlock from '@/components/SkeletonBlock.vue';
import { listActivities, type SeckillActivity } from '@/api/seckill';
import { isApiError } from '@/api/types';
import SeckillActivityCard from '@/features/seckill/SeckillActivityCard.vue';
import { describeError } from '@/utils/errors';
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

const route = useRoute();
const router = useRouter();

const list = ref<SeckillActivity[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(12);
const runningOnly = ref(false);
const loading = ref(false);
const error = ref<Error | null>(null);
const errorCopy = computed(() => describeError(error.value));

let controller: AbortController | null = null;
let seq = 0;

function readQuery(): void {
  const rawPage = Number(route.query.page);
  page.value = Number.isInteger(rawPage) && rawPage >= 1 ? rawPage : 1;
  const rawSize = Number(route.query.size);
  size.value = Number.isInteger(rawSize) && rawSize >= 1 && rawSize <= 100 ? rawSize : 12;
  runningOnly.value = route.query.runningOnly === 'true';
}

function pushQuery(next: { page?: number; runningOnly?: boolean }): void {
  const query: Record<string, string> = {};
  const nextPage = next.page ?? page.value;
  const nextRunning = next.runningOnly ?? runningOnly.value;
  if (nextPage > 1) query.page = String(nextPage);
  if (size.value !== 12) query.size = String(size.value);
  if (nextRunning) query.runningOnly = 'true';
  void router.push({ name: 'seckill-list', query });
}

async function load(): Promise<void> {
  controller?.abort();
  controller = new AbortController();
  const current = ++seq;
  loading.value = true;
  error.value = null;

  try {
    const result = await listActivities(
      { page: page.value, size: size.value, runningOnly: runningOnly.value },
      controller.signal
    );
    if (current !== seq) return;
    list.value = result.list;
    total.value = result.total;
  } catch (caught) {
    if (current !== seq) return;
    if (isApiError(caught) && caught.kind === 'canceled') return;
    error.value = caught as Error;
  } finally {
    if (current === seq) loading.value = false;
  }
}

function toggleRunning(): void {
  pushQuery({ runningOnly: !runningOnly.value, page: 1 });
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
  <div class="page">
    <div class="container">
      <div class="page-head">
        <div>
          <p class="eyebrow">FLASH SALE</p>
          <h1 class="page-title">限时秒杀</h1>
          <p class="page-description">
            抢购请求受理后进入排队，结果由后端异步创建订单后返回。倒计时仅用于展示，不参与业务判定。
          </p>
        </div>
        <button
          class="button button--outline button--small"
          type="button"
          :aria-pressed="runningOnly ? 'true' : 'false'"
          @click="toggleRunning"
        >
          {{ runningOnly ? '只看进行中' : '显示全部活动' }}
        </button>
      </div>

      <div v-if="loading" class="activity-grid">
        <div v-for="index in 6" :key="index" class="card-skeleton">
          <SkeletonBlock width="30%" height="14px" />
          <SkeletonBlock height="20px" />
          <SkeletonBlock width="45%" height="18px" />
          <SkeletonBlock height="60px" />
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
        v-else-if="list.length === 0"
        :title="runningOnly ? '暂无进行中的活动' : '暂无秒杀活动'"
        :description="runningOnly ? '当前没有进行中的活动，可以关闭筛选查看全部。' : '活动创建后会显示在这里。'"
        :action-text="runningOnly ? '显示全部活动' : '刷新'"
        @action="runningOnly ? toggleRunning() : load()"
      />

      <template v-else>
        <div class="activity-grid">
          <SeckillActivityCard v-for="activity in list" :key="activity.id" :activity="activity" />
        </div>
        <Pagination
          :page="page"
          :size="size"
          :total="total"
          :size-options="[12, 24, 48]"
          :disabled="loading"
          @update:page="(next) => pushQuery({ page: next })"
          @update:size="
            (next) => {
              size = next;
              pushQuery({ page: 1 });
            }
          "
        />
      </template>
    </div>
  </div>
</template>

<style scoped>
.activity-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.card-skeleton {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: var(--radius-m);
  background: rgba(251, 248, 240, 0.58);
}

@media (max-width: 1100px) {
  .activity-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 600px) {
  .activity-grid {
    grid-template-columns: 1fr;
    gap: 10px;
  }
}
</style>
