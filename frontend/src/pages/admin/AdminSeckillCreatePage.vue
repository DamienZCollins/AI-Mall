<script setup lang="ts">
import { ref } from 'vue';

import { createAdminActivity, type AdminActivityCreatePayload } from '@/api/admin';
import { isApiError } from '@/api/types';
import AdminSeckillForm from '@/features/admin/AdminSeckillForm.vue';
import { useToast } from '@/composables/useToast';

const toast = useToast();

const submitting = ref(false);
const created = ref<{ id: number; title: string } | null>(null);
const serverErrors = ref<Record<string, string>>({});

/** 业务码 4008 / 4009 回填到对应字段 */
function mapServerErrors(code: number, message: string): Record<string, string> {
  if (code === 4008) return { endTime: message || '结束时间必须晚于开始时间' };
  if (code === 4009) return { seckillPrice: message || '秒杀价必须低于商品原价' };
  if (code === 2001) return { productId: message || '请选择上架商品' };
  return {};
}

async function submit(payload: Record<string, unknown>): Promise<void> {
  if (submitting.value) return;
  submitting.value = true;
  serverErrors.value = {};

  try {
    const activity = await createAdminActivity(payload as unknown as AdminActivityCreatePayload);
    created.value = { id: activity.id, title: activity.title };
    // 后端在事务提交后触发 Redis 预热，页面不承诺「预热完成」
    toast.success('活动已创建', '库存预热已触发');
  } catch (caught) {
    if (isApiError(caught)) {
      serverErrors.value = mapServerErrors(caught.code, caught.message);
      toast.error(
        '创建失败',
        caught.kind === 'network' ? '网络异常，请求未提交' : caught.message || '请稍后重试'
      );
    } else {
      toast.error('创建失败', '请稍后重试');
    }
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div class="admin-page">
    <div class="page-head">
      <div>
        <p class="eyebrow">NEW SECKILL</p>
        <h1 class="page-title">创建秒杀活动</h1>
        <p class="page-description">商品必须是上架商品，秒杀价必须低于商品原价。</p>
      </div>
    </div>

    <div v-if="created" class="panel created-panel" role="status">
      <p class="created-panel__title">活动已创建，库存预热已触发</p>
      <p class="created-panel__text">
        活动 #{{ created.id }} · {{ created.title }}。预热失败不会回滚活动，可稍后在管理端确认库存。
      </p>
      <div class="row">
        <RouterLink class="button button--primary button--small" :to="{ name: 'admin-seckill' }">返回活动列表</RouterLink>
        <button class="button button--outline button--small" type="button" @click="created = null">继续创建</button>
      </div>
    </div>

    <AdminSeckillForm
      v-else
      mode="create"
      :submitting="submitting"
      :server-errors="serverErrors"
      @submit="submit"
    />
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
  line-height: 1.7;
}
</style>
