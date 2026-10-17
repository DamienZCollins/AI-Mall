<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { getActivity, type SeckillActivity } from '@/api/seckill';
import { BIZ_CODE, isApiError } from '@/api/types';
import ErrorState from '@/components/ErrorState.vue';
import PriceText from '@/components/PriceText.vue';
import SkeletonBlock from '@/components/SkeletonBlock.vue';
import { useCountdown } from '@/composables/useCountdown';
import { useSeckillPurchase } from '@/features/seckill/useSeckillPurchase';
import { activityStatusMeta } from '@/features/seckill/seckillLabels';
import SeckillActionButton from '@/features/seckill/SeckillActionButton.vue';
import { useToast } from '@/composables/useToast';
import { describeError } from '@/utils/errors';
import { formatDateTime } from '@/utils/datetime';

const props = defineProps<{ activityId: string }>();
const router = useRouter();
const toast = useToast();

const activity = ref<SeckillActivity | null>(null);
const loading = ref(true);
const error = ref<Error | null>(null);
const notFound = ref(false);

const { submitting, error: purchaseError, purchase } = useSeckillPurchase(Number(props.activityId));

// 倒计时只做展示；到 0 触发一次重新拉取，不参与业务判定
const countdownTarget = computed(() => {
  if (!activity.value) return null;
  return activity.value.status === 0 ? activity.value.startTime : activity.value.endTime;
});
const { clock, expired } = useCountdown(countdownTarget);
const countdownLabel = computed(() => (activity.value?.status === 0 ? '距开始' : '距结束'));

const meta = computed(() => (activity.value ? activityStatusMeta(activity.value.status) : null));
const errorCopy = computed(() => describeError(error.value));

let controller: AbortController | null = null;

async function load(showSkeleton = true): Promise<void> {
  controller?.abort();
  controller = new AbortController();
  if (showSkeleton) loading.value = true;
  error.value = null;
  notFound.value = false;

  try {
    activity.value = await getActivity(Number(props.activityId), controller.signal);
  } catch (caught) {
    if (isApiError(caught) && caught.kind === 'canceled') return;
    if (isApiError(caught) && caught.code === BIZ_CODE.ACTIVITY_NOT_FOUND) {
      notFound.value = true;
      activity.value = null;
    } else {
      error.value = caught as Error;
    }
  } finally {
    loading.value = false;
  }
}

// 倒计时归零后主动刷新一次活动状态，按钮可用性以接口返回为准
watch(expired, (value) => {
  if (value && activity.value) {
    void load(false);
  }
});

watch(
  () => purchaseError.value,
  (info) => {
    if (!info) return;
    toast.error(info.title, info.description);
    if (info.code === BIZ_CODE.ACTIVITY_NOT_STARTED || info.code === BIZ_CODE.ACTIVITY_ENDED || info.code === BIZ_CODE.SOLD_OUT) {
      void load(false);
    }
  }
);

watch(
  () => props.activityId,
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
        <span aria-current="page">{{ activity?.title ?? '活动详情' }}</span>
      </nav>

      <div v-if="loading" class="detail">
        <SkeletonBlock height="380px" radius="var(--radius-l)" />
        <div class="detail__info">
          <SkeletonBlock width="40%" height="12px" />
          <SkeletonBlock height="32px" />
          <SkeletonBlock width="60%" height="26px" />
          <SkeletonBlock height="120px" />
        </div>
      </div>

      <ErrorState
        v-else-if="notFound"
        title="秒杀活动不存在"
        description="活动可能已下线或链接有误，请返回活动列表查看。"
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

      <article v-else-if="activity" class="detail">
        <div class="detail__visual">
          <div class="detail__visual-inner">
            <p class="eyebrow">FLASH SALE</p>
            <p class="detail__visual-title">{{ activity.title }}</p>
            <PriceText :value="activity.seckillPrice" size="l" />
            <p class="detail__original">原价 ¥{{ activity.originalPrice.toFixed(2) }}</p>
            <div class="detail__countdown" role="timer" aria-live="off">
              <span>{{ countdownLabel }}</span>
              <strong>{{ expired ? '已到时间' : clock }}</strong>
            </div>
            <p class="detail__visual-note">倒计时仅用于展示，活动是否可抢以后端状态为准。</p>
          </div>
        </div>

        <div class="detail__info">
          <div class="row">
            <span v-if="meta" class="badge" :class="meta.badgeClass">{{ meta.label }}</span>
            <span v-if="activity.remainingStock <= 0" class="badge badge--muted">已售罄</span>
            <RouterLink class="badge badge--muted" :to="`/products/${activity.productId}`">
              关联商品 #{{ activity.productId }}
            </RouterLink>
          </div>

          <h1 class="detail__title">{{ activity.title }}</h1>

          <dl class="detail__list">
            <div>
              <dt>秒杀价</dt>
              <dd>¥{{ activity.seckillPrice.toFixed(2) }}</dd>
            </div>
            <div>
              <dt>原价</dt>
              <dd>¥{{ activity.originalPrice.toFixed(2) }}</dd>
            </div>
            <div>
              <dt>剩余库存</dt>
              <dd>{{ activity.remainingStock }}</dd>
            </div>
            <div>
              <dt>活动总量</dt>
              <dd>{{ activity.totalStock }}</dd>
            </div>
            <div>
              <dt>每人限购</dt>
              <dd>{{ activity.perUserLimit }} 件</dd>
            </div>
            <div>
              <dt>活动状态</dt>
              <dd>{{ meta?.label ?? '—' }}</dd>
            </div>
            <div>
              <dt>开始时间</dt>
              <dd>{{ formatDateTime(activity.startTime) }}</dd>
            </div>
            <div>
              <dt>结束时间</dt>
              <dd>{{ formatDateTime(activity.endTime) }}</dd>
            </div>
          </dl>

          <SeckillActionButton
            :status="activity.status"
            :remaining-stock="activity.remainingStock"
            :per-user-limit="activity.perUserLimit"
            :start-time="formatDateTime(activity.startTime)"
            :end-time="formatDateTime(activity.endTime)"
            :submitting="submitting"
            @purchase="purchase()"
          />

          <div class="detail__links">
            <RouterLink class="text-link" :to="{ name: 'seckill-list' }">返回活动列表</RouterLink>
            <RouterLink class="text-link" :to="`/products/${activity.productId}`">查看关联商品</RouterLink>
          </div>
        </div>
      </article>
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

.detail {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 40px;
  align-items: start;
}

.detail__visual {
  position: relative;
  overflow: hidden;
  min-height: 420px;
  border-radius: var(--radius-l);
  background:
    radial-gradient(circle at 24% 18%, rgba(255, 255, 255, 0.7), transparent 60%),
    linear-gradient(140deg, #efe7d7 0%, #e2d5bd 60%, #d8c8ab 100%);
}

.detail__visual-inner {
  display: flex;
  height: 100%;
  flex-direction: column;
  gap: 10px;
  padding: 36px 32px;
}

.detail__visual-title {
  max-width: 360px;
  font-size: clamp(22px, 2.6vw, 30px);
  font-weight: 800;
  letter-spacing: -0.05em;
  line-height: 1.24;
}

.detail__original {
  color: var(--ink-muted);
  font-size: 12px;
  text-decoration: line-through;
}

.detail__countdown {
  display: inline-flex;
  align-items: baseline;
  gap: 10px;
  margin-top: 16px;
  padding: 10px 14px;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-s);
  background: rgba(255, 253, 248, 0.72);
  color: var(--ink-soft);
  font-size: 11px;
}

.detail__countdown strong {
  color: var(--ink);
  font-family: var(--font-latin);
  font-size: 18px;
  letter-spacing: 0.02em;
}

.detail__visual-note {
  margin-top: auto;
  color: var(--ink-muted);
  font-size: 11px;
  line-height: 1.7;
}

.detail__info {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.detail__title {
  font-size: clamp(24px, 3vw, 34px);
  font-weight: 800;
  letter-spacing: -0.055em;
  line-height: 1.2;
}

.detail__list {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.detail__list > div {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
  font-size: 12px;
}

.detail__list dt {
  color: var(--ink-muted);
}

.detail__list dd {
  margin: 0;
  font-family: var(--font-latin);
  text-align: right;
}

.detail__links {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 4px;
}

@media (max-width: 860px) {
  .detail {
    grid-template-columns: 1fr;
    gap: 24px;
  }

  .detail__visual {
    min-height: 320px;
  }
}

@media (max-width: 600px) {
  .detail__visual-inner {
    padding: 24px 20px;
  }
}
</style>
