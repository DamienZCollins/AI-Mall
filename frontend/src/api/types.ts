/** 统一响应信封：code === 0 才表示业务成功 */
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

/** 分页结构：页码从 1 开始 */
export interface PageResult<T> {
  total: number;
  page: number;
  size: number;
  list: T[];
}

/** 错误分类：业务码、HTTP 错误、超时、网络异常、主动取消 */
export type ApiErrorKind = 'business' | 'http' | 'timeout' | 'network' | 'canceled';

export interface ApiErrorOptions {
  code: number;
  message: string;
  httpStatus: number | null;
  kind: ApiErrorKind;
  data?: unknown;
}

/** 所有请求失败都以该类型抛出，页面按 code 与 kind 分支 */
export class ApiError extends Error {
  readonly code: number;
  readonly httpStatus: number | null;
  readonly kind: ApiErrorKind;
  readonly data: unknown;

  constructor(options: ApiErrorOptions) {
    super(options.message);
    this.name = 'ApiError';
    this.code = options.code;
    this.httpStatus = options.httpStatus;
    this.kind = options.kind;
    this.data = options.data;
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

/** 业务码常量，避免页面上出现魔法数字 */
export const BIZ_CODE = {
  SUCCESS: 0,
  BAD_REQUEST: 400,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  SERVER_ERROR: 500,
  LOGIN_FAILED: 1001,
  USER_NOT_FOUND: 1002,
  USER_DISABLED: 1003,
  PRODUCT_NOT_FOUND: 2001,
  PRODUCT_STATUS_INVALID: 2002,
  IMAGE_UPLOAD_FAILED: 2003,
  EMBEDDING_FAILED: 3001,
  SEARCH_UNAVAILABLE: 3002,
  ACTIVITY_NOT_FOUND: 4001,
  ACTIVITY_NOT_STARTED: 4002,
  ACTIVITY_ENDED: 4003,
  SOLD_OUT: 4004,
  PER_USER_LIMIT: 4005,
  ORDER_NOT_FOUND: 4006,
  ORDER_STATUS_INVALID: 4007,
  ACTIVITY_TIME_INVALID: 4008,
  SECKILL_PRICE_INVALID: 4009,
  QUEUE_UNAVAILABLE: 4010,
  PURCHASE_PROCESSING: 4011,
} as const;
