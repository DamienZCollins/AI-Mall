<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { listAdminActivities, updateAdminActivity, type AdminActivityUpdatePayload } from '@/api/admin';
import type { SeckillActivity } from '@/api/seckill';
import { isApiError } from '@/api/types';
import ErrorState from '@/components/ErrorState.vue';
import SkeletonBlock from '@/components/SkeletonBlock.vue';
import AdminSeckillForm from '@/features/admin/AdminSeckillForm.vue';
import { useToast } from '@/composables/useToast';
import { describeError } from '@/utils/errors';

const props = defineProps<{ id: string }>();
const router = useRouter();
const toast = useToast();

const activity = ref<SeckillActivity | null>(null);
const loading = ref(true);
const error = ref<Error | null>(null);
const notFound = ref(false);
const submitting = ref(false);
const dirty = ref(false);
const serverErrors = ref<Record<string, string>>({});

const errorCopy = computed(() => describeError(error.value));

const CACHE_KEY = 'mall.admin.activityCache';

/** 后端没有管理员单条活动查询接口，优先用列表快照，否则按页遍历 */
async function findActivity(targetId: number): Promise<SeckillActivity | null> {
  const cached = sessionStorage.getItem(CACHE_KEY);
  if (cached) {
    try {
      const parsed = JSON.parse(cached) as SeckillActivity;
      if (parsed.id === targetId) {
        return parsed;
      }
    } catch {
      sessionStorage.removeItem(CACHE_KEY);
    }
  }

  let page = 1;
  for (;;) {
    const result = await listAdminActivities({ page, size: 100 });
    const hit = result.list.find((item) => item.id === targetId);
    if (hit) return hit;
    if (page * 100 >= result.total || result.list.length === 0) return null;
    page += 1;
  }
}

async function load(): Promise<void> {
  loading.value = true;
  error.value = null;
  notFound.value = false;
  try {
    const found = await findActivity(Number(props.id));
    if (!found) {
      notFound.value = true;
      return;
    }
    activity.value = found;
  } catch (caught) {
    error.value = caught as Error;
  } finally {
    loading.value = false;
  }
}

function mapServerErrors(code: number, message: string): Record<string, string> {
  if (code === 4008) return { endTime: message || '结束时间必须晚于开始时间' };
  if (code === 4009) return { seckillPrice: message || '秒杀价必须低于商品原价' };
  if (code === 4001) return {};
  return {};
}

async function submit(payload: Record<string, unknown>): Promise<void> {
  if (submitting.value || !activity.value) return;
  if (Object.keys(payload).length === 0) {
    toast.info('没有需要保存的修改');
    return;
  }

  submitting.value = true;
  serverErrors.value = {};
  try {
    const updated = await updateAdminActivity(activity.value.id, payload as AdminActivityUpdatePayload);
    activity.value = updated;
    dirty.value = false;
    toast.success('已保存修改', `活动 #${updated.id} 已更新`);
  } catch (caught) {
    if (isApiError(caught)) {
      serverErrors.value = mapServerErrors(caught.code, caught.message);
      if (caught.code === 4001) {
        notFound.value = true;
        toast.error('活动不存在', '该活动可能已被删除');
      } else {
        toast.error('保存失败', caught.kind === 'network' ? '网络异常，修改未提交' : caught.message || '请稍后重试');
      }
    } else {
      toast.error('保存失败', '请稍后重试');
    }
  } finally {
    submitting.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="admin-page">
    <div class="page-head">
      <div>
        <p class="eyebrow">EDIT SECKILL</p>
        <h1 class="page-title">编辑秒杀活动</h1>
        <p class="page-description">
          只能修改标题、起止时间与启用状态；商品、秒杀价、总量与限购由创建时确定。
        </p>
      </div>
      <span v-if="dirty" class="badge badge--warning">有未保存的修改</span>
    </div>

    <div v-if="loading" class="panel edit-loading">
      <SkeletonBlock width="40%" height="14px" />
      <SkeletonBlock height="44px" />
      <SkeletonBlock height="44px" />
      <SkeletonBlock height="90px" />
    </div>

    <ErrorState
      v-else-if="notFound"
      title="活动不存在"
      description="活动可能已被删除，请返回列表查看。"
      retry-text="返回活动列表"
      @retry="router.push({ name: 'admin-seckill' })"
    />

    <ErrorState
      v-else-if="error"
      :error="error as never"
      :title="errorCopy.title"
      :description="errorCopy.description"
      :show-retry="errorCopy.retryable"
      @retry="load"
    />

    <AdminSeckillForm
      v-else-if="activity"
      mode="edit"
      :initial="activity"
      :submitting="submitting"
      :server-errors="serverErrors"
      @submit="submit"
      @dirty="(value) => (dirty = value)"
    />
  </div>
</template>

<style scoped>
.admin-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.edit-loading {
  display: flex;
  max-width: 720px;
  flex-direction: column;
  gap: 12px;
  padding: 24px;
}
</style>
