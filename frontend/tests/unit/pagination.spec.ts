import { describe, expect, it } from 'vitest';

import { normalizeProductQuery, PRODUCT_KEYWORD_MAX, PRODUCT_SIZE_MAX } from '@/api/product';
import { normalizeSearchQuery, SEARCH_KEYWORD_MAX, SEARCH_SIZE_MAX } from '@/api/search';
import { normalizeAdminProductQuery } from '@/api/admin';

describe('分页与参数收敛', () => {
  it('商品接口 size 收敛到 1–100', () => {
    expect(normalizeProductQuery({ size: 0 }).size).toBe(1);
    expect(normalizeProductQuery({ size: 101 }).size).toBe(100);
    expect(normalizeProductQuery({ size: -5 }).size).toBe(1);
    expect(normalizeProductQuery({ page: 0 }).page).toBe(1);
    expect(normalizeProductQuery({ page: -3 }).page).toBe(1);
    expect(PRODUCT_SIZE_MAX).toBe(100);
  });

  it('商品 keyword 截断到 64', () => {
    const long = 'x'.repeat(100);
    expect(normalizeProductQuery({ keyword: long }).keyword).toHaveLength(PRODUCT_KEYWORD_MAX);
    expect(normalizeProductQuery({ keyword: '  跑步  ' }).keyword).toBe('跑步');
    expect(normalizeProductQuery({ keyword: '   ' }).keyword).toBeUndefined();
  });

  it('categoryId 只接受正数', () => {
    expect(normalizeProductQuery({ categoryId: 0 }).categoryId).toBeUndefined();
    expect(normalizeProductQuery({ categoryId: -1 }).categoryId).toBeUndefined();
    expect(normalizeProductQuery({ categoryId: 1001 }).categoryId).toBe(1001);
  });

  it('搜索接口 size 上限是 50，不复用商品的 100', () => {
    expect(SEARCH_SIZE_MAX).toBe(50);
    expect(normalizeSearchQuery({ keyword: '鞋', size: 100 }).size).toBe(50);
    expect(normalizeSearchQuery({ keyword: '鞋', size: 0 }).size).toBe(1);
    expect(normalizeSearchQuery({ keyword: '鞋' }).size).toBe(10);
    expect(normalizeSearchQuery({ keyword: '鞋' }).page).toBe(1);
  });

  it('搜索 keyword 截断到 128', () => {
    const long = 'x'.repeat(200);
    expect(normalizeSearchQuery({ keyword: long }).keyword).toHaveLength(SEARCH_KEYWORD_MAX);
  });

  it('管理端不选状态时请求参数不含 status', () => {
    const params = normalizeAdminProductQuery({ page: 1, size: 10 });
    expect(params).not.toHaveProperty('status');
  });

  it('管理端 status=0 会被保留（不能当作未传）', () => {
    const params = normalizeAdminProductQuery({ status: 0 });
    expect(params.status).toBe('0');
    expect(normalizeAdminProductQuery({ status: 1 }).status).toBe('1');
  });
});
