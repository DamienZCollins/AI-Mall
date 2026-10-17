<script setup lang="ts">
withDefaults(
  defineProps<{
    width?: string;
    height?: string;
    radius?: string;
  }>(),
  {
    width: '100%',
    height: '16px',
    radius: 'var(--radius-s)',
  }
);
</script>

<template>
  <span
    class="skeleton"
    aria-hidden="true"
    :style="{ width, height, borderRadius: radius }"
  />
</template>

<style scoped>
.skeleton {
  display: block;
  background: var(--paper-deep);
  animation: skeletonPulse 1.4s ease-in-out infinite;
}

@media (prefers-reduced-motion: reduce) {
  .skeleton {
    animation: none;
    opacity: 0.8;
  }
}
</style>
