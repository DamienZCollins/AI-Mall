<script setup lang="ts">
import { computed } from 'vue';

import type { ApiError } from '@/api/types';

const props = defineProps<{
  error?: ApiError | Error | null;
  title?: string;
  description?: string;
  retryText?: string;
  showRetry?: boolean;
}>();

const emit = defineEmits<{ retry: [] }>();

/** 区分超时、网络、业务与 HTTP 三类文案，业务错误优先使用后端 message */
const message = computed(() => {
  if (props.description) return props.description;
  const error = props.error;
  if (!error) return '';

  if ('kind' in error) {
    switch (error.kind) {
      case 'timeout':
        return '请求超时，请稍后重试';
      case 'network':
        return '网络异常，请检查网络后重试';
      case 'canceled':
        return '请求已取消';
      case 'business':
        return error.message || '请求未能完成';
      default:
        return error.message || '请求失败，请稍后重试';
    }
  }

  return error.message || '出现未知错误';
});

const heading = computed(() => props.title ?? '加载失败');
</script>

<template>
  <div class="error-state" role="alert">
    <svg class="error-state__icon" viewBox="0 0 48 48" aria-hidden="true">
      <circle cx="24" cy="24" r="16" />
      <path d="M24 16v10M24 31v.5" />
    </svg>
    <p class="error-state__title">{{ heading }}</p>
    <p class="error-state__description">{{ message }}</p>
    <button
      v-if="showRetry !== false"
      class="button button--outline button--small"
      type="button"
      @click="emit('retry')"
    >
      {{ retryText ?? '重试' }}
    </button>
  </div>
</template>

<style scoped>
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 52px 20px;
  border: 1px solid rgba(189, 67, 40, 0.28);
  border-radius: var(--radius-m);
  background: rgba(189, 67, 40, 0.05);
  text-align: center;
}

.error-state__icon {
  width: 42px;
  height: 42px;
  fill: none;
  stroke: var(--persimmon);
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.5;
}

.error-state__title {
  color: var(--ink);
  font-size: 15px;
  font-weight: 600;
}

.error-state__description {
  max-width: 440px;
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.7;
}

.error-state .button {
  margin-top: 6px;
}
</style>
