<script setup lang="ts">
import { computed } from 'vue';
import { useRouter } from 'vue-router';

import type { SeckillResult } from '@/api/seckill';
import { formatDateTime } from '@/utils/datetime';
import type { PollingPhase } from '@/composables/useSeckillPolling';

const props = defineProps<{
  activityId: string;
  phase: PollingPhase;
  result: SeckillResult | null;
  waitedMs: number;
  errorMessage?: string;
}>();

const emit = defineEmits<{ requery: [] }>();
const router = useRouter();

const waitedText = computed(() => `${Math.max(0, Math.floor(props.waitedMs / 1000))} 秒`);

/** FAILED 兜底文案：从未参与也会拿到 FAILED，不能直接写「抢购失败」 */
const failedTitle = computed(() =>
  props.result?.productName ? '很遗憾，本次未抢到' : '未查询到抢购记录'
);

function goOrder(): void {
  if (props.result?.orderNo) {
    void router.push({ name: 'order-detail', params: { orderNo: props.result.orderNo } });
  }
}
</script>

<template>
  <div class="result">
    <div class="sr-only" role="status" aria-live="polite">
      <template v-if="phase === 'queuing' || phase === 'polling'">正在为你排队</template>
      <template v-else-if="phase === 'success'">抢购成功</template>
      <template v-else-if="phase === 'failed'">未抢到</template>
      <template v-else-if="phase === 'timeout'">仍在处理中</template>
      <template v-else-if="phase === 'error'">查询失败</template>
    </div>

    <template v-if="phase === 'queuing' || phase === 'polling'">
      <div class="result__panel">
        <span class="result__spinner" aria-hidden="true" />
        <h1 class="result__title">正在为你排队</h1>
        <p class="result__text">
          订单由后端异步创建，页面会持续查询结果。已等待 <strong>{{ waitedText }}</strong>。
        </p>
        <p class="result__note">请不要关闭页面，页面离开后轮询会自动停止。</p>
      </div>
    </template>

    <template v-else-if="phase === 'success'">
      <div class="result__panel result__panel--success">
        <span class="result__mark" aria-hidden="true">
          <svg class="icon" viewBox="0 0 24 24">
            <path d="m5 12 4 4L19 6" />
          </svg>
        </span>
        <h1 class="result__title">抢购成功</h1>
        <dl class="result__list">
          <div>
            <dt>订单号</dt>
            <dd>{{ result?.orderNo ?? '—' }}</dd>
          </div>
          <div>
            <dt>商品</dt>
            <dd>{{ result?.productName ?? '—' }}</dd>
          </div>
          <div>
            <dt>成交价</dt>
            <dd>¥{{ (result?.seckillPrice ?? 0).toFixed(2) }}</dd>
          </div>
          <div>
            <dt>支付截止</dt>
            <dd>{{ formatDateTime(result?.expireTime) }}</dd>
          </div>
        </dl>
        <p class="result__text">请在支付截止时间前完成支付，超时订单会被后端自动关闭。</p>
        <div class="result__actions">
          <button class="button button--primary" type="button" @click="goOrder">前往订单详情</button>
          <RouterLink class="button button--outline" :to="{ name: 'seckill-list' }">返回活动列表</RouterLink>
        </div>
      </div>
    </template>

    <template v-else-if="phase === 'failed'">
      <div class="result__panel result__panel--failed">
        <span class="result__mark result__mark--muted" aria-hidden="true">
          <svg class="icon" viewBox="0 0 24 24">
            <path d="m6 6 12 12M18 6 6 18" />
          </svg>
        </span>
        <h1 class="result__title">{{ failedTitle }}</h1>
        <p class="result__text">
          本次没有生成订单，可能是库存已抢完、已达限购，或你没有参与过本场活动。
        </p>
        <div class="result__actions">
          <RouterLink class="button button--primary" :to="`/seckill/${activityId}`">返回活动详情</RouterLink>
          <RouterLink class="button button--outline" :to="{ name: 'seckill-list' }">查看其他活动</RouterLink>
        </div>
      </div>
    </template>

    <template v-else-if="phase === 'timeout'">
      <div class="result__panel">
        <span class="result__mark result__mark--warn" aria-hidden="true">
          <svg class="icon" viewBox="0 0 24 24">
            <circle cx="12" cy="12" r="8.5" />
            <path d="M12 7.5V12l3 2" />
          </svg>
        </span>
        <h1 class="result__title">仍在处理中</h1>
        <p class="result__text">
          轮询已达到页面级超时，抢购结果可能还在后端处理中。超时≠失败，可以手动重新查询。
        </p>
        <div class="result__actions">
          <button class="button button--primary" type="button" @click="emit('requery')">重新查询</button>
          <RouterLink class="button button--outline" :to="`/seckill/${activityId}`">返回活动详情</RouterLink>
        </div>
      </div>
    </template>

    <template v-else-if="phase === 'error'">
      <div class="result__panel result__panel--failed">
        <h1 class="result__title">查询失败</h1>
        <p class="result__text">{{ errorMessage || '查询抢购结果时出错，请重试。' }}</p>
        <div class="result__actions">
          <button class="button button--primary" type="button" @click="emit('requery')">重新查询</button>
          <RouterLink class="button button--outline" :to="{ name: 'seckill-list' }">返回活动列表</RouterLink>
        </div>
      </div>
    </template>

    <template v-else>
      <div class="result__panel">
        <h1 class="result__title">正在确认抢购结果</h1>
        <p class="result__text">页面正在查询你在本场活动的抢购结果。</p>
        <div class="result__actions">
          <button class="button button--primary" type="button" @click="emit('requery')">立即查询</button>
          <RouterLink class="button button--outline" :to="`/seckill/${activityId}`">返回活动详情</RouterLink>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.result {
  display: flex;
  justify-content: center;
  padding: 24px 0 60px;
}

.result__panel {
  display: flex;
  width: min(100%, 560px);
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 44px 32px 36px;
  border: 1px solid var(--line);
  border-radius: var(--radius-l);
  background: var(--paper-light);
  text-align: center;
  box-shadow: 0 12px 40px rgba(29, 27, 23, 0.06);
}

.result__panel--success {
  border-color: rgba(127, 138, 117, 0.5);
}

.result__panel--failed {
  border-color: rgba(189, 67, 40, 0.26);
}

.result__spinner {
  width: 40px;
  height: 40px;
  border: 3px solid var(--paper-deep);
  border-top-color: var(--persimmon);
  border-radius: 50%;
  animation: spinnerRotate 900ms linear infinite;
}

.result__mark {
  display: grid;
  width: 52px;
  height: 52px;
  place-items: center;
  border-radius: 50%;
  color: var(--white);
  background: var(--sage);
}

.result__mark--muted {
  background: var(--ink-muted);
}

.result__mark--warn {
  background: var(--state-warning);
}

.result__mark .icon {
  width: 24px;
  height: 24px;
}

.result__title {
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -0.04em;
}

.result__text {
  max-width: 420px;
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.8;
}

.result__note {
  color: var(--ink-muted);
  font-size: 11px;
}

.result__list {
  width: 100%;
  margin-top: 6px;
}

.result__list > div {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 9px 0;
  border-bottom: 1px solid var(--line);
  font-size: 12px;
  text-align: left;
}

.result__list dt {
  color: var(--ink-muted);
}

.result__list dd {
  margin: 0;
  font-family: var(--font-latin);
  text-align: right;
}

.result__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  margin-top: 10px;
}

@media (max-width: 600px) {
  .result__panel {
    padding: 32px 18px 26px;
  }

  .result__actions {
    width: 100%;
    flex-direction: column;
  }

  .result__actions .button {
    width: 100%;
  }
}
</style>
