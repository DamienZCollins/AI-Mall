<script setup lang="ts">
import { onErrorCaptured, ref } from 'vue';

/**
 * 全局错误边界：捕获子组件渲染与生命周期异常，展示可恢复的错误页，避免白屏。
 * 事件处理器中的异步异常不会进入这里，由页面的 error 态承担。
 */
const hasError = ref(false);
const errorMessage = ref('');

onErrorCaptured((error) => {
  hasError.value = true;
  errorMessage.value = error instanceof Error ? error.message : String(error);
  console.error('[AppErrorBoundary]', error);
  return false;
});

function recover(): void {
  hasError.value = false;
  errorMessage.value = '';
  window.location.reload();
}
</script>

<template>
  <div v-if="hasError" class="error-boundary" role="alert">
    <div class="error-boundary__panel">
      <p class="eyebrow">UNEXPECTED ERROR</p>
      <h1 class="error-boundary__title">页面出现了意外错误</h1>
      <p class="error-boundary__text">
        页面在渲染过程中遇到异常。可以重新加载后再试，已保存的数据不受影响。
      </p>
      <p v-if="errorMessage" class="error-boundary__detail">{{ errorMessage }}</p>
      <div class="error-boundary__actions">
        <button class="button button--primary" type="button" @click="recover">重新加载</button>
        <a class="button button--outline" href="/">返回首页</a>
      </div>
    </div>
  </div>
  <slot v-else />
</template>

<style scoped>
.error-boundary {
  display: flex;
  min-height: 100vh;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: var(--paper);
}

.error-boundary__panel {
  width: min(100%, 520px);
  padding: 36px 32px 30px;
  border: 1px solid var(--line);
  border-radius: var(--radius-l);
  background: var(--paper-light);
  box-shadow: 0 12px 40px rgba(29, 27, 23, 0.08);
}

.error-boundary__title {
  margin-top: 14px;
  font-size: clamp(22px, 2.6vw, 28px);
  font-weight: 800;
  letter-spacing: -0.045em;
  line-height: 1.25;
}

.error-boundary__text {
  margin-top: 12px;
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.8;
}

.error-boundary__detail {
  margin-top: 12px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-s);
  color: var(--ink-muted);
  font-family: var(--font-latin);
  font-size: 11px;
  word-break: break-word;
}

.error-boundary__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 24px;
}
</style>
