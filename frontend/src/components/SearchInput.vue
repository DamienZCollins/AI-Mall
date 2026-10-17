<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

const props = withDefaults(
  defineProps<{
    /** 初始值，用于搜索页回填 */
    modelValue?: string;
    placeholder?: string;
    /** 本地建议词表；后端未提供搜索建议接口 */
    suggestions?: string[];
    /** 弹层内使用时提交后关闭父级 */
    autofocus?: boolean;
    inputId?: string;
  }>(),
  {
    modelValue: '',
    placeholder: '搜索好物、品牌或灵感',
    suggestions: () => ['香氛', '桌面音箱', '原木家具', '护肤精华', '水洗棉', '通勤包'],
    autofocus: false,
    inputId: 'global-search',
  }
);

const emit = defineEmits<{ submit: [keyword: string]; edit: [value: string] }>();

const router = useRouter();
const formRef = ref<HTMLFormElement | null>(null);
const inputRef = ref<HTMLInputElement | null>(null);
const keyword = ref(props.modelValue);
const panelOpen = ref(false);
const expanded = computed(() => panelOpen.value && keyword.value.trim().length > 0);
const hasValue = computed(() => keyword.value.length > 0);

watch(
  () => props.modelValue,
  (value) => {
    keyword.value = value;
  }
);

const suggestionItems = computed<{ label: string; items: string[]; matched: boolean }>(() => {
  const query = keyword.value.trim().toLowerCase();
  if (!query) return { label: '', items: [], matched: false };
  const matched = props.suggestions.filter((item) => item.toLowerCase().includes(query)).slice(0, 5);
  return {
    label: matched.length ? '搜索建议' : '大家常搜',
    items: matched.length ? matched : props.suggestions.slice(0, 4),
    matched: matched.length > 0,
  };
});

/** 把命中片段切分为三段，供 <mark> 高亮 */
function segments(item: string): { before: string; hit: string; after: string } {
  const query = keyword.value.trim().toLowerCase();
  const index = item.toLowerCase().indexOf(query);
  if (index < 0 || !query) {
    return { before: item, hit: '', after: '' };
  }
  return {
    before: item.slice(0, index),
    hit: item.slice(index, index + query.length),
    after: item.slice(index + query.length),
  };
}

function syncPanel(): void {
  panelOpen.value = keyword.value.trim().length > 0;
}

/** 提交：trim 后非空才跳转，空值不请求不跳转 */
function submit(value = keyword.value): void {
  const trimmed = value.trim().slice(0, 128);
  if (!trimmed) return;
  closePanel();
  emit('submit', trimmed);
  void router.push({ path: '/search', query: { keyword: trimmed } });
}

function choose(item: string): void {
  keyword.value = item;
  emit('edit', item);
  submit(item);
}

function clear(): void {
  keyword.value = '';
  emit('edit', '');
  closePanel();
  inputRef.value?.focus();
}

function closePanel(): void {
  panelOpen.value = false;
}

function onInput(): void {
  emit('edit', keyword.value);
  syncPanel();
}

function onFocus(): void {
  if (keyword.value.trim()) {
    syncPanel();
  }
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    closePanel();
    inputRef.value?.blur();
    return;
  }
  // 中文输入法上屏时 isComposing 为真，不能当作提交
  if (event.key === 'Enter' && !event.isComposing) {
    event.preventDefault();
    submit();
  }
}

function onPointerDown(event: PointerEvent): void {
  if (!formRef.value?.contains(event.target as Node)) {
    closePanel();
  }
}

document.addEventListener('pointerdown', onPointerDown);

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onPointerDown);
});

if (props.autofocus) {
  window.setTimeout(() => inputRef.value?.focus(), 80);
}

defineExpose({ focus: () => inputRef.value?.focus() });
</script>

<template>
  <form ref="formRef" class="search-form" :class="{ 'has-value': hasValue }" role="search" novalidate @submit.prevent="submit()">
    <label class="sr-only" :for="inputId">搜索商品</label>
    <svg class="icon search-form__icon" viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="11" cy="11" r="6.5" />
      <path d="m16 16 4 4" />
    </svg>
    <input
      :id="inputId"
      ref="inputRef"
      v-model="keyword"
      name="keyword"
      type="text"
      autocomplete="off"
      maxlength="128"
      :placeholder="placeholder"
      :aria-controls="`${inputId}-suggestions`"
      :aria-expanded="expanded ? 'true' : 'false'"
      aria-autocomplete="list"
      @input="onInput"
      @focus="onFocus"
      @keydown="onKeydown"
    />
    <button class="search-form__submit" type="button" aria-label="搜索" @click="submit()">
      <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
        <path d="M5 12h14M13 6l6 6-6 6" />
      </svg>
    </button>
    <button
      class="search-form__clear"
      :class="{ 'is-visible': hasValue }"
      type="button"
      aria-label="清空搜索内容"
      @click="clear"
    >
      <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
        <path d="m6 6 12 12M18 6 6 18" />
      </svg>
    </button>
    <div
      v-show="expanded"
      :id="`${inputId}-suggestions`"
      class="search-suggestions is-open"
      role="listbox"
      aria-label="搜索建议"
    >
      <p class="suggestions-label">{{ suggestionItems.label }}</p>
      <button
        v-for="item in suggestionItems.items"
        :key="item"
        class="suggestion-button"
        type="button"
        role="option"
        :aria-selected="false"
        :aria-label="`搜索${item}`"
        @click="choose(item)"
      >
        <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
          <circle cx="11" cy="11" r="6.5" />
          <path d="m16 16 4 4" />
        </svg>
        <span>
          {{ segments(item).before }}<mark v-if="segments(item).hit">{{ segments(item).hit }}</mark
          >{{ segments(item).after }}
        </span>
      </button>
    </div>
  </form>
</template>

<style scoped>
.search-form {
  position: relative;
  display: flex;
  flex: 1 1 280px;
  min-width: 180px;
  max-width: 380px;
  height: 44px;
  align-items: center;
  margin-left: auto;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-s);
  background: transparent;
  transition:
    border-color var(--fast) ease,
    background var(--fast) ease,
    box-shadow var(--fast) ease;
}

.search-form:focus-within {
  border-color: var(--ink);
  background: var(--white);
  box-shadow: 0 0 0 3px rgba(189, 67, 40, 0.14);
}

.search-form__icon {
  width: 17px;
  height: 17px;
  margin-left: 13px;
  color: var(--ink-muted);
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.7;
}

.search-form:focus-within .search-form__icon {
  color: var(--persimmon);
}

.search-form input {
  min-width: 0;
  flex: 1;
  height: 100%;
  padding: 0 8px;
  border: 0;
  outline: 0;
  color: var(--ink);
  background: none;
  font-size: 13px;
}

.search-form input::placeholder {
  color: var(--ink-muted);
}

.search-form__clear {
  display: grid;
  width: 44px;
  height: 44px;
  flex: 0 0 44px;
  place-items: center;
  border-radius: var(--radius-s);
  color: var(--ink-soft);
  opacity: 0;
  pointer-events: none;
  transition:
    opacity var(--fast) ease,
    color var(--fast) ease;
}

.search-form__clear.is-visible {
  opacity: 1;
  pointer-events: auto;
}

.search-form:not(.has-value) .search-form__clear,
.search-form:not(.has-value) .search-form__submit {
  display: none;
}

.search-form__clear:hover {
  color: var(--persimmon);
}

.search-form__clear .icon {
  width: 15px;
  height: 15px;
}

.search-form__submit {
  position: relative;
  display: grid;
  width: 44px;
  height: 44px;
  flex: 0 0 44px;
  place-items: center;
  border-radius: var(--radius-s);
  color: var(--white);
  background: var(--persimmon);
  transition:
    background var(--fast) ease,
    transform var(--fast) ease;
}

.search-form__submit:hover {
  background: var(--persimmon-dark);
}

.search-form__submit:active {
  transform: scale(0.96);
}

.search-form__submit .icon {
  width: 15px;
  height: 15px;
}

.search-suggestions {
  position: absolute;
  z-index: 70;
  top: calc(100% + 8px);
  right: 0;
  left: 0;
  display: none;
  overflow: hidden;
  padding: 8px;
  border: 1px solid var(--line);
  border-radius: var(--radius-s);
  background: var(--white);
  box-shadow: 0 14px 40px rgba(29, 27, 23, 0.12);
}

.search-suggestions.is-open {
  display: block;
  animation: fadeSlideIn 180ms var(--ease);
}

.suggestions-label {
  padding: 6px 9px 7px;
  color: var(--ink-muted);
  font-size: 10px;
  letter-spacing: 0.08em;
}

.suggestion-button {
  display: flex;
  width: 100%;
  min-height: 42px;
  align-items: center;
  gap: 10px;
  padding: 0 9px;
  border-radius: 6px;
  color: var(--ink-soft);
  font-size: 12px;
  text-align: left;
}

.suggestion-button:hover,
.suggestion-button:focus-visible {
  outline: 0;
  color: var(--ink);
  background: var(--paper);
}

.suggestion-button .icon {
  width: 14px;
  height: 14px;
  color: var(--ink-muted);
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.7;
}

.suggestion-button mark {
  color: var(--ink);
  background: transparent;
  font-weight: 600;
}

@media (max-width: 1280px) {
  .search-form {
    max-width: 300px;
  }
}
</style>
