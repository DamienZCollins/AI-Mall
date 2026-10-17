<script setup lang="ts">
import { computed } from 'vue';

const props = withDefaults(
  defineProps<{
    stock: number;
    threshold?: number;
  }>(),
  { threshold: 10 }
);

const soldOut = computed(() => props.stock <= 0);
const low = computed(() => !soldOut.value && props.stock < props.threshold);
const label = computed(() => {
  if (soldOut.value) return '已售罄';
  if (low.value) return `仅剩 ${props.stock} 件`;
  return `库存 ${props.stock}`;
});
</script>

<template>
  <span class="stock-badge" :class="{ 'stock-badge--out': soldOut, 'stock-badge--low': low }">
    {{ label }}
  </span>
</template>

<style scoped>
.stock-badge {
  display: inline-flex;
  min-height: 22px;
  align-items: center;
  padding: 0 7px;
  border: 1px solid var(--line);
  border-radius: 4px;
  color: var(--ink-soft);
  font-size: 10px;
  line-height: 1;
  white-space: nowrap;
}

.stock-badge--low {
  border-color: rgba(168, 121, 31, 0.4);
  color: var(--state-warning);
  background: rgba(168, 121, 31, 0.08);
}

.stock-badge--out {
  border-color: rgba(29, 27, 23, 0.3);
  color: var(--ink-muted);
  background: rgba(29, 27, 23, 0.06);
}
</style>
