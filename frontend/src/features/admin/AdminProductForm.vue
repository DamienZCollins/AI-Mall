<script setup lang="ts">
import { reactive, ref, watch } from 'vue';

import ImageUploader from './ImageUploader.vue';
import type { AdminProductRow } from '@/api/admin';
import { validateProductForm } from '@/utils/validation';

const props = defineProps<{
  mode: 'create' | 'edit';
  initial?: AdminProductRow | null;
  submitting?: boolean;
  serverErrors?: Record<string, string>;
}>();

const emit = defineEmits<{
  submit: [payload: Record<string, unknown>];
  dirty: [dirty: boolean];
}>();

interface FormState {
  name: string;
  brand: string;
  categoryId: string;
  description: string;
  sellingPoint: string;
  price: string;
  stock: string;
  imageUrl: string;
}

const form = reactive<FormState>({
  name: '',
  brand: '',
  categoryId: '',
  description: '',
  sellingPoint: '',
  price: '',
  stock: '',
  imageUrl: '',
});

const errors = reactive<Record<string, string>>({});
const snapshot = ref<string>('');

function fill(row: AdminProductRow | null | undefined): void {
  if (!row) {
    form.name = '';
    form.brand = '';
    form.categoryId = '';
    form.description = '';
    form.sellingPoint = '';
    form.price = '';
    form.stock = '';
    form.imageUrl = '';
  } else {
    form.name = row.name ?? '';
    form.brand = row.brand ?? '';
    form.categoryId = String(row.categoryId ?? '');
    form.description = row.description ?? '';
    form.sellingPoint = row.sellingPoint ?? '';
    form.price = Number(row.price).toFixed(2);
    form.stock = String(row.stock ?? '');
    form.imageUrl = row.imageUrl ?? '';
  }
  snapshot.value = JSON.stringify(form);
  for (const key of Object.keys(errors)) delete errors[key];
}

watch(
  () => props.initial,
  (row) => fill(row),
  { immediate: true }
);

watch(
  () => form,
  () => {
    emit('dirty', JSON.stringify(form) !== snapshot.value);
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
  const result = validateProductForm(form);
  Object.assign(errors, result);
  return Object.keys(result).length === 0;
}

/** 编辑时只发送与初始快照不同的字段 */
function buildPayload(): Record<string, unknown> {
  const base: Record<string, unknown> = {
    name: form.name.trim(),
    brand: form.brand.trim() || null,
    categoryId: Number(form.categoryId),
    description: form.description.trim() || null,
    sellingPoint: form.sellingPoint.trim() || null,
    price: Number(form.price),
    stock: Number(form.stock),
    imageUrl: form.imageUrl.trim() || null,
  };

  if (props.mode === 'create') {
    return base;
  }

  const initial = props.initial;
  if (!initial) return base;

  const changed: Record<string, unknown> = {};
  if (base.name !== initial.name) changed.name = base.name;
  if ((base.brand ?? null) !== (initial.brand ?? null)) changed.brand = base.brand;
  if (base.categoryId !== initial.categoryId) changed.categoryId = base.categoryId;
  if ((base.description ?? null) !== (initial.description ?? null)) changed.description = base.description;
  if ((base.sellingPoint ?? null) !== (initial.sellingPoint ?? null)) changed.sellingPoint = base.sellingPoint;
  if (base.price !== Number(initial.price)) changed.price = base.price;
  if (base.stock !== initial.stock) changed.stock = base.stock;
  if ((base.imageUrl ?? null) !== (initial.imageUrl ?? null)) changed.imageUrl = base.imageUrl;
  return changed;
}

function onSubmit(): void {
  if (!validate()) return;
  emit('submit', buildPayload());
}

defineExpose({ validate, buildPayload, isDirty: () => JSON.stringify(form) !== snapshot.value });
</script>

<template>
  <form class="product-form" novalidate @submit.prevent="onSubmit">
    <div class="field">
      <label for="pf-name">商品名称 <span aria-hidden="true">*</span></label>
      <input
        id="pf-name"
        v-model="form.name"
        class="input"
        type="text"
        maxlength="128"
        :aria-invalid="Boolean(errors.name)"
        :aria-describedby="errors.name ? 'pf-name-error' : undefined"
      />
      <p v-if="errors.name" id="pf-name-error" class="field__error" role="alert">{{ errors.name }}</p>
    </div>

    <div class="product-form__row">
      <div class="field">
        <label for="pf-brand">品牌</label>
        <input id="pf-brand" v-model="form.brand" class="input" type="text" maxlength="64" :aria-invalid="Boolean(errors.brand)" />
        <p v-if="errors.brand" class="field__error" role="alert">{{ errors.brand }}</p>
      </div>
      <div class="field">
        <label for="pf-category">分类 ID <span aria-hidden="true">*</span></label>
        <input
          id="pf-category"
          v-model="form.categoryId"
          class="input"
          type="text"
          inputmode="numeric"
          :aria-invalid="Boolean(errors.categoryId)"
          :aria-describedby="errors.categoryId ? 'pf-category-error' : undefined"
        />
        <p v-if="errors.categoryId" id="pf-category-error" class="field__error" role="alert">{{ errors.categoryId }}</p>
      </div>
    </div>

    <div class="product-form__row">
      <div class="field">
        <label for="pf-price">售价 <span aria-hidden="true">*</span></label>
        <input
          id="pf-price"
          v-model="form.price"
          class="input"
          type="text"
          inputmode="decimal"
          placeholder="例如 399.00"
          :aria-invalid="Boolean(errors.price)"
          :aria-describedby="errors.price ? 'pf-price-error' : 'pf-price-hint'"
          @blur="form.price = form.price.trim() && Number.isFinite(Number(form.price)) ? Number(form.price).toFixed(2) : form.price"
        />
        <p v-if="errors.price" id="pf-price-error" class="field__error" role="alert">{{ errors.price }}</p>
        <p v-else id="pf-price-hint" class="field__hint">最多 8 位整数与 2 位小数</p>
      </div>
      <div class="field">
        <label for="pf-stock">库存 <span aria-hidden="true">*</span></label>
        <input
          id="pf-stock"
          v-model="form.stock"
          class="input"
          type="text"
          inputmode="numeric"
          :aria-invalid="Boolean(errors.stock)"
          :aria-describedby="errors.stock ? 'pf-stock-error' : undefined"
        />
        <p v-if="errors.stock" id="pf-stock-error" class="field__error" role="alert">{{ errors.stock }}</p>
      </div>
    </div>

    <div class="field">
      <label for="pf-selling">卖点</label>
      <input id="pf-selling" v-model="form.sellingPoint" class="input" type="text" maxlength="255" :aria-invalid="Boolean(errors.sellingPoint)" />
      <p v-if="errors.sellingPoint" class="field__error" role="alert">{{ errors.sellingPoint }}</p>
    </div>

    <div class="field">
      <label for="pf-description">商品描述</label>
      <textarea id="pf-description" v-model="form.description" class="textarea" maxlength="2000" :aria-invalid="Boolean(errors.description)" />
      <p v-if="errors.description" class="field__error" role="alert">{{ errors.description }}</p>
    </div>

    <div class="field">
      <span class="field__label">商品图片</span>
      <ImageUploader v-model="form.imageUrl" :disabled="submitting" />
      <p v-if="errors.imageUrl" class="field__error" role="alert">{{ errors.imageUrl }}</p>
    </div>

    <div class="product-form__actions">
      <button class="button button--primary" type="submit" :disabled="submitting">
        {{ submitting ? '保存中…' : mode === 'create' ? '创建商品' : '保存修改' }}
      </button>
      <RouterLink class="button button--outline" :to="{ name: 'admin-products' }">返回列表</RouterLink>
    </div>
  </form>
</template>

<style scoped>
.product-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 720px;
  padding: 24px;
  border: 1px solid var(--line);
  border-radius: var(--radius-m);
  background: var(--paper-light);
}

.product-form__row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.product-form__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding-top: 6px;
  border-top: 1px solid var(--line);
}

@media (max-width: 600px) {
  .product-form {
    padding: 18px 14px;
  }

  .product-form__row {
    grid-template-columns: 1fr;
  }

  .product-form__actions .button {
    flex: 1;
  }
}
</style>
