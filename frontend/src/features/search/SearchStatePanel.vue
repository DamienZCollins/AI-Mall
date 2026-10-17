<script setup lang="ts">
import { computed } from 'vue';

import EmptyState from '@/components/EmptyState.vue';
import SkeletonBlock from '@/components/SkeletonBlock.vue';
import type { SearchErrorKind, SearchStatus } from './useSearchQuery';

const props = defineProps<{
  status: SearchStatus;
  errorKind: SearchErrorKind;
  keyword: string;
  errorMessage?: string;
}>();

const emit = defineEmits<{ retry: []; 'back-home': [] }>();

const copy = computed<{ title: string; description: string; retry: boolean }>(() => {
  switch (props.errorKind) {
    case '3001':
      return {
        title: '语义检索暂不可用',
        description: '当前搜索依赖语义检索，向量化服务暂时无法完成，请稍后重试。',
        retry: true,
      };
    case '3002':
      return {
        title: '搜索服务暂不可用',
        description: '搜索服务当前不可用，请稍后重试。',
        retry: true,
      };
    case 'timeout':
      return { title: '搜索超时，请重试', description: '搜索请求超时，可以重新发起一次查询。', retry: true };
    case 'network':
      return { title: '网络异常', description: '搜索请求没有到达服务器，请检查网络后重试。', retry: true };
    default:
      return {
        title: '搜索未完成',
        description: props.errorMessage || '搜索请求处理失败，请稍后重试。',
        retry: true,
      };
  }
});

const announce = computed(() => {
  if (props.status === 'loading') return '正在搜索…';
  if (props.status === 'empty') return '没有找到相关商品';
  if (props.status === 'error') return copy.value.title;
  return '';
});
</script>

<template>
  <div class="search-state" role="status" aria-live="polite">
    <span class="sr-only">{{ announce }}</span>

    <template v-if="status === 'idle'">
      <div class="search-state__guide">
        <svg class="search-state__icon" viewBox="0 0 48 48" aria-hidden="true">
          <circle cx="22" cy="22" r="13" />
          <path d="m31 31 8 8" />
        </svg>
        <p class="search-state__title">请输入关键词</p>
        <p class="search-state__text">
          可以描述你需要的场景，例如「夏天穿的透气鞋」「适合书房的桌面音箱」。
        </p>
      </div>
    </template>

    <template v-else-if="status === 'loading'">
      <div class="skeleton-grid" aria-hidden="true">
        <div v-for="index in 6" :key="index" class="card-skeleton">
          <SkeletonBlock height="170px" radius="var(--radius-m) var(--radius-m) 0 0" />
          <div class="card-skeleton__body">
            <SkeletonBlock width="40%" height="10px" />
            <SkeletonBlock height="16px" />
            <SkeletonBlock width="32%" height="20px" />
          </div>
        </div>
      </div>
    </template>

    <template v-else-if="status === 'empty'">
      <EmptyState
        title="没有找到相关商品"
        :description="`没有与「${keyword}」匹配的商品，可以换一种说法，或回到首页看看精选。`"
        action-text="返回首页"
        @action="emit('back-home')"
      />
    </template>

    <template v-else-if="status === 'error'">
      <div class="search-state__error" role="alert">
        <p class="search-state__title">{{ copy.title }}</p>
        <p class="search-state__text">{{ copy.description }}</p>
        <button v-if="copy.retry" class="button button--outline button--small" type="button" @click="emit('retry')">
          重试
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.search-state {
  display: block;
}

.search-state__guide,
.search-state__error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 64px 20px;
  border: 1px dashed var(--line-strong);
  border-radius: var(--radius-m);
  background: rgba(251, 248, 240, 0.6);
  text-align: center;
}

.search-state__error {
  border-style: solid;
  border-color: rgba(189, 67, 40, 0.28);
  background: rgba(189, 67, 40, 0.05);
}

.search-state__icon {
  width: 46px;
  height: 46px;
  fill: none;
  stroke: var(--ink-muted);
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.5;
}

.search-state__title {
  font-size: 15px;
  font-weight: 600;
}

.search-state__text {
  max-width: 460px;
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.75;
}

.skeleton-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.card-skeleton {
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: var(--radius-m);
  background: rgba(251, 248, 240, 0.58);
}

.card-skeleton__body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
}

@media (max-width: 860px) {
  .skeleton-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 600px) {
  .skeleton-grid {
    gap: 10px;
  }

  .search-state__guide,
  .search-state__error {
    padding: 44px 16px;
  }
}
</style>
