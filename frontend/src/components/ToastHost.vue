<script setup lang="ts">
import { useToast } from '@/composables/useToast';

const { items, dismiss } = useToast();
</script>

<template>
  <div class="toast-region" role="status" aria-live="polite" aria-atomic="false">
    <div
      v-for="item in items"
      :key="item.id"
      class="toast"
      :class="`toast--${item.kind}`"
      data-testid="toast"
    >
      <div class="toast__copy">
        <strong>{{ item.title }}</strong>
        <span v-if="item.detail">{{ item.detail }}</span>
      </div>
      <button class="toast__action" type="button" aria-label="关闭提示" @click="dismiss(item.id)">
        <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
          <path d="m6 6 12 12M18 6 6 18" />
        </svg>
      </button>
    </div>
  </div>
</template>

<style scoped>
.toast-region {
  position: fixed;
  z-index: 110;
  right: 24px;
  bottom: 24px;
  display: flex;
  max-width: min(360px, calc(100vw - 32px));
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  pointer-events: none;
}

.toast {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px 12px 12px;
  border: 1px solid var(--line);
  border-left-width: 3px;
  border-radius: var(--radius-s);
  color: var(--ink);
  background: var(--white);
  box-shadow: 0 12px 34px rgba(29, 27, 23, 0.14);
  font-size: 12px;
  line-height: 1.5;
  pointer-events: auto;
  animation: toastIn var(--normal) var(--ease);
}

.toast--success {
  border-left-color: var(--state-success);
}

.toast--error {
  border-left-color: var(--persimmon);
}

.toast--info {
  border-left-color: var(--ink-soft);
}

.toast__copy strong {
  display: block;
  font-weight: 600;
}

.toast__copy span {
  color: var(--ink-soft);
  font-size: 11px;
}

.toast__action {
  display: inline-flex;
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  align-items: center;
  justify-content: center;
  margin-left: auto;
  border-radius: 4px;
  color: var(--ink-muted);
}

.toast__action:hover {
  color: var(--ink);
  background: rgba(29, 27, 23, 0.06);
}

.toast__action .icon {
  width: 14px;
  height: 14px;
}

@media (max-width: 600px) {
  .toast-region {
    right: 16px;
    bottom: calc(78px + env(safe-area-inset-bottom));
    left: 16px;
    max-width: none;
    align-items: stretch;
  }

  .toast {
    width: 100%;
  }
}
</style>
