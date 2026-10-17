const EAST_8_OFFSET_MS = 8 * 60 * 60 * 1000;

/**
 * 后端返回 ISO 8601 本地时间（2026-10-17T10:00:00，不含偏移），
 * 展示时按字面时间处理，不经过 Date 解析，避免浏览器时区造成偏移。
 */
export function formatDateTime(value: string | null | undefined, fallback = '—'): string {
  if (!value) return fallback;
  const match = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/.exec(value);
  if (!match) return value;
  const [, year, month, day, hour, minute] = match;
  return `${year}-${month}-${day} ${hour}:${minute}`;
}

export function formatTime(value: string | null | undefined, fallback = '—'): string {
  if (!value) return fallback;
  const match = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/.exec(value);
  if (!match) return value;
  const [, , , , hour, minute] = match;
  return `${hour}:${minute}`;
}

/** 把后端本地时间当作 Asia/Shanghai 时间解析为时间戳 */
export function parseServerTime(value: string | null | undefined): number | null {
  if (!value) return null;
  const match = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?/.exec(value);
  if (!match) return null;
  const [, year, month, day, hour, minute, second] = match;
  const utc = Date.UTC(
    Number(year),
    Number(month) - 1,
    Number(day),
    Number(hour),
    Number(minute),
    Number(second ?? '0')
  );
  return utc - EAST_8_OFFSET_MS;
}

/** datetime-local 控件值（截到分钟） → 后端可接受的本地时间（补齐秒） */
export function fromLocalDateTimeInput(value: string): string | null {
  if (!value) return null;
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?/.exec(value);
  if (!match) return null;
  const [, year, month, day, hour, minute, second] = match;
  return `${year}-${month}-${day}T${hour}:${minute}:${second ?? '00'}`;
}

/** 后端本地时间 → datetime-local 控件值（截到分钟） */
export function toLocalDateTimeInput(value: string | null | undefined): string {
  if (!value) return '';
  const match = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/.exec(value);
  if (!match) return '';
  const [, year, month, day, hour, minute] = match;
  return `${year}-${month}-${day}T${hour}:${minute}`;
}

/** 金额统一两位小数 */
export function formatAmount(value: number | null | undefined): string {
  if (typeof value !== 'number' || !Number.isFinite(value)) return '—';
  return value.toFixed(2);
}
