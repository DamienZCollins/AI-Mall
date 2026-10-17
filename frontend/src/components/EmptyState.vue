<script setup lang="ts">
defineProps<{
  title: string;
  description?: string;
  actionText?: string;
}>();

const emit = defineEmits<{ action: [] }>();
</script>

<template>
  <div class="empty-state">
    <svg class="empty-state__icon" viewBox="0 0 48 48" aria-hidden="true">
      <rect x="6" y="10" width="36" height="26" rx="4" />
      <path d="M6 18h36M19 26h10" />
    </svg>
    <p class="empty-state__title">{{ title }}</p>
    <p v-if="description" class="empty-state__description">{{ description }}</p>
    <button v-if="actionText" class="button button--outline button--small" type="button" @click="emit('action')">
      {{ actionText }}
    </button>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 56px 20px;
  border: 1px dashed var(--line-strong);
  border-radius: var(--radius-m);
  background: rgba(251, 248, 240, 0.6);
  text-align: center;
}

.empty-state__icon {
  width: 44px;
  height: 44px;
  margin-bottom: 2px;
  fill: none;
  stroke: var(--ink-muted);
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.4;
}

.empty-state__title {
  color: var(--ink);
  font-size: 15px;
  font-weight: 600;
}

.empty-state__description {
  max-width: 420px;
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.7;
}

.empty-state .button {
  margin-top: 6px;
}
</style>
