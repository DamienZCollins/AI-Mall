import { http } from './http';
import type { PageResult } from './types';

/** 搜索结果条目：主键是 productId，字段集合与商品模型不同 */
export interface SearchHit {
  productId: number;
  name: string;
  brand: string | null;
  categoryId: number;
  sellingPoint: string | null;
  price: number;
  stock: number;
  imageUrl: string | null;
  /** RRF 融合分，仅用于调试排序，不作为用户可见评分 */
  score: number;
  /** 该商品文档是否已有语义向量；可能缺失 */
  hasVector?: boolean | null;
}

export interface SearchQuery {
  keyword: string;
  page?: number;
  size?: number;
}

export const SEARCH_SIZE_MAX = 50;
export const SEARCH_KEYWORD_MAX = 128;

/** 搜索的 size 上限是 50，不复用商品的 100 */
export function normalizeSearchQuery(query: SearchQuery): SearchQuery {
  const page = Math.max(1, Math.trunc(query.page ?? 1));
  const size = Math.min(SEARCH_SIZE_MAX, Math.max(1, Math.trunc(query.size ?? 10)));
  return { keyword: query.keyword.trim().slice(0, SEARCH_KEYWORD_MAX), page, size };
}

export function searchProducts(query: SearchQuery, signal?: AbortSignal): Promise<PageResult<SearchHit>> {
  const params: Record<string, string> = {
    keyword: query.keyword.trim().slice(0, SEARCH_KEYWORD_MAX),
    page: String(Math.max(1, Math.trunc(query.page ?? 1))),
    size: String(Math.min(SEARCH_SIZE_MAX, Math.max(1, Math.trunc(query.size ?? 10)))),
  };
  return http.get<PageResult<SearchHit>>('/api/search', params, { signal });
}
