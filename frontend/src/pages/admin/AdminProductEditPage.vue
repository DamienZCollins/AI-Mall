<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import {
  ADMIN_PRODUCT_SIZE_MAX,
  listAdminProducts,
  updateAdminProduct,
  type AdminProductRow,
  type AdminProductUpdatePayload,
} from '@/api/admin';
import { BIZ_CODE, isApiError } from '@/api/types';
import ErrorState from '@/components/ErrorState.vue';
import SkeletonBlock from '@/components/SkeletonBlock.vue';
import AdminProductForm from '@/features/admin/AdminProductForm.vue';
import { useToast } from '@/composables/useToast';
import { describeError } from '@/utils/errors';

const props = defineProps<{ id: string }>();
const router = useRouter();
const toast = useToast();

const row = ref<AdminProductRow | null>(null);
const loading = ref(true);
const error = ref<Error | null>(null);
const notFound = ref(false);
const submitting = ref(false);
const dirty = ref(false);

const errorCopy = computed(() => describeError(error.value));

const CACHE_KEY = 'mall.admin.productCache';

/** 无快照时按 size=100 逐页遍历到该 id；后端没有管理员单条商品查询接口 */
async function findProduct(targetId: number): Promise<AdminProductRow | null> {
  const cached = sessionStorage.getItem(CACHE_KEY);
  if (cached) {
    try {
      const parsed = JSON.parse(cached) as AdminProductRow;
      if (parsed.id === targetId) {
        sessionStorage.removeItem(CACHE_KEY);
        return parsed;
      }
    } catch {
      sessionStorage.removeItem(CACHE_KEY);
    }
  }

  let page = 1;
  for (;;) {
    const result = await listAdminProducts({ page, size: ADMIN_PRODUCT_SIZE_MAX });
    const hit = result.list.find((item) => item.id === targetId);
    if (hit) return hit;
    if (page * ADMIN_PRODUCT_SIZE_MAX >= result.total || result.list.length === 0) {
      return null;
    }
    page += 1;
  }
}

async function load(): Promise<void> {
  loading.value = true;
  error.value = null;
  notFound.value = false;
  try {
    const found = await findProduct(Number(props.id));
    if (!found) {
      notFound.value = true;
      return;
    }
    row.value = found;
  } catch (caught) {
    error.value = caught as Error;
  } finally {
    loading.value = false;
  }
}

async function submit(payload: Record<string, unknown>): Promise<void> {
  if (submitting.value || !row.value) return;
  if (Object.keys(payload).length === 0) {
    toast.info('没有需要保存的修改');
    return;
  }

  submitting.value = true;
  try {
    const updated = await updateAdminProduct(row.value.id, payload as AdminProductUpdatePayload);
    row.value = updated;
    dirty.value = false;
    toast.success('已保存修改', `商品 ${updated.id} 已更新`);
  } catch (caught) {
    if (isApiError(caught) && caught.code === BIZ_CODE.PRODUCT_NOT_FOUND) {
      toast.error('商品不存在', '该商品可能已被删除');
      notFound.value = true;
    } else if (isApiError(caught) && caught.kind === 'network') {
      toast.error('保存失败', '网络异常，修改未提交');
    } else {
      toast.error('保存失败', isApiError(caught) ? caught.message || '请稍后重试' : '请稍后重试');
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
        <p class="eyebrow">EDIT PRODUCT</p>
        <h1 class="page-title">编辑商品</h1>
        <p class="page-description">只提交改动字段；上下架请在商品管理列表操作。</p>
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
      title="商品不存在"
      description="该商品可能已被删除，请返回列表查看。"
      retry-text="返回商品列表"
      @retry="router.push({ name: 'admin-products' })"
    />

    <ErrorState
      v-else-if="error"
      :error="error as never"
      :title="errorCopy.title"
      :description="errorCopy.description"
      :show-retry="errorCopy.retryable"
      @retry="load"
    />

    <AdminProductForm
      v-else-if="row"
      mode="edit"
      :initial="row"
      :submitting="submitting"
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
