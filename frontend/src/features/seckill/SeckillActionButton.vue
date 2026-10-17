<script setup lang="ts">
import { computed } from 'vue';

import type { ActivityStatus } from '@/api/seckill';
import { resolveActionButton } from './seckillLabels';

const props = defineProps<{
  status: ActivityStatus;
  remainingStock: number;
  perUserLimit: number;
  startTime: string;
  endTime: string;
  submitting?: boolean;
}>();

const emit = defineEmits<{ purchase: [] }>();

const button = computed(() => resolveActionButton(props.status, props.remainingStock));
const disabled = computed(() => !button.value.clickable || Boolean(props.submitting));

const hintText = computed(() => {
  if (props.status === 0) return `开始时间 ${props.startTime}`;
  if (props.status === 1 && props.remainingStock > 0) return `每人限购 ${props.perUserLimit} 件`;
  if (props.status === 2) return `结束时间 ${props.endTime}`;
  return button.value.hint;
});
</script>

<template>
  <div class="action-block">
    <button
      class="button button--primary button--block"
      type="button"
      :disabled="disabled"
      :aria-describedby="'seckill-action-hint'"
      @click="emit('purchase')"
    >
      {{ submitting ? '提交中…' : button.text }}
    </button>
    <p id="seckill-action-hint" class="action-block__hint">{{ hintText }}</p>
    <p v-if="status === 1 && remainingStock <= 0" class="action-block__hint">本场库存已抢完</p>
    <p v-if="status === 3" class="action-block__hint">活动已停用</p>
  </div>
</template>

<style scoped>
.action-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.action-block__hint {
  color: var(--ink-muted);
  font-size: 11px;
  line-height: 1.65;
  text-align: center;
}
</style>
