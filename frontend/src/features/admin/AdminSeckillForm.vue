<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';

import { listAdminProducts, type AdminProductRow } from '@/api/admin';
import type { SeckillActivity } from '@/api/seckill';
import { validateActivityForm } from '@/utils/validation';
import { fromLocalDateTimeInput, toLocalDateTimeInput } from '@/utils/datetime';

const props = defineProps<{
  mode: 'create' | 'edit';
  initial?: SeckillActivity | null;
  submitting?: boolean;
  serverErrors?: Record<string, string>;
}>();

const emit = defineEmits<{
  submit: [payload: Record<string, unknown>];
  dirty: [dirty: boolean];
}>();

interface ActivityFormState {
  productId: string;
  title: string;
  seckillPrice: string;
  totalStock: string;
  perUserLimit: string;
  startTime: string;
  endTime: string;
  payTimeoutMinutes: string;
  status: 0 | 1;
}

const form = reactive<ActivityFormState>({
  productId: '',
  title: '',
  seckillPrice: '',
  totalStock: '',
  perUserLimit: '1',
  startTime: '',
  endTime: '',
  payTimeoutMinutes: '15',
  status: 1,
});

const errors = reactive<Record<string, string>>({});
const products = ref<AdminProductRow[]>([]);
const productsLoading = ref(false);
const productsError = ref('');
const snapshot = ref('');

const selectedProduct = computed(() => products.value.find((item) => String(item.id) === form.productId) ?? null);
const originalPrice = computed(() => {
  if (selectedProduct.value) return Number(selectedProduct.value.price);
  // 编辑时若商品不在下拉里，用活动原价做价格关系校验
  return props.initial ? Number(props.initial.originalPrice) : null;
});

function fill(): void {
  const initial = props.initial;
  if (initial) {
    form.productId = String(initial.productId);
    form.title = initial.title;
    form.seckillPrice = Number(initial.seckillPrice).toFixed(2);
    form.totalStock = String(initial.totalStock);
    form.perUserLimit = String(initial.perUserLimit);
    form.startTime = toLocalDateTimeInput(initial.startTime);
    form.endTime = toLocalDateTimeInput(initial.endTime);
    form.payTimeoutMinutes = '15';
    // 列表 status 0–3 由时间窗口推导，编辑接口的 status 是配置开关，只有「已关闭」回填为停用
    form.status = initial.status === 3 ? 0 : 1;
  }
  snapshot.value = JSON.stringify(form);
}

/** 商品下拉：只取上架商品，分页取全 */
async function loadProducts(): Promise<void> {
  productsLoading.value = true;
  productsError.value = '';
  try {
    const collected: AdminProductRow[] = [];
    let page = 1;
    for (;;) {
      const result = await listAdminProducts({ page, size: 100, status: 1 });
      collected.push(...result.list);
      if (page * 100 >= result.total || result.list.length === 0) break;
      page += 1;
    }
    products.value = collected;
  } catch {
    products.value = [];
    productsError.value = '商品列表加载失败，可以手动输入商品 ID；请确认已登录管理员账号。';
  } finally {
    productsLoading.value = false;
  }
}

watch(
  () => props.initial,
  () => fill(),
  { immediate: true }
);

watch(
  () => form,
  () => {
    emit('dirty', JSON.stringify(form) !== snapshot.value);
    // 切换商品后重校验价格关系
    if (errors.seckillPrice) {
      const result = validateActivityForm({ ...form, originalPrice: originalPrice.value });
      if (!result.seckillPrice) delete errors.seckillPrice;
    }
  },
  { deep: true }
);

watch(
  () => props.serverErrors,
  (value) => {
    if (!value) return;
    for (const [key, message] of Object.entries(value)) {
      errors[key] = message;
    }
  },
  { deep: true }
);

function validate(): boolean {
  for (const key of Object.keys(errors)) delete errors[key];
  const result = validateActivityForm({ ...form, originalPrice: originalPrice.value });
  Object.assign(errors, result);
  return Object.keys(result).length === 0;
}

function buildPayload(): Record<string, unknown> {
  if (props.mode === 'create') {
    const payload: Record<string, unknown> = {
      productId: Number(form.productId),
      title: form.title.trim(),
      seckillPrice: Number(form.seckillPrice),
      totalStock: Number(form.totalStock),
      startTime: fromLocalDateTimeInput(form.startTime),
      endTime: fromLocalDateTimeInput(form.endTime),
    };
    if (form.perUserLimit.trim()) payload.perUserLimit = Number(form.perUserLimit);
    if (form.payTimeoutMinutes.trim()) payload.payTimeoutMinutes = Number(form.payTimeoutMinutes);
    return payload;
  }

  // 编辑只接受 title、startTime、endTime、status
  const changed: Record<string, unknown> = {};
  const initial = props.initial;
  if (!initial) return changed;
  if (form.title.trim() !== initial.title) changed.title = form.title.trim();
  const start = fromLocalDateTimeInput(form.startTime);
  const end = fromLocalDateTimeInput(form.endTime);
  if (start !== initial.startTime) changed.startTime = start;
  if (end !== initial.endTime) changed.endTime = end;
  const initialStatus = initial.status === 3 ? 0 : 1;
  if (form.status !== initialStatus) changed.status = form.status;
  return changed;
}

function onSubmit(): void {
  if (!validate()) return;
  emit('submit', buildPayload());
}

onMounted(loadProducts);

defineExpose({ validate, buildPayload });
</script>

<template>
  <form class="form-card" novalidate @submit.prevent="onSubmit">
    <div class="form-row">
      <div class="field">
        <label for="af-product">秒杀商品 <span aria-hidden="true">*</span></label>
        <select
          v-if="products.length"
          id="af-product"
          v-model="form.productId"
          class="select"
          :disabled="mode === 'edit'"
          :aria-invalid="Boolean(errors.productId)"
        >
          <option value="">请选择上架商品</option>
          <option v-for="item in products" :key="item.id" :value="String(item.id)">
            #{{ item.id }} {{ item.name }} · ¥{{ Number(item.price).toFixed(2) }}
          </option>
        </select>
        <input
          v-else
          id="af-product"
          v-model="form.productId"
          class="input"
          type="text"
          inputmode="numeric"
          placeholder="请输入商品 ID"
          :disabled="mode === 'edit'"
          :aria-invalid="Boolean(errors.productId)"
        />
        <p v-if="errors.productId" class="field__error" role="alert">{{ errors.productId }}</p>
        <p v-else-if="productsLoading" class="field__hint">正在加载上架商品…</p>
        <p v-else-if="productsError" class="field__error" role="alert">{{ productsError }}</p>
        <p v-else-if="selectedProduct" class="field__hint">
          原价 ¥{{ Number(selectedProduct.price).toFixed(2) }}，秒杀价必须低于原价。
        </p>
      </div>

      <div class="field">
        <label for="af-title">活动标题 <span aria-hidden="true">*</span></label>
        <input
          id="af-title"
          v-model="form.title"
          class="input"
          type="text"
          maxlength="128"
          :aria-invalid="Boolean(errors.title)"
        />
        <p v-if="errors.title" class="field__error" role="alert">{{ errors.title }}</p>
      </div>
    </div>

    <div class="form-row">
      <div class="field">
        <label for="af-price">秒杀价 <span aria-hidden="true">*</span></label>
        <input
          id="af-price"
          v-model="form.seckillPrice"
          class="input"
          type="text"
          inputmode="decimal"
          placeholder="例如 199.00"
          :aria-invalid="Boolean(errors.seckillPrice)"
        />
        <p v-if="errors.seckillPrice" class="field__error" role="alert">{{ errors.seckillPrice }}</p>
      </div>

      <div class="field">
        <label for="af-stock">活动总量 <span aria-hidden="true">*</span></label>
        <input
          id="af-stock"
          v-model="form.totalStock"
          class="input"
          type="text"
          inputmode="numeric"
          :disabled="mode === 'edit'"
          :aria-invalid="Boolean(errors.totalStock)"
        />
        <p v-if="errors.totalStock" class="field__error" role="alert">{{ errors.totalStock }}</p>
      </div>
    </div>

    <div class="form-row">
      <div class="field">
        <label for="af-limit">每人限购</label>
        <input
          id="af-limit"
          v-model="form.perUserLimit"
          class="input"
          type="text"
          inputmode="numeric"
          :disabled="mode === 'edit'"
          :aria-invalid="Boolean(errors.perUserLimit)"
        />
        <p v-if="errors.perUserLimit" class="field__error" role="alert">{{ errors.perUserLimit }}</p>
      </div>

      <div class="field">
        <label for="af-paytime">支付超时（分钟）</label>
        <input
          id="af-paytime"
          v-model="form.payTimeoutMinutes"
          class="input"
          type="text"
          inputmode="numeric"
          :disabled="mode === 'edit'"
          :aria-invalid="Boolean(errors.payTimeoutMinutes)"
        />
        <p v-if="errors.payTimeoutMinutes" class="field__error" role="alert">{{ errors.payTimeoutMinutes }}</p>
      </div>
    </div>

    <div class="form-row">
      <div class="field">
        <label for="af-start">开始时间 <span aria-hidden="true">*</span></label>
        <input
          id="af-start"
          v-model="form.startTime"
          class="input"
          type="datetime-local"
          :aria-invalid="Boolean(errors.startTime)"
        />
        <p v-if="errors.startTime" class="field__error" role="alert">{{ errors.startTime }}</p>
      </div>

      <div class="field">
        <label for="af-end">结束时间 <span aria-hidden="true">*</span></label>
        <input
          id="af-end"
          v-model="form.endTime"
          class="input"
          type="datetime-local"
          :aria-invalid="Boolean(errors.endTime)"
        />
        <p v-if="errors.endTime" class="field__error" role="alert">{{ errors.endTime }}</p>
      </div>
    </div>

    <div v-if="mode === 'edit'" class="field">
      <span class="field__label">活动启用状态</span>
      <label class="switch">
        <input v-model="form.status" type="checkbox" :true-value="1" :false-value="0" />
        <span>{{ form.status === 1 ? '启用' : '停用' }}</span>
      </label>
      <p class="field__hint">停用后活动状态变为「已关闭」，用户侧不能抢购。</p>
    </div>

    <p class="form-note">
      时间按本地时间提交，格式 <code>2026-10-17T10:00:00</code>，不带时区偏移。
    </p>

    <div class="form-actions">
      <button class="button button--primary" type="submit" :disabled="submitting">
        {{ submitting ? '保存中…' : mode === 'create' ? '创建活动' : '保存修改' }}
      </button>
      <RouterLink class="button button--outline" :to="{ name: 'admin-seckill' }">返回列表</RouterLink>
    </div>
  </form>
</template>

<style scoped>
.form-card {
  display: flex;
  max-width: 720px;
  flex-direction: column;
  gap: 16px;
  padding: 24px;
  border: 1px solid var(--line);
  border-radius: var(--radius-m);
  background: var(--paper-light);
}

.form-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.form-note {
  color: var(--ink-muted);
  font-size: 11px;
}

.form-note code {
  font-family: var(--font-latin);
}

.form-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding-top: 6px;
  border-top: 1px solid var(--line);
}

.switch {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.switch input {
  width: 18px;
  height: 18px;
  accent-color: var(--persimmon);
}

@media (max-width: 600px) {
  .form-card {
    padding: 18px 14px;
  }

  .form-row {
    grid-template-columns: 1fr;
  }
}
</style>
