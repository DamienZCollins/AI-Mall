import { http } from './http';
import type { PageResult } from './types';

/** 活动状态：0 未开始，1 进行中，2 已结束，3 已关闭 */
export type ActivityStatus = 0 | 1 | 2 | 3;
/** 订单状态：1 待支付，2 已支付，3 已取消，4 超时取消 */
export type OrderStatus = 1 | 2 | 3 | 4;
export type PurchaseState = 'QUEUING' | 'SUCCESS' | 'FAILED';

export interface SeckillActivity {
  id: number;
  productId: number;
  title: string;
  seckillPrice: number;
  originalPrice: number;
  totalStock: number;
  remainingStock: number;
  perUserLimit: number;
  startTime: string;
  endTime: string;
  status: ActivityStatus;
}

export interface SeckillResult {
  state: PurchaseState;
  orderNo: string | null;
  productName: string | null;
  seckillPrice: number | null;
  status: OrderStatus | null;
  expireTime: string | null;
}

export interface SeckillOrder {
  orderNo: string;
  activityId: number;
  productId: number;
  productName: string;
  seckillPrice: number;
  status: OrderStatus;
  expireTime: string;
  payTime: string | null;
}

export interface ActivityQuery {
  page?: number;
  size?: number;
  runningOnly?: boolean;
}

export const ACTIVITY_SIZE_MAX = 100;

function normalizeActivityQuery(query: ActivityQuery): Record<string, string> {
  const params: Record<string, string> = {
    page: String(Math.max(1, Math.trunc(query.page ?? 1))),
    size: String(Math.min(ACTIVITY_SIZE_MAX, Math.max(1, Math.trunc(query.size ?? 10)))),
  };
  // runningOnly 仅在为 true 时拼进查询串
  if (query.runningOnly === true) {
    params.runningOnly = 'true';
  }
  return params;
}

export function listActivities(
  query: ActivityQuery = {},
  signal?: AbortSignal
): Promise<PageResult<SeckillActivity>> {
  return http.get<PageResult<SeckillActivity>>(
    '/api/seckill/activities',
    normalizeActivityQuery(query),
    { signal }
  );
}

export function getActivity(activityId: number, signal?: AbortSignal): Promise<SeckillActivity> {
  return http.get<SeckillActivity>(`/api/seckill/activities/${activityId}`, undefined, { signal });
}

/** 抢购：返回 code === 0 仅表示 Redis 已受理，订单由消费者异步创建 */
export function placeOrder(activityId: number): Promise<void> {
  return http.post<null>(`/api/seckill/${activityId}`).then(() => undefined);
}

export function getResult(activityId: number, signal?: AbortSignal): Promise<SeckillResult> {
  return http.get<SeckillResult>(`/api/seckill/${activityId}/result`, undefined, { signal });
}

export function getOrder(orderNo: string, signal?: AbortSignal): Promise<SeckillOrder> {
  return http.get<SeckillOrder>(`/api/seckill/orders/${encodeURIComponent(orderNo)}`, undefined, { signal });
}

export function payOrder(orderNo: string): Promise<SeckillOrder> {
  return http.post<SeckillOrder>(`/api/seckill/orders/${encodeURIComponent(orderNo)}/pay`);
}
