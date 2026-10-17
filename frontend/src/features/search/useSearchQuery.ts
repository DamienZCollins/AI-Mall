import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { SEARCH_KEYWORD_MAX, SEARCH_SIZE_MAX, searchProducts, type SearchHit } from '@/api/search';
import { BIZ_CODE, isApiError } from '@/api/types';

export type SearchStatus = 'idle' | 'loading' | 'success' | 'empty' | 'error';
export type SearchErrorKind = '3001' | '3002' | 'timeout' | 'network' | 'other' | null;

const DEFAULT_SIZE = 10;

/** 搜索条件只信 URL，页面不另存第二份状态 */
export function useSearchQuery() {
  const route = useRoute();
  const router = useRouter();

  const hits = ref<SearchHit[]>([]);
  const total = ref(0);
  const status = ref<SearchStatus>('idle');
  const errorKind = ref<SearchErrorKind>(null);
  const errorMessage = ref('');

  let controller: AbortController | null = null;
  let seq = 0;

  const keyword = computed(() => {
    const raw = route.query.keyword;
    return typeof raw === 'string' ? raw.trim().slice(0, SEARCH_KEYWORD_MAX) : '';
  });

  const page = computed(() => {
    const raw = Number(route.query.page);
    return Number.isInteger(raw) && raw >= 1 ? raw : 1;
  });

  const size = computed(() => {
    const raw = Number(route.query.size);
    return Number.isInteger(raw) && raw >= 1 && raw <= SEARCH_SIZE_MAX ? raw : DEFAULT_SIZE;
  });

  /** page 非正整数时用 replace 修正 URL，避免发出非法请求 */
  function fixUrl(): boolean {
    const rawPage = route.query.page;
    const rawSize = route.query.size;
    const next: Record<string, string> = {};
    let changed = false;

    if (typeof route.query.keyword === 'string' && route.query.keyword !== keyword.value) {
      next.keyword = keyword.value;
      changed = true;
    } else if (typeof route.query.keyword === 'string') {
      next.keyword = route.query.keyword;
    }

    if (rawPage !== undefined) {
      const numeric = Number(rawPage);
      if (!Number.isInteger(numeric) || numeric < 1) {
        changed = true;
      } else if (numeric > 1) {
        next.page = String(numeric);
      }
    }

    if (rawSize !== undefined) {
      const numeric = Number(rawSize);
      if (!Number.isInteger(numeric) || numeric < 1 || numeric > SEARCH_SIZE_MAX) {
        changed = true;
      } else if (numeric !== DEFAULT_SIZE) {
        next.size = String(numeric);
      }
    }

    if (changed && keyword.value) {
      void router.replace({ name: 'search', query: next });
    }
    return changed;
  }

  async function run(): Promise<void> {
    fixUrl();

    if (!keyword.value) {
      controller?.abort();
      hits.value = [];
      total.value = 0;
      status.value = 'idle';
      errorKind.value = null;
      return;
    }

    controller?.abort();
    controller = new AbortController();
    const current = ++seq;

    status.value = 'loading';
    errorKind.value = null;
    errorMessage.value = '';

    try {
      const result = await searchProducts(
        { keyword: keyword.value, page: page.value, size: size.value },
        controller.signal
      );
      if (current !== seq) return;
      hits.value = result.list;
      total.value = result.total;
      // 只有 code === 0 且 list 为空才允许进入空态
      status.value = result.list.length === 0 ? 'empty' : 'success';
    } catch (caught) {
      if (current !== seq) return;
      if (isApiError(caught) && caught.kind === 'canceled') return;

      status.value = 'error';
      errorMessage.value = isApiError(caught) ? caught.message : '';

      if (isApiError(caught)) {
        if (caught.code === BIZ_CODE.EMBEDDING_FAILED) {
          errorKind.value = '3001';
        } else if (caught.code === BIZ_CODE.SEARCH_UNAVAILABLE) {
          errorKind.value = '3002';
        } else if (caught.kind === 'timeout') {
          errorKind.value = 'timeout';
        } else if (caught.kind === 'network') {
          errorKind.value = 'network';
        } else {
          errorKind.value = 'other';
        }
      } else {
        errorKind.value = 'other';
      }
    }
  }

  /** 提交新关键词：重置到第 1 页，用 push 保证前进后退可用 */
  function submitKeyword(next: string): void {
    const trimmed = next.trim().slice(0, SEARCH_KEYWORD_MAX);
    if (!trimmed) return;
    void router.push({
      name: 'search',
      query: { keyword: trimmed, ...(size.value !== DEFAULT_SIZE ? { size: String(size.value) } : {}) },
    });
  }

  function goToPage(next: number): void {
    if (!keyword.value) return;
    const target = Math.max(1, Math.trunc(next));
    void router.push({
      name: 'search',
      query: {
        keyword: keyword.value,
        ...(target > 1 ? { page: String(target) } : {}),
        ...(size.value !== DEFAULT_SIZE ? { size: String(size.value) } : {}),
      },
    });
  }

  function changeSize(next: number): void {
    const clamped = Math.min(SEARCH_SIZE_MAX, Math.max(1, Math.trunc(next)));
    void router.push({
      name: 'search',
      query: {
        keyword: keyword.value,
        ...(clamped !== DEFAULT_SIZE ? { size: String(clamped) } : {}),
      },
    });
  }

  watch(
    () => [keyword.value, page.value, size.value],
    () => {
      void run();
    },
    { immediate: true }
  );

  onBeforeUnmount(() => {
    controller?.abort();
    seq += 1;
  });

  return {
    keyword,
    page,
    size,
    hits,
    total,
    status,
    errorKind,
    errorMessage,
    run,
    submitKeyword,
    goToPage,
    changeSize,
    sizeMax: SEARCH_SIZE_MAX,
  };
}
