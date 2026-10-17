import { ref } from 'vue';

export type ToastKind = 'success' | 'error' | 'info';

export interface ToastItem {
  id: number;
  kind: ToastKind;
  title: string;
  detail?: string;
}

const MAX_TOASTS = 3;
const DEFAULT_DURATION = 3000;

const items = ref<ToastItem[]>([]);
let nextId = 1;

function remove(id: number): void {
  items.value = items.value.filter((item) => item.id !== id);
}

function push(kind: ToastKind, title: string, detail?: string, duration = DEFAULT_DURATION): number {
  const id = nextId++;
  const item: ToastItem = { id, kind, title };
  if (detail) {
    item.detail = detail;
  }
  items.value = [...items.value, item].slice(-MAX_TOASTS);
  if (duration > 0) {
    window.setTimeout(() => remove(id), duration);
  }
  return id;
}

/** 全局轻量提示：两套布局共用，同一时刻最多 3 条 */
export function useToast() {
  return {
    items,
    success: (title: string, detail?: string) => push('success', title, detail),
    error: (title: string, detail?: string) => push('error', title, detail),
    info: (title: string, detail?: string) => push('info', title, detail),
    dismiss: remove,
    clear: () => {
      items.value = [];
    },
  };
}
