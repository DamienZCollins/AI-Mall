<script setup lang="ts">
import { computed } from 'vue';

const props = withDefaults(
  defineProps<{
    page: number;
    size: number;
    total: number;
    disabled?: boolean;
    sizeOptions?: number[];
  }>(),
  {
    disabled: false,
    sizeOptions: () => [12, 24, 48],
  }
);

const emit = defineEmits<{ 'update:page': [page: number]; 'update:size': [size: number] }>();

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / Math.max(1, props.size))));
const canPrev = computed(() => props.page > 1 && !props.disabled);
const canNext = computed(() => props.page < totalPages.value && !props.disabled);

const pages = computed<(number | '...')[]>(() => {
  const last = totalPages.value;
  const current = props.page;
  if (last <= 7) {
    return Array.from({ length: last }, (_, index) => index + 1);
  }
  const items: (number | '...')[] = [1];
  const start = Math.max(2, current - 1);
  const end = Math.min(last - 1, current + 1);
  if (start > 2) items.push('...');
  for (let i = start; i <= end; i += 1) items.push(i);
  if (end < last - 1) items.push('...');
  items.push(last);
  return items;
});

function go(page: number): void {
  if (props.disabled) return;
  const next = Math.min(totalPages.value, Math.max(1, page));
  if (next !== props.page) {
    emit('update:page', next);
  }
}

function changeSize(event: Event): void {
  const value = Number((event.target as HTMLSelectElement).value);
  if (Number.isFinite(value)) {
    emit('update:size', value);
  }
}
</script>

<template>
  <nav class="pagination" aria-label="分页">
    <p class="pagination__summary">
      共 <strong>{{ total }}</strong> 条 · 第 {{ page }} / {{ totalPages }} 页
    </p>
    <div class="pagination__controls">
      <button class="pagination__btn" type="button" :disabled="!canPrev" aria-label="上一页" @click="go(page - 1)">
        上一页
      </button>
      <template v-for="(item, index) in pages" :key="`${item}-${index}`">
        <span v-if="item === '...'" class="pagination__gap" aria-hidden="true">…</span>
        <button
          v-else
          class="pagination__btn pagination__btn--num"
          :class="{ 'is-current': item === page }"
          type="button"
          :disabled="disabled"
          :aria-current="item === page ? 'page' : undefined"
          :aria-label="`第 ${item} 页`"
          @click="go(item)"
        >
          {{ item }}
        </button>
      </template>
      <button class="pagination__btn" type="button" :disabled="!canNext" aria-label="下一页" @click="go(page + 1)">
        下一页
      </button>
      <label class="pagination__size">
        <span class="sr-only">每页显示数量</span>
        <select class="select" :value="size" :disabled="disabled" @change="changeSize">
          <option v-for="option in sizeOptions" :key="option" :value="option">每页 {{ option }} 条</option>
        </select>
      </label>
    </div>
  </nav>
</template>

<style scoped>
.pagination {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-top: 28px;
  padding-top: 20px;
  border-top: 1px solid var(--line);
}

.pagination__summary {
  color: var(--ink-muted);
  font-size: 12px;
}

.pagination__summary strong {
  color: var(--ink);
  font-family: var(--font-latin);
}

.pagination__controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.pagination__btn {
  display: inline-flex;
  min-width: 38px;
  min-height: 38px;
  align-items: center;
  justify-content: center;
  padding: 0 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-s);
  color: var(--ink-soft);
  font-size: 12px;
  transition:
    color var(--fast) ease,
    border-color var(--fast) ease,
    background var(--fast) ease;
}

.pagination__btn:hover:not(:disabled) {
  border-color: var(--ink);
  color: var(--ink);
}

.pagination__btn:disabled {
  opacity: 0.42;
}

.pagination__btn--num {
  padding: 0;
  font-family: var(--font-latin);
}

.pagination__btn.is-current {
  border-color: var(--persimmon);
  color: var(--white);
  background: var(--persimmon);
}

.pagination__gap {
  padding: 0 4px;
  color: var(--ink-muted);
}

.pagination__size {
  margin-left: 6px;
}

.pagination__size .select {
  width: auto;
  min-height: 38px;
  font-size: 12px;
}

@media (max-width: 600px) {
  .pagination {
    flex-direction: column;
    align-items: flex-start;
  }

  .pagination__btn {
    min-width: 34px;
    min-height: 34px;
    padding: 0 9px;
    font-size: 11px;
  }
}
</style>
