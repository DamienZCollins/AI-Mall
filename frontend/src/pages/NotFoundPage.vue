<script setup lang="ts">
import { useRouter } from 'vue-router';

const router = useRouter();
</script>

<template>
  <div class="page">
    <div class="container">
      <div class="state-page">
        <p class="state-page__code" aria-hidden="true">404</p>
        <h1 class="state-page__title">页面不存在</h1>
        <p class="state-page__text">你访问的地址可能已更改或被移除。</p>
        <div class="row">
          <button class="button button--primary" type="button" @click="router.push('/')">返回首页</button>
          <button class="button button--outline" type="button" @click="router.push('/products')">浏览商品</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.state-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 90px 20px;
  text-align: center;
}

.state-page__code {
  color: var(--paper-deep);
  font-family: var(--font-latin);
  font-size: clamp(74px, 12vw, 130px);
  font-weight: 700;
  letter-spacing: -0.06em;
  line-height: 1;
}

.state-page__title {
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -0.03em;
}

.state-page__text {
  color: var(--ink-soft);
  font-size: 13px;
}

.state-page .row {
  margin-top: 10px;
  justify-content: center;
}
</style>
