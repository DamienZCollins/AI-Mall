import { onBeforeUnmount, ref } from 'vue';

import { getResult } from '@/api/seckill';
import type { PurchaseState } from '@/api/seckill';
import { isApiError } from '@/api/types';

/** 递增间隔：1s → 2s → 3s → 5s，上限 5s；页面级超时 60 秒 */
export const POLLING_INTERVALS = [1000, 2000, 3000, 5000] as const;
export const POLLING_TIMEOUT_MS = 60_000;

export type PollingPhase = 'idle' | 'polling' | 'queuing' | 'success' | 'failed' | 'timeout' | 'error';

export interface UseSeckillPollingOptions {
  activityId: number;
  autoStart?: boolean;
  onState?: (state: PurchaseState) => void;
}

/**
 * 只负责定时器与请求节奏，不持有页面 UI 状态。
 * 单实例：start() 内部先 stop()，重复调用不会产生第二个定时器。
 */
export function useSeckillPolling(options: UseSeckillPollingOptions) {
  const phase = ref<PollingPhase>('idle');
  const state = ref<PurchaseState | null>(null);
  const orderNo = ref<string | null>(null);
  const running = ref(false);
  const errorMessage = ref('');
  const waitedMs = ref(0);

  let timer: number | null = null;
  let startedAt = 0;
  let attempt = 0;
  let controller: AbortController | null = null;
  let disposed = false;

  function clearTimer(): void {
    if (timer !== null) {
      window.clearTimeout(timer);
      timer = null;
    }
  }

  function stop(): void {
    clearTimer();
    controller?.abort();
    controller = null;
    running.value = false;
  }

  function scheduleNext(): void {
    const delay = POLLING_INTERVALS[Math.min(attempt, POLLING_INTERVALS.length - 1)] as number;
    attempt += 1;
    timer = window.setTimeout(() => {
      timer = null;
      void tick();
    }, delay);
  }

  async function tick(): Promise<void> {
    if (!running.value || disposed) return;

    if (Date.now() - startedAt >= POLLING_TIMEOUT_MS) {
      stop();
      phase.value = 'timeout';
      return;
    }

    controller?.abort();
    controller = new AbortController();

    try {
      const result = await getResult(options.activityId, controller.signal);
      if (!running.value || disposed) return;

      state.value = result.state;
      orderNo.value = result.orderNo;
      options.onState?.(result.state);
      waitedMs.value = Date.now() - startedAt;

      if (result.state === 'SUCCESS') {
        stop();
        phase.value = 'success';
        return;
      }
      if (result.state === 'FAILED') {
        stop();
        phase.value = 'failed';
        return;
      }

      phase.value = 'queuing';
      scheduleNext();
    } catch (caught) {
      if (!running.value || disposed) return;
      if (isApiError(caught) && caught.kind === 'canceled') return;

      // 单次请求失败不算 FAILED：保持轮询并继续递增间隔
      phase.value = 'error';
      errorMessage.value = isApiError(caught) ? caught.message : '查询失败，请重试';
      scheduleNext();
    }
  }

  /** 启动轮询：先停止既有轮询，保证同一页面只有一个实例 */
  function start(): void {
    stop();
    disposed = false;
    startedAt = Date.now();
    attempt = 0;
    waitedMs.value = 0;
    errorMessage.value = '';
    phase.value = 'polling';
    running.value = true;
    void tick();
  }

  /** 手动查询一次；super 态不自动重试 */
  async function queryOnce(): Promise<void> {
    stop();
    disposed = false;
    running.value = true;
    startedAt = Date.now();
    await tick();
    if (!running.value) return;
    stop();
  }

  function reset(): void {
    stop();
    phase.value = 'idle';
    state.value = null;
    orderNo.value = null;
    errorMessage.value = '';
    waitedMs.value = 0;
  }

  if (options.autoStart) {
    start();
  }

  onBeforeUnmount(() => {
    disposed = true;
    stop();
  });

  return { phase, state, orderNo, running, errorMessage, waitedMs, start, stop, queryOnce, reset };
}
