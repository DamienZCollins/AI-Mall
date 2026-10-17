<script setup lang="ts">
import PriceText from '@/components/PriceText.vue';
import type { SeckillActivity } from '@/api/seckill';
import { activityStatusMeta } from '@/features/seckill/seckillLabels';
import { formatDateTime } from '@/utils/datetime';

defineProps<{ activity: SeckillActivity }>();

const meta = (activity: SeckillActivity) => activityStatusMeta(activity.status);
</script>

<template>
  <article class="activity-card">
    <div class="activity-card__top">
      <span class="badge" :class="meta(activity).badgeClass">{{ meta(activity).label }}</span>
      <span v-if="activity.remainingStock <= 0" class="badge badge--muted">已售罄</span>
    </div>

    <h3 class="activity-card__title">
      <RouterLink :to="`/seckill/${activity.id}`">{{ activity.title }}</RouterLink>
    </h3>

    <div class="activity-card__price">
      <PriceText :value="activity.seckillPrice" size="m" />
      <span class="activity-card__original">原价 ¥{{ activity.originalPrice.toFixed(2) }}</span>
    </div>

    <dl class="activity-card__meta">
      <div>
        <dt>剩余 / 总量</dt>
        <dd>{{ activity.remainingStock }} / {{ activity.totalStock }}</dd>
      </div>
      <div>
        <dt>每人限购</dt>
        <dd>{{ activity.perUserLimit }} 件</dd>
      </div>
      <div>
        <dt>开始</dt>
        <dd>{{ formatDateTime(activity.startTime) }}</dd>
      </div>
      <div>
        <dt>结束</dt>
        <dd>{{ formatDateTime(activity.endTime) }}</dd>
      </div>
    </dl>

    <RouterLink class="button button--outline button--small button--block" :to="`/seckill/${activity.id}`">
      查看活动
    </RouterLink>
  </article>
</template>

<style scoped>
.activity-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: var(--radius-m);
  background: rgba(251, 248, 240, 0.62);
  transition:
    border-color var(--fast) ease,
    background var(--fast) ease;
}

.activity-card:hover {
  border-color: var(--line-strong);
  background: var(--paper-light);
}

.activity-card__top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.activity-card__title {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.5;
}

.activity-card__price {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px;
}

.activity-card__original {
  color: var(--ink-muted);
  font-size: 11px;
  text-decoration: line-through;
}

.activity-card__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 14px;
}

.activity-card__meta > div {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 11px;
}

.activity-card__meta dt {
  color: var(--ink-muted);
}

.activity-card__meta dd {
  margin: 0;
  color: var(--ink-soft);
  font-family: var(--font-latin);
  text-align: right;
}

@media (max-width: 600px) {
  .activity-card {
    padding: 14px;
  }

  .activity-card__meta {
    grid-template-columns: 1fr;
  }
}
</style>
