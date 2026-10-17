<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue';

const props = withDefaults(
  defineProps<{
    open: boolean;
    title: string;
    description?: string;
    confirmText?: string;
    cancelText?: string;
    busy?: boolean;
    tone?: 'default' | 'danger';
  }>(),
  {
    confirmText: '确认',
    cancelText: '取消',
    busy: false,
    tone: 'default',
  }
);

const emit = defineEmits<{ confirm: []; cancel: [] }>();

const dialogRef = ref<HTMLElement | null>(null);
const confirmRef = ref<HTMLButtonElement | null>(null);
let previousFocus: HTMLElement | null = null;

function focusables(): HTMLElement[] {
  if (!dialogRef.value) return [];
  return Array.from(
    dialogRef.value.querySelectorAll<HTMLElement>(
      'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
    )
  );
}

function onKeydown(event: KeyboardEvent): void {
  if (!props.open) return;

  if (event.key === 'Escape') {
    event.preventDefault();
    emit('cancel');
    return;
  }

  if (event.key !== 'Tab') return;

  const list = focusables();
  if (list.length === 0) return;
  const first = list[0] as HTMLElement;
  const last = list[list.length - 1] as HTMLElement;
  const active = document.activeElement;

  // 焦点陷阱：Tab 在首尾可聚焦元素之间循环
  if (event.shiftKey && (active === first || !dialogRef.value?.contains(active))) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && active === last) {
    event.preventDefault();
    first.focus();
  }
}

watch(
  () => props.open,
  async (open) => {
    if (open) {
      previousFocus = document.activeElement as HTMLElement | null;
      document.body.classList.add('has-overlay');
      document.addEventListener('keydown', onKeydown, true);
      await nextTick();
      confirmRef.value?.focus();
    } else {
      document.body.classList.remove('has-overlay');
      document.removeEventListener('keydown', onKeydown, true);
      previousFocus?.focus({ preventScroll: true });
      previousFocus = null;
    }
  }
);

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown, true);
  document.body.classList.remove('has-overlay');
});
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="confirm" role="presentation" @click.self="emit('cancel')">
      <div
        ref="dialogRef"
        class="confirm__panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirm-title"
        aria-describedby="confirm-description"
      >
        <h2 id="confirm-title" class="confirm__title">{{ title }}</h2>
        <p v-if="description" id="confirm-description" class="confirm__description">{{ description }}</p>
        <div class="confirm__actions">
          <button class="button button--outline button--small" type="button" :disabled="busy" @click="emit('cancel')">
            {{ cancelText }}
          </button>
          <button
            ref="confirmRef"
            class="button button--small"
            :class="tone === 'danger' ? 'button--danger' : 'button--primary'"
            type="button"
            :disabled="busy"
            @click="emit('confirm')"
          >
            {{ confirmText }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.confirm {
  position: fixed;
  z-index: 120;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(29, 27, 23, 0.4);
  -webkit-backdrop-filter: blur(3px);
  backdrop-filter: blur(3px);
  animation: fadeIn 160ms ease;
}

.confirm__panel {
  width: min(100%, 400px);
  padding: 24px 22px 20px;
  border-radius: var(--radius-m);
  background: var(--paper-light);
  box-shadow: 0 22px 60px rgba(20, 18, 15, 0.24);
  animation: fadeSlideIn 200ms var(--ease);
}

.confirm__title {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.confirm__description {
  margin-top: 10px;
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.7;
}

.confirm__actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 22px;
}
</style>
