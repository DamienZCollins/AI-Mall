<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { getOrder, payOrder, type SeckillOrder } from '@/api/seckill';
import { BIZ_CODE, isApiError } from '@/api/types';
import ErrorState from '@/components/ErrorState.vue';
import PriceText from '@/components/PriceText.vue';
import SkeletonBlock from '@/components/SkeletonBlock.vue';
import { useCountdown } from '@/composables/useCountdown';
import { useToast } from '@/composables/useToast';
import { orderStatusMeta } from '@/features/seckill/seckillLabels';
import { describeError } from '@/utils/errors';
import { formatDateTime } from '@/utils/datetime';

const props = defineProps<{ orderNo: string }>();
const router = useRouter();
const toast = useToast();

const order = ref<SeckillOrder | null>(null);
const loading = ref(true);
const error = ref<Error | null>(null);
const notFound = ref(false);
const paying = ref(false);
const payError = ref('');
/** 倒计时到期后禁用支付；后端关单有延迟，仍需提示状态更新中 */
const payExpired = ref(false);

const errorCopy = computed(() => describeError(error.value));
const statusMeta = computed(() => (order.value ? orderStatusMeta(order.value.status) : null));
const isPending = computed(() => order.value?.status === 1 && !payExpired.value);

const countdownTarget = computed(() => (order.value?.status === 1 ? order.value.expireTime : null));
const { clock, expired } = useCountdown(countdownTarget);

let controller: AbortController | null = null;

async function load(showSkeleton = true): Promise<void> {
  controller?.abort();
  controller = new AbortController();
  if (showSkeleton) loading.value = true;
  error.value = null;
  notFound.value = false;

  try {
    order.value = await getOrder(props.orderNo, controller.signal);
    payExpired.value = false;
  } catch (caught) {
    if (isApiError(caught) && caught.kind === 'canceled') return;
    if (isApiError(caught) && caught.code === BIZ_CODE.ORDER_NOT_FOUND) {
      notFound.value = true;
      order.value = null;
    } else {
      error.value = caught as Error;
    }
  } finally {
    loading.value = false;
  }
}

// 倒计时归零：禁用支付并主动重新拉取一次订单
watch(expired, (value) => {
  if (!value) return;
  payExpired.value = true;
  if (order.value?.status === 1) {
    void load(false);
  }
});

async function pay(): Promise<void> {
  if (paying.value || !order.value) return;
  paying.value = true;
  payError.value = '';
  try {
    // 用响应中的订单数据整体替换，不依赖再发一次 GET
    const updated = await payOrder(order.value.orderNo);
    order.value = updated;
    toast.success('支付成功', `订单 ${updated.orderNo} 状态已更新为已支付`);
  } catch (caught) {
    if (isApiError(caught)) {
      if (caught.code === BIZ_CODE.ORDER_STATUS_INVALID) {
        payError.value = '订单状态不允许该操作，已刷新订单状态';
        payExpired.value = true;
        await load(false);
      } else if (caught.code === BIZ_CODE.ORDER_NOT_FOUND) {
        notFound.value = true;
      } else {
        payError.value =
          caught.kind === 'network'
            ? '网络异常，支付未提交，请稍后重试'
            : caught.kind === 'timeout'
              ? '支付请求超时，请刷新订单确认状态'
              : caught.message || '支付失败，请稍后重试';
      }
    } else {
      payError.value = '支付失败，请稍后重试';
    }
    if (payError.value) {
      toast.error('支付未完成', payError.value);
    }
  } finally {
    paying.value = false;
  }
}

watch(
  () => props.orderNo,
  () => void load()
);

onMounted(() => void load());
onBeforeUnmount(() => controller?.abort());
</script>

<template>
  <div class="page">
    <div class="container">
      <nav class="crumb" aria-label="面包屑">
        <RouterLink :to="{ name: 'seckill-list' }">限时秒杀</RouterLink>
        <span aria-hidden="true">/</span>
        <span aria-current="page">订单详情</span>
      </nav>

      <div v-if="loading" class="panel order-panel">
        <SkeletonBlock width="40%" height="14px" />
        <SkeletonBlock height="30px" />
        <SkeletonBlock width="70%" height="16px" />
        <SkeletonBlock height="80px" />
      </div>

      <ErrorState
        v-else-if="notFound"
        title="秒杀订单不存在"
        description="订单号可能不正确，或订单已被清理。"
        retry-text="返回活动列表"
        @retry="router.push({ name: 'seckill-list' })"
      />

      <ErrorState
        v-else-if="error"
        :error="error as never"
        :title="errorCopy.title"
        :description="errorCopy.description"
        :show-retry="errorCopy.retryable"
        @retry="load()"
      />

      <div v-else-if="order" class="order-layout">
        <section class="panel order-panel" aria-labelledby="order-title">
          <div class="row">
            <span v-if="statusMeta" class="badge" :class="statusMeta.badgeClass">{{ statusMeta.label }}</span>
            <span class="badge badge--muted">秒杀订单</span>
          </div>

          <h1 id="order-title" class="order-title">{{ order.productName }}</h1>
          <p class="order-no">订单号 {{ order.orderNo }}</p>

          <dl class="order-list">
            <div>
              <dt>成交价</dt>
              <dd><PriceText :value="order.seckillPrice" size="s" /></dd>
            </div>
            <div>
              <dt>订单状态</dt>
              <dd>{{ statusMeta?.label ?? '—' }}</dd>
            </div>
            <div>
              <dt>活动 ID</dt>
              <dd>{{ order.activityId }}</dd>
            </div>
            <div>
              <dt>商品 ID</dt>
              <dd>{{ order.productId }}</dd>
            </div>
            <div>
              <dt>支付截止</dt>
              <dd>{{ formatDateTime(order.expireTime) }}</dd>
            </div>
            <div>
              <dt>支付时间</dt>
              <dd>{{ formatDateTime(order.payTime) }}</dd>
            </div>
          </dl>

          <div v-if="order.status === 1" class="order-pay">
            <div class="order-pay__countdown" role="timer" aria-live="off">
              <span>剩余支付时间</span>
              <strong>{{ expired ? '已超时' : clock }}</strong>
            </div>
            <button
              class="button button--primary button--block"
              type="button"
              :disabled="paying || !isPending"
              @click="pay"
            >
              {{ paying ? '支付中…' : payExpired ? '支付已超时' : '模拟支付' }}
            </button>
            <p v-if="payExpired" class="order-pay__hint">
              支付已超时，按钮保持禁用。订单状态更新中，请稍后刷新页面确认。
            </p>
            <p v-else class="order-pay__hint">当前为模拟支付，调用后端模拟支付接口，不涉及真实资金。</p>
          </div>

          <div v-else class="order-pay">
            <p class="order-pay__hint">
              <template v-if="order.status === 2">订单已支付，无需再次操作。</template>
              <template v-else-if="order.status === 3">订单已取消，不可支付。</template>
              <template v-else>订单已超时取消，不可支付。</template>
            </p>
          </div>

          <div class="order-links">
            <RouterLink class="text-link" :to="{ name: 'seckill-list' }">返回活动列表</RouterLink>
            <RouterLink class="text-link" :to="`/seckill/${order.activityId}`">查看活动详情</RouterLink>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.crumb {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 22px;
  color: var(--ink-muted);
  font-size: 12px;
}

.crumb a:hover {
  color: var(--ink);
}

.order-layout {
  max-width: 640px;
  margin-inline: auto;
}

.order-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 28px 26px 24px;
}

.order-title {
  font-size: clamp(22px, 2.6vw, 28px);
  font-weight: 800;
  letter-spacing: -0.045em;
  line-height: 1.25;
}

.order-no {
  color: var(--ink-muted);
  font-family: var(--font-latin);
  font-size: 12px;
  letter-spacing: 0.02em;
}

.order-list {
  display: flex;
  flex-direction: column;
}

.order-list > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
  font-size: 12px;
}

.order-list dt {
  color: var(--ink-muted);
}

.order-list dd {
  margin: 0;
  font-family: var(--font-latin);
  text-align: right;
}

.order-pay {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-top: 6px;
}

.order-pay__countdown {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-s);
  background: var(--paper);
  color: var(--ink-soft);
  font-size: 11px;
}

.order-pay__countdown strong {
  color: var(--persimmon);
  font-family: var(--font-latin);
  font-size: 20px;
  letter-spacing: 0.02em;
}

.order-pay__hint {
  color: var(--ink-muted);
  font-size: 11px;
  line-height: 1.7;
}

.order-links {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding-top: 6px;
  border-top: 1px solid var(--line);
}

@media (max-width: 600px) {
  .order-panel {
    padding: 22px 16px 20px;
  }
}
</style>
