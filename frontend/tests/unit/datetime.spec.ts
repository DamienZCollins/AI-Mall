import { describe, expect, it } from 'vitest';

import { fromLocalDateTimeInput, formatAmount, formatDateTime, parseServerTime, toLocalDateTimeInput } from '@/utils/datetime';

describe('datetime 工具', () => {
  it('formatDateTime 保留后端本地时间字面值', () => {
    expect(formatDateTime('2026-10-17T10:00:00')).toBe('2026-10-17 10:00');
    expect(formatDateTime(null)).toBe('—');
    expect(formatDateTime('')).toBe('—');
  });

  it('toLocalDateTimeInput 截到分钟，供 datetime-local 控件使用', () => {
    expect(toLocalDateTimeInput('2026-10-17T10:00:00')).toBe('2026-10-17T10:00');
    expect(toLocalDateTimeInput('2026-10-17T10:30:59')).toBe('2026-10-17T10:30');
    expect(toLocalDateTimeInput('')).toBe('');
  });

  it('fromLocalDateTimeInput 补齐秒，不带时区偏移', () => {
    expect(fromLocalDateTimeInput('2026-10-17T10:00')).toBe('2026-10-17T10:00:00');
    expect(fromLocalDateTimeInput('2026-10-17T10:00:30')).toBe('2026-10-17T10:00:30');
    expect(fromLocalDateTimeInput('')).toBeNull();
  });

  it('两个格式化函数可逆，不会因 UTC 转换偏移 8 小时', () => {
    const original = '2026-10-17T10:00:00';
    const roundTrip = fromLocalDateTimeInput(toLocalDateTimeInput(original));
    expect(roundTrip).toBe(original);
  });

  it('parseServerTime 按 Asia/Shanghai 解析', () => {
    const timestamp = parseServerTime('2026-10-17T10:00:00');
    expect(timestamp).not.toBeNull();
    // 东八区 10:00 等于 UTC 02:00
    expect(new Date(timestamp as number).toISOString()).toBe('2026-10-17T02:00:00.000Z');
    expect(parseServerTime(null)).toBeNull();
    expect(parseServerTime('not-a-date')).toBeNull();
  });

  it('formatAmount 固定两位小数', () => {
    expect(formatAmount(199)).toBe('199.00');
    expect(formatAmount(199.5)).toBe('199.50');
    expect(formatAmount(null)).toBe('—');
  });
});
