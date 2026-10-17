<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import type { SeckillResult } from '@/api/seckill';
import { getResult } from '@/api/seckill';
import { isApiError } from '@/api/types';
import { useSeckillPolling } from '@/composables/useSeckillPolling';
import SeckillResultState from '@/features/seckill/SeckillResultState.vue';

const props = defineProps<{ activityId: string }>();
const route = useRoute();

const result = ref<SeckillResult | null>(null);
const initializing = ref(true);

const { phase, orderNo, waitedMs, start, queryOnce, stop } = useSeckillPolling({
  activityId: Number(props.activityId),
  onState: () => {
    // 记录最近一次结果，供结果页展示商品与订单号
    void refreshSnapshot();
  },
});

async function refreshSnapshot(): Promise<void> {
  try {
    result.value = await getResult(Number(props.activityId));
    if (result.value.orderNo) {
      orderNo.value = result.value.orderNo;
    }
  } catch (caught) {
    if (isApiError(caught) && caught.kind === 'canceled') return;
  }
}

const accepted = computed(() => route.query.accepted === '1');

onMounted(async () => {
  initializing.value = true;
  await refreshSnapshot();
  initializing.value = false;

  // 带受理标记自动轮询；直接刷新该页只查询一次，不因刷新而空白
  if (accepted.value || result.value?.state === 'QUEUING') {
    start();
  } else if (!result.value) {
    void queryOnce();
  }
});

function requery(): void {
  void refreshSnapshot();
  start();
}

/** 页面离开时停止轮询 */
import { onBeforeUnmount } from 'vue';
onBeforeUnmount(stop);
</script>

<template>
  <div class="page page--tight">
    <div class="container">
      <nav class="crumb" aria-label="面包屑">
        <RouterLink :to="{ name: 'seckill-list' }">限时秒杀</RouterLink>
        <span aria-hidden="true">/</span>
        <RouterLink :to="`/seckill/${props.activityId}`">活动 {{ props.activityId }}</RouterLink>
        <span aria-hidden="true">/</span>
        <span aria-current="page">抢购结果</span>
      </nav>

      <SeckillResultState
        :activity-id="props.activityId"
        :phase="initializing ? 'idle' : phase"
        :result="result"
        :waited-ms="waitedMs"
        @requery="requery"
      />
    </div>
  </div>
</template>

<style scoped>
.crumb {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  color: var(--ink-muted);
  font-size: 12px;
}

.crumb a:hover {
  color: var(--ink);
}
</style>
