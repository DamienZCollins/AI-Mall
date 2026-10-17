import { describe, expect, it } from 'vitest';

import {
  nonNegativeInteger,
  normalizedAmount,
  optionalText,
  positiveAmount,
  positiveInteger,
  requiredText,
  validateActivityForm,
  validateProductForm,
} from '@/utils/validation';

describe('通用校验规则', () => {
  it('requiredText 按字符数计长度', () => {
    expect(requiredText('', '商品名称', 128).valid).toBe(false);
    expect(requiredText('   ', '商品名称', 128).message).toBe('商品名称不能为空');
    expect(requiredText('x'.repeat(128), '商品名称', 128).valid).toBe(true);
    expect(requiredText('x'.repeat(129), '商品名称', 128).message).toBe('商品名称长度不能超过 128');
    expect(requiredText('中'.repeat(128), '商品名称', 128).valid).toBe(true);
  });

  it('positiveAmount 与 @DecimalMin + @Digits 口径一致', () => {
    expect(positiveAmount(0, '售价').valid).toBe(false);
    expect(positiveAmount(0.01, '售价').valid).toBe(true);
    expect(positiveAmount('199.999', '售价').valid).toBe(false);
    expect(positiveAmount('123456789', '售价').valid).toBe(false);
    expect(positiveAmount('12345678.99', '售价').valid).toBe(true);
  });

  it('integer 校验拒绝负数与小数', () => {
    expect(positiveInteger(0, '总量').valid).toBe(false);
    expect(positiveInteger(1.5, '总量').valid).toBe(false);
    expect(positiveInteger('3', '总量').valid).toBe(true);
    expect(nonNegativeInteger(0, '库存').valid).toBe(true);
    expect(nonNegativeInteger(-1, '库存').message).toBe('库存不能为负数');
  });

  it('optionalText 允许空值但限制长度', () => {
    expect(optionalText('', '品牌', 64).valid).toBe(true);
    expect(optionalText('x'.repeat(65), '品牌', 64).valid).toBe(false);
  });

  it('normalizedAmount 拒绝非法输入', () => {
    expect(normalizedAmount('')).toBeNull();
    expect(normalizedAmount('1e3')).toBeNull();
    expect(normalizedAmount('12.345')).toBeNull();
    expect(normalizedAmount('12.34')).toBe(12.34);
  });
});

describe('商品表单校验', () => {
  const base = {
    name: '轻量网面透气跑步鞋',
    brand: '沃兰',
    categoryId: '1001',
    description: '适合日常通勤与轻量跑步。',
    sellingPoint: '网面透气 轻量缓震',
    price: '399.00',
    stock: '100',
    imageUrl: '',
  };

  it('合法表单无错误', () => {
    expect(validateProductForm(base)).toEqual({});
  });

  it('空名称被拦截', () => {
    expect(validateProductForm({ ...base, name: '  ' }).name).toBe('商品名称不能为空');
  });

  it('超长品牌与描述被拦截', () => {
    const errors = validateProductForm({ ...base, brand: 'x'.repeat(65), description: 'x'.repeat(2001) });
    expect(errors.brand).toBe('品牌长度不能超过 64');
    expect(errors.description).toBe('商品描述长度不能超过 2000');
  });

  it('分类必须为正整数', () => {
    expect(validateProductForm({ ...base, categoryId: '0' }).categoryId).toBe('分类必须大于 0');
    expect(validateProductForm({ ...base, categoryId: 'abc' }).categoryId).toBe('分类不能为空');
  });

  it('售价与库存边界', () => {
    expect(validateProductForm({ ...base, price: '0' }).price).toBe('售价必须大于 0');
    expect(validateProductForm({ ...base, price: '1.234' }).price).toBe('售价格式不正确');
    expect(validateProductForm({ ...base, stock: '-1' }).stock).toBe('库存不能为负数');
  });

  it('图片 URL 最长 512', () => {
    expect(validateProductForm({ ...base, imageUrl: 'x'.repeat(513) }).imageUrl).toBe('图片 URL 长度不能超过 512');
  });
});

describe('活动表单校验', () => {
  const base = {
    productId: '1',
    title: '限时秒杀',
    seckillPrice: '199.00',
    totalStock: '10',
    perUserLimit: '1',
    startTime: '2026-10-17T10:00',
    endTime: '2026-10-17T12:00',
    payTimeoutMinutes: '15',
    originalPrice: 399,
  };

  it('合法表单无错误', () => {
    expect(validateActivityForm(base)).toEqual({});
  });

  it('结束时间不晚于开始时间返回 4008 同位文案', () => {
    const errors = validateActivityForm({ ...base, endTime: '2026-10-17T10:00' });
    expect(errors.endTime).toBe('结束时间必须晚于开始时间');
  });

  it('秒杀价不得高于等于原价（4009 口径）', () => {
    expect(validateActivityForm({ ...base, seckillPrice: '399.00' }).seckillPrice).toBe('秒杀价必须低于商品原价');
    expect(validateActivityForm({ ...base, seckillPrice: '400.00' }).seckillPrice).toBe('秒杀价必须低于商品原价');
    expect(validateActivityForm({ ...base, seckillPrice: '398.99' }).seckillPrice).toBeUndefined();
  });

  it('总量必须大于 0，限购与超时可为空', () => {
    expect(validateActivityForm({ ...base, totalStock: '0' }).totalStock).toBe('活动总量必须大于 0');
    expect(validateActivityForm({ ...base, perUserLimit: '', payTimeoutMinutes: '' })).toEqual({});
  });

  it('商品与标题必填', () => {
    const errors = validateActivityForm({ ...base, productId: '', title: ' ' });
    expect(errors.productId).toBe('请选择上架商品');
    expect(errors.title).toBe('活动标题不能为空');
  });
});
