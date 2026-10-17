import { computed, onBeforeUnmount, ref, watch, type Ref } from 'vue';

import { parseServerTime } from '@/utils/datetime';

/**
 * 倒计时：以接口返回的时间为目标，不使用浏览器本地时间推算业务状态。
 * 只负责展示，业务状态一律以后端字段为准。
 */
export function useCountdown(target: Ref<string | null | undefined> | string | null | undefined) {
  const targetRef = computed(() => (typeof target === 'object' && target !== null && 'value' in target ? target.value : (target as string | null | undefined)));

  const remainingMs = ref(0);
  const expired = computed(() => remainingMs.value <= 0);

  let timer: number | null = null;

  const totalSeconds = computed(() => Math.max(0, Math.floor(remainingMs.value / 1000)));
  const days = computed(() => Math.floor(totalSeconds.value / 86400));
  const hours = computed(() => Math.floor((totalSeconds.value % 86400) / 3600));
  const minutes = computed(() => Math.floor((totalSeconds.value % 3600) / 60));
  const seconds = computed(() => totalSeconds.value % 60);

  const clock = computed(() => {
    const pad = (value: number) => String(value).padStart(2, '0');
    if (days.value > 0) {
      return `${days.value} 天 ${pad(hours.value)}:${pad(minutes.value)}:${pad(seconds.value)}`;
    }
    return `${pad(hours.value)}:${pad(minutes.value)}:${pad(seconds.value)}`;
  });

  function tick(): void {
    const targetMs = parseServerTime(targetRef.value ?? null);
    remainingMs.value = targetMs === null ? 0 : Math.max(0, targetMs - Date.now());
  }

  function stop(): void {
    if (timer !== null) {
      window.clearInterval(timer);
      timer = null;
    }
  }

  function start(): void {
    stop();
    tick();
    if (remainingMs.value <= 0) return;
    timer = window.setInterval(() => {
      tick();
      if (remainingMs.value <= 0) {
        stop();
      }
    }, 1000);
  }

  watch(targetRef, () => start(), { immediate: true });

  onBeforeUnmount(stop);

  return { remainingMs, expired, clock, days, hours, minutes, seconds, start, stop };
}
