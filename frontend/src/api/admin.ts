import { http } from './http';
import type { PageResult } from './types';
import type { ActivityStatus, SeckillActivity } from './seckill';

export const ADMIN_PRODUCT_SIZE_MAX = 100;
export const ADMIN_PRODUCT_KEYWORD_MAX = 64;

export interface AdminProductQuery {
  page?: number;
  size?: number;
  keyword?: string;
  categoryId?: number;
  /** 不传表示全部；用 undefined 表达而不是空字符串 */
  status?: 0 | 1;
}

export interface AdminProductPayload {
  name: string;
  brand?: string | null;
  categoryId: number;
  description?: string | null;
  sellingPoint?: string | null;
  price: number;
  stock: number;
  imageUrl?: string | null;
}

/** 编辑接口不接受 status，用类型禁止通过编辑改上下架 */
export type AdminProductUpdatePayload = Partial<Omit<AdminProductPayload, 'status'>>;

export interface AdminProductRow {
  id: number;
  name: string;
  brand: string | null;
  categoryId: number;
  description: string | null;
  sellingPoint: string | null;
  price: number;
  stock: number;
  status: 0 | 1;
  imageUrl: string | null;
}

export function normalizeAdminProductQuery(query: AdminProductQuery): Record<string, string> {
  const params: Record<string, string> = {
    page: String(Math.max(1, Math.trunc(query.page ?? 1))),
    size: String(Math.min(ADMIN_PRODUCT_SIZE_MAX, Math.max(1, Math.trunc(query.size ?? 10)))),
  };
  const keyword = query.keyword?.trim();
  if (keyword) params.keyword = keyword.slice(0, ADMIN_PRODUCT_KEYWORD_MAX);
  if (typeof query.categoryId === 'number' && query.categoryId > 0) {
    params.categoryId = String(Math.trunc(query.categoryId));
  }
  // 不选状态时请求 URL 不含 status=
  if (query.status === 0 || query.status === 1) {
    params.status = String(query.status);
  }
  return params;
}

export function listAdminProducts(
  query: AdminProductQuery = {},
  signal?: AbortSignal
): Promise<PageResult<AdminProductRow>> {
  return http.get<PageResult<AdminProductRow>>(
    '/api/admin/products',
    normalizeAdminProductQuery(query),
    { signal }
  );
}

export function createAdminProduct(payload: AdminProductPayload): Promise<AdminProductRow> {
  return http.post<AdminProductRow>('/api/admin/products', payload);
}

export function updateAdminProduct(id: number, payload: AdminProductUpdatePayload): Promise<AdminProductRow> {
  return http.put<AdminProductRow>(`/api/admin/products/${id}`, payload);
}

export function changeProductStatus(id: number, status: 0 | 1): Promise<AdminProductRow> {
  return http.patch<AdminProductRow>(`/api/admin/products/${id}/status`, { status });
}

export function uploadProductImage(
  file: File,
  onProgress?: (percent: number) => void
): Promise<string> {
  return http.upload<string>('/api/admin/products/images', file, { timeout: 60_000, onProgress });
}

/** 全量重建索引：后端没有幂等保护，前端需禁用重复点击；耗时较长单独设置超时 */
export function reindexSearch(): Promise<number> {
  return http.post<number>('/api/admin/search/reindex', undefined, { timeout: 120_000 });
}

export interface AdminActivityQuery {
  page?: number;
  size?: number;
  runningOnly?: boolean;
}

export interface AdminActivityCreatePayload {
  productId: number;
  title: string;
  seckillPrice: number;
  totalStock: number;
  perUserLimit?: number;
  startTime: string;
  endTime: string;
  payTimeoutMinutes?: number;
}

/** 编辑只接受标题、时间与配置开关 status（1 启用 / 0 停用） */
export interface AdminActivityUpdatePayload {
  title?: string;
  startTime?: string;
  endTime?: string;
  status?: 0 | 1;
}

export function listAdminActivities(
  query: AdminActivityQuery = {},
  signal?: AbortSignal
): Promise<PageResult<SeckillActivity>> {
  const params: Record<string, string> = {
    page: String(Math.max(1, Math.trunc(query.page ?? 1))),
    size: String(Math.min(100, Math.max(1, Math.trunc(query.size ?? 10)))),
  };
  if (query.runningOnly === true) params.runningOnly = 'true';
  return http.get<PageResult<SeckillActivity>>('/api/admin/seckill/activities', params, { signal });
}

export function createAdminActivity(payload: AdminActivityCreatePayload): Promise<SeckillActivity> {
  return http.post<SeckillActivity>('/api/admin/seckill/activities', payload);
}

export function updateAdminActivity(
  activityId: number,
  payload: AdminActivityUpdatePayload
): Promise<SeckillActivity> {
  return http.put<SeckillActivity>(`/api/admin/seckill/activities/${activityId}`, payload);
}

export type { ActivityStatus };
