import type { PageResult } from '@/api/types';
import {
  fetchProduct as fetchProductApi,
  queryProducts as queryProductsApi,
  PRODUCT_KEYWORD_MAX,
  type Product,
  type ProductQuery,
} from '@/api/product';
import { CATALOG_CATEGORIES, CATALOG_PRODUCTS } from './products';

/**
 * 商品数据源开关。
 *
 * - `backend`：走 `GET /api/products`，商品图依赖后端 `imageUrl`（默认）
 * - `local`：走仓库 `素材/` 生成的本地目录，用于在商品图入库前验收视觉
 *
 * 由 `VITE_CATALOG_MODE` 控制；`.env.local` 里设为 `local` 即可预览素材目录，
 * 端到端测试固定用 `backend`，避免断言依赖本地目录内容。
 */
export type CatalogMode = 'backend' | 'local';

export const CATALOG_MODE: CatalogMode =
  import.meta.env.VITE_CATALOG_MODE === 'local' ? 'local' : 'backend';

export const IS_LOCAL_CATALOG = CATALOG_MODE === 'local';

/**
 * 启动时在控制台标注当前数据源。
 * 进程环境变量优先于 .env.local，容易在调试后残留成 backend，
 * 导致页面全部显示占位图；这行日志用于快速判断是不是数据源不对。
 */
if (import.meta.env.DEV) {
  const hint =
    CATALOG_MODE === 'local'
      ? '本地素材目录（public/images/products）'
      : '后端接口 GET /api/products（商品图依赖 imageUrl）';
  console.warn(`[catalog] 商品数据源：${hint}`);
}

/** 本地目录只做展示，不参与下单与秒杀 */
export function categoryNameOf(categoryId: number): string {
  return CATALOG_CATEGORIES.find((item) => item.id === categoryId)?.name ?? `分类 ${categoryId}`;
}

function matchKeyword(product: Product, keyword: string): boolean {
  const needle = keyword.trim().toLowerCase();
  if (!needle) return true;
  const haystack = [product.name, product.brand, product.sellingPoint, product.description]
    .filter(Boolean)
    .join(' ')
    .toLowerCase();
  return haystack.includes(needle);
}

/** 本地目录的列表查询：与后端一致的分页结构，页码从 1 开始 */
export function queryLocalProducts(query: ProductQuery = {}): PageResult<Product> {
  const page = Math.max(1, Math.trunc(query.page ?? 1));
  const size = Math.min(100, Math.max(1, Math.trunc(query.size ?? 10)));
  const keyword = (query.keyword ?? '').trim().slice(0, PRODUCT_KEYWORD_MAX);
  const categoryId = typeof query.categoryId === 'number' && query.categoryId > 0 ? query.categoryId : undefined;

  const filtered = CATALOG_PRODUCTS.filter((product) => {
    if (product.status !== 1) return false;
    if (categoryId && product.categoryId !== categoryId) return false;
    return matchKeyword(product, keyword);
  });

  const start = (page - 1) * size;
  return {
    total: filtered.length,
    page,
    size,
    list: filtered.slice(start, start + size),
  };
}

export function fetchLocalProduct(id: number): Product | null {
  return CATALOG_PRODUCTS.find((product) => product.id === id && product.status === 1) ?? null;
}

/** 页面统一入口：按开关选择数据源，签名与后端函数保持一致 */
export function queryProducts(query: ProductQuery = {}, signal?: AbortSignal): Promise<PageResult<Product>> {
  if (IS_LOCAL_CATALOG) {
    return Promise.resolve(queryLocalProducts(query));
  }
  return queryProductsApi(query, signal);
}

export async function fetchProduct(id: number, signal?: AbortSignal): Promise<Product> {
  if (IS_LOCAL_CATALOG) {
    const product = fetchLocalProduct(id);
    if (!product) {
      // 与后端 2001 保持同一错误形态，页面无需分支
      const { ApiError, BIZ_CODE } = await import('@/api/types');
      throw new ApiError({
        code: BIZ_CODE.PRODUCT_NOT_FOUND,
        message: '商品不存在',
        httpStatus: 200,
        kind: 'business',
      });
    }
    return product;
  }
  return fetchProductApi(id, signal);
}

export { CATALOG_CATEGORIES, CATALOG_PRODUCTS };
