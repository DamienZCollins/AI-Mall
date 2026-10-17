import { http } from './http';
import type { PageResult } from './types';

/** 商品模型字段与 docs/接口文档.md 5.1 一致 */
export interface Product {
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

export interface ProductQuery {
  page?: number;
  size?: number;
  keyword?: string;
  categoryId?: number;
}

export const PRODUCT_SIZE_MAX = 100;
export const PRODUCT_KEYWORD_MAX = 64;

/** 把越界参数收敛到后端允许的范围，避免把非法值发出去 */
export function normalizeProductQuery(query: ProductQuery = {}): ProductQuery {
  const page = Math.max(1, Math.trunc(query.page ?? 1));
  const size = Math.min(PRODUCT_SIZE_MAX, Math.max(1, Math.trunc(query.size ?? 10)));
  const result: ProductQuery = { page, size };

  const keyword = query.keyword?.trim();
  if (keyword) {
    result.keyword = keyword.slice(0, PRODUCT_KEYWORD_MAX);
  }

  if (typeof query.categoryId === 'number' && query.categoryId > 0) {
    result.categoryId = Math.trunc(query.categoryId);
  }

  return result;
}

export function queryProducts(query: ProductQuery = {}, signal?: AbortSignal): Promise<PageResult<Product>> {
  return http.get<PageResult<Product>>('/api/products', normalizeProductQuery(query) as Record<string, unknown>, {
    signal,
  });
}

export function fetchProduct(id: number, signal?: AbortSignal): Promise<Product> {
  return http.get<Product>(`/api/products/${id}`, undefined, { signal });
}
