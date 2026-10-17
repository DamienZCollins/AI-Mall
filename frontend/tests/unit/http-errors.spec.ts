import { describe, expect, it } from 'vitest';

import { ApiError, BIZ_CODE, isApiError } from '@/api/types';

/**
 * HTTP 错误解析规则：业务失败可能返回 HTTP 200，
 * 因此每个请求都必须先看 HTTP 状态码，再看 code。
 * 这里验证错误类型本身的分支不混淆。
 */
describe('ApiError 分类', () => {
  it('业务错误携带 code 与 kind=business', () => {
    const error = new ApiError({ code: BIZ_CODE.SOLD_OUT, message: '秒杀商品已售罄', httpStatus: 200, kind: 'business' });
    expect(error.code).toBe(4004);
    expect(error.kind).toBe('business');
    expect(error.httpStatus).toBe(200);
    expect(error.message).toBe('秒杀商品已售罄');
  });

  it('HTTP 200 + code !== 0 与 HTTP 400 走不同分支', () => {
    const business = new ApiError({ code: 2001, message: '商品不存在', httpStatus: 200, kind: 'business' });
    const badRequest = new ApiError({ code: 400, message: '请求参数不合法', httpStatus: 400, kind: 'http' });
    expect(business.kind).not.toBe(badRequest.kind);
    expect(business.httpStatus).toBe(200);
    expect(badRequest.httpStatus).toBe(400);
  });

  it('401 / 403 / 超时 / 取消 / 网络互不混淆', () => {
    const unauthorized = new ApiError({ code: 401, message: '未认证', httpStatus: 401, kind: 'http' });
    const forbidden = new ApiError({ code: 403, message: '权限不足', httpStatus: 403, kind: 'http' });
    const timeout = new ApiError({ code: -1, message: '请求超时', httpStatus: null, kind: 'timeout' });
    const canceled = new ApiError({ code: -1, message: '取消', httpStatus: null, kind: 'canceled' });
    const network = new ApiError({ code: -1, message: '网络异常', httpStatus: null, kind: 'network' });

    expect(unauthorized.code).toBe(401);
    expect(forbidden.code).toBe(403);
    expect(timeout.kind).toBe('timeout');
    expect(canceled.kind).toBe('canceled');
    expect(network.kind).toBe('network');
    // 超时与网络错误都不能被当作业务结果
    expect([timeout.kind, network.kind]).not.toContain('business');
  });

  it('isApiError 只识别 ApiError', () => {
    expect(isApiError(new ApiError({ code: 0, message: '', httpStatus: 200, kind: 'business' }))).toBe(true);
    expect(isApiError(new Error('普通错误'))).toBe(false);
    expect(isApiError(null)).toBe(false);
  });

  it('业务码常量与接口文档一致', () => {
    expect(BIZ_CODE.LOGIN_FAILED).toBe(1001);
    expect(BIZ_CODE.PRODUCT_NOT_FOUND).toBe(2001);
    expect(BIZ_CODE.PRODUCT_STATUS_INVALID).toBe(2002);
    expect(BIZ_CODE.IMAGE_UPLOAD_FAILED).toBe(2003);
    expect(BIZ_CODE.EMBEDDING_FAILED).toBe(3001);
    expect(BIZ_CODE.SEARCH_UNAVAILABLE).toBe(3002);
    expect(BIZ_CODE.ACTIVITY_NOT_FOUND).toBe(4001);
    expect(BIZ_CODE.SECKILL_PRICE_INVALID).toBe(4009);
    expect(BIZ_CODE.QUEUE_UNAVAILABLE).toBe(4010);
    expect(BIZ_CODE.PURCHASE_PROCESSING).toBe(4011);
  });
});
