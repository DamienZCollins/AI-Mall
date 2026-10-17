import type { ActivityStatus, OrderStatus } from '@/api/seckill';

/** 活动状态文案与按钮矩阵集中在此，页面不各写一份 */
export interface ActivityStatusMeta {
  label: string;
  badgeClass: string;
  /** 该状态下按钮文案 */
  buttonText: string;
  /** 该状态下按钮是否可点击（库存另判） */
  clickable: boolean;
  /** 说明文案 */
  hint: string;
}

export const ACTIVITY_STATUS_META: Record<ActivityStatus, ActivityStatusMeta> = {
  0: {
    label: '未开始',
    badgeClass: 'badge--muted',
    buttonText: '未开始',
    clickable: false,
    hint: '活动尚未开始，可以稍后再来。',
  },
  1: {
    label: '进行中',
    badgeClass: 'badge--success',
    buttonText: '立即抢购',
    clickable: true,
    hint: '每人限购，抢到后请在支付截止时间内完成支付。',
  },
  2: {
    label: '已结束',
    badgeClass: 'badge--muted',
    buttonText: '已结束',
    clickable: false,
    hint: '本场活动已经结束，可以关注其他场次。',
  },
  3: {
    label: '已关闭',
    badgeClass: 'badge--danger',
    buttonText: '已关闭',
    clickable: false,
    hint: '活动已被停用，暂时无法参与。',
  },
};

export interface OrderStatusMeta {
  label: string;
  badgeClass: string;
  terminal: boolean;
}

export const ORDER_STATUS_META: Record<OrderStatus, OrderStatusMeta> = {
  1: { label: '待支付', badgeClass: 'badge--warning', terminal: false },
  2: { label: '已支付', badgeClass: 'badge--success', terminal: true },
  3: { label: '已取消', badgeClass: 'badge--muted', terminal: true },
  4: { label: '超时取消', badgeClass: 'badge--danger', terminal: true },
};

export function activityStatusMeta(status: ActivityStatus): ActivityStatusMeta {
  return ACTIVITY_STATUS_META[status] ?? ACTIVITY_STATUS_META[0];
}

export function orderStatusMeta(status: OrderStatus): OrderStatusMeta {
  return ORDER_STATUS_META[status] ?? ORDER_STATUS_META[1];
}

/** 抢购结果状态文案：FAILED 不等于「抢购失败」，从未参与同样落到 FAILED */
export const PURCHASE_STATE_LABEL: Record<string, string> = {
  QUEUING: '排队中',
  SUCCESS: '抢购成功',
  FAILED: '未查询到抢购记录',
};

/** 按钮文案矩阵：只有「进行中 + 有库存」才可点击 */
export function resolveActionButton(
  status: ActivityStatus,
  remainingStock: number
): { text: string; clickable: boolean; hint: string } {
  const meta = activityStatusMeta(status);
  if (status === 1 && remainingStock <= 0) {
    return { text: '已售罄', clickable: false, hint: '本场库存已抢完。' };
  }
  return { text: meta.buttonText, clickable: meta.clickable, hint: meta.hint };
}
