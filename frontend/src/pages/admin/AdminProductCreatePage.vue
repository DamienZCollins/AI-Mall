<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue';
import { useRouter } from 'vue-router';

import { createAdminProduct, type AdminProductPayload } from '@/api/admin';
import { BIZ_CODE, isApiError } from '@/api/types';
import AdminProductForm from '@/features/admin/AdminProductForm.vue';
import { useToast } from '@/composables/useToast';

const router = useRouter();
const toast = useToast();

const submitting = ref(false);
const createdId = ref<number | null>(null);

async function submit(payload: Record<string, unknown>): Promise<void> {
  if (submitting.value) return;
  submitting.value = true;
  try {
    const row = await createAdminProduct(payload as unknown as AdminProductPayload);
    createdId.value = row.id;
    toast.success('商品已创建', '当前为下架状态，上架后用户侧可见');
  } catch (caught) {
    if (isApiError(caught)) {
      toast.error('创建失败', caught.kind === 'network' ? '网络异常，请检查网络后重试' : caught.message || '请稍后重试');
    } else {
      toast.error('创建失败', '请稍后重试');
    }
  } finally {
    submitting.value = false;
  }
}

function continueCreate(): void {
  createdId.value = null;
  void router.go(0);
}

onBeforeUnmount(() => {
  createdId.value = null;
});

// 供 BIZ_CODE 引用保持导入使用
void BIZ_CODE;
</script>

<template>
  <div class="admin-page">
    <div class="page-head">
      <div>
        <p class="eyebrow">NEW PRODUCT</p>
        <h1 class="page-title">新增商品</h1>
        <p class="page-description">新增后默认下架，需要在商品管理中上架才会出现在用户侧。</p>
      </div>
    </div>

    <div v-if="createdId" class="panel created-panel" role="status">
      <p class="created-panel__title">商品已创建，当前为下架状态</p>
      <p class="created-panel__text">商品 ID {{ createdId }}，可以继续新增，或前往列表上架。</p>
      <div class="row">
        <RouterLink class="button button--primary button--small" :to="{ name: 'admin-products' }">去上架</RouterLink>
        <button class="button button--outline button--small" type="button" @click="continueCreate">继续新增</button>
      </div>
    </div>

    <AdminProductForm v-else mode="create" :submitting="submitting" @submit="submit" />
  </div>
</template>

<style scoped>
.admin-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.created-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 24px;
}

.created-panel__title {
  font-size: 15px;
  font-weight: 700;
}

.created-panel__text {
  color: var(--ink-soft);
  font-size: 12px;
}
</style>
