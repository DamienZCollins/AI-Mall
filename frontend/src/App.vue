<script setup lang="ts">
import AppErrorBoundary from '@/components/AppErrorBoundary.vue';
import ToastHost from '@/components/ToastHost.vue';
</script>

<template>
  <AppErrorBoundary>
    <RouterView />
  </AppErrorBoundary>
  <ToastHost />
</template>
