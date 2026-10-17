<script setup lang="ts">
import { computed } from 'vue';

const props = withDefaults(
  defineProps<{
    value: number | null | undefined;
    size?: 's' | 'm' | 'l';
  }>(),
  { size: 'm' }
);

/** 金额统一两位小数；缺失值显示占位而非 NaN */
const formatted = computed(() => {
  const value = props.value;
  if (typeof value !== 'number' || Number.isNaN(value)) {
    return '—';
  }
  return value.toFixed(2);
});
</script>

<template>
  <span class="price" :class="`price--${size}`">
    <small>¥</small>{{ formatted }}
  </span>
</template>

<style scoped>
.price {
  color: var(--persimmon);
  font-family: var(--font-latin);
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.04em;
  line-height: 1.2;
}

.price small {
  margin-right: 1px;
  font-family: var(--font-sans);
  font-size: 10px;
  font-weight: 500;
}

.price--s {
  font-size: 15px;
}

.price--s small {
  font-size: 9px;
}

.price--l {
  font-size: 30px;
}

.price--l small {
  font-size: 14px;
}
</style>
