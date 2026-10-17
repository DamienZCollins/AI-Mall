import { describe, expect, it } from 'vitest';

import { activityStatusMeta, orderStatusMeta, resolveActionButton } from '@/features/seckill/seckillLabels';
import { describePurchaseError } from '@/features/seckill/useSeckillPurchase';
import { ApiError } from '@/api/types';

describe('秒杀状态文案', () => {
  it('四种活动状态各有独立文案', () => {
    expect(activityStatusMeta(0).label).toBe('未开始');
    expect(activityStatusMeta(1).label).toBe('进行中');
    expect(activityStatusMeta(2).label).toBe('已结束');
    expect(activityStatusMeta(3).label).toBe('已关闭');
    const labels = [0, 1, 2, 3].map((status) => activityStatusMeta(status as 0 | 1 | 2 | 3).label);
    expect(new Set(labels).size).toBe(4);
  });

  it('四种订单状态各有独立文案，终态不含待支付', () => {
    expect(orderStatusMeta(1).label).toBe('待支付');
    expect(orderStatusMeta(2).label).toBe('已支付');
    expect(orderStatusMeta(3).label).toBe('已取消');
    expect(orderStatusMeta(4).label).toBe('超时取消');
    expect(orderStatusMeta(1).terminal).toBe(false);
    expect(orderStatusMeta(2).terminal).toBe(true);
    expect(orderStatusMeta(3).terminal).toBe(true);
    expect(orderStatusMeta(4).terminal).toBe(true);
  });

  it('按钮矩阵：只有进行中且有库存才可点击', () => {
    expect(resolveActionButton(0, 10)).toEqual({ text: '未开始', clickable: false, hint: expect.any(String) });
    expect(resolveActionButton(1, 10).text).toBe('立即抢购');
    expect(resolveActionButton(1, 10).clickable).toBe(true);
    expect(resolveActionButton(1, 0)).toEqual({ text: '已售罄', clickable: false, hint: '本场库存已抢完。' });
    expect(resolveActionButton(2, 10).clickable).toBe(false);
    expect(resolveActionButton(3, 10).clickable).toBe(false);
  });
});

describe('抢购错误分支', () => {
  const make = (code: number, message = '') =>
    new ApiError({ code, message, httpStatus: 200, kind: 'business' });

  it('六个业务码各有独立文案', () => {
    const titles = [4002, 4003, 4004, 4005, 4010, 4011].map((code) => describePurchaseError(make(code)).title);
    expect(new Set(titles).size).toBe(6);
  });

  it('4004 售罄与 4005 限购不允许立即重试', () => {
    expect(describePurchaseError(make(4004)).canRetry).toBe(false);
    expect(describePurchaseError(make(4005)).canRetry).toBe(false);
    expect(describePurchaseError(make(4005)).title).toBe('已达限购数量');
  });

  it('4010 允许重新发起抢购', () => {
    const info = describePurchaseError(make(4010));
    expect(info.canRetry).toBe(true);
    expect(info.goToResult).toBe(false);
  });

  it('4011 转入结果页查询，不允许重复提交', () => {
    const info = describePurchaseError(make(4011));
    expect(info.goToResult).toBe(true);
    expect(info.canRetry).toBe(false);
  });

  it('超时与网络错误不能降级成业务失败', () => {
    const timeout = describePurchaseError(
      new ApiError({ code: -1, message: '超时', httpStatus: null, kind: 'timeout' })
    );
    const network = describePurchaseError(
      new ApiError({ code: -1, message: '网络', httpStatus: null, kind: 'network' })
    );
    expect(timeout.title).toBe('抢购超时');
    expect(network.title).toBe('网络异常');
    expect(new Set([timeout.title, network.title]).size).toBe(2);
  });
});
