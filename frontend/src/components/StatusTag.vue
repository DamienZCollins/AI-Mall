<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{
  status: 0 | 1;
}>();

const label = computed(() => (props.status === 1 ? '在售' : '已下架'));
</script>

<template>
  <span class="status-tag" :class="status === 1 ? 'status-tag--on' : 'status-tag--off'">{{ label }}</span>
</template>

<style scoped>
.status-tag {
  display: inline-flex;
  min-height: 22px;
  align-items: center;
  padding: 0 7px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
}

.status-tag--on {
  color: #4d5945;
  background: rgba(127, 138, 117, 0.18);
}

.status-tag--off {
  color: var(--ink-muted);
  background: rgba(29, 27, 23, 0.08);
}
</style>
