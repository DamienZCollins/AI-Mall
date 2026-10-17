import axios, { AxiosError, type AxiosInstance, type AxiosRequestConfig } from 'axios';

import { ApiError, type ApiResponse, isApiError } from './types';

const DEFAULT_TIMEOUT = 10_000;

function resolveBaseUrl(): string {
  const baseUrl = import.meta.env.VITE_API_BASE_URL;
  if (!baseUrl) {
    throw new Error('缺少 VITE_API_BASE_URL，请在 .env.local 中配置后端服务地址');
  }
  return baseUrl;
}

/** 401 由 HTTP 客户端统一处理：清空登录态并跳转登录页 */
type UnauthorizedHandler = (redirectPath: string) => void;

let unauthorizedHandler: UnauthorizedHandler | null = null;
let lastUnauthorizedAt = 0;

export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  unauthorizedHandler = handler;
}

function handleUnauthorized(redirectPath: string): void {
  const now = Date.now();
  if (now - lastUnauthorizedAt < 1000) {
    return;
  }
  lastUnauthorizedAt = now;
  unauthorizedHandler?.(redirectPath);
}

function currentRedirectPath(): string {
  const { pathname, search, hash } = window.location;
  return `${pathname}${search}${hash}`;
}

function toApiError(error: unknown): ApiError {
  if (isApiError(error)) {
    return error;
  }

  if (axios.isCancel(error)) {
    return new ApiError({
      code: -1,
      message: '请求已取消',
      httpStatus: null,
      kind: 'canceled',
    });
  }

  const axiosError = error as AxiosError<ApiResponse<unknown>>;

  if (axiosError.code === 'ECONNABORTED' || axiosError.code === 'ETIMEDOUT') {
    return new ApiError({
      code: -1,
      message: '请求超时，请重试',
      httpStatus: null,
      kind: 'timeout',
    });
  }

  if (!axiosError.response) {
    return new ApiError({
      code: -1,
      message: '网络异常，请检查网络后重试',
      httpStatus: null,
      kind: 'network',
    });
  }

  const { status, data } = axiosError.response;
  const body = (data ?? {}) as Partial<ApiResponse<unknown>>;
  return new ApiError({
    code: typeof body.code === 'number' ? body.code : status,
    message: body.message || `请求失败（HTTP ${status}）`,
    httpStatus: status,
    kind: 'http',
    data: body.data ?? null,
  });
}

const instance: AxiosInstance = axios.create({
  timeout: DEFAULT_TIMEOUT,
  headers: { 'Content-Type': 'application/json' },
});

instance.interceptors.request.use((config) => {
  config.baseURL = resolveBaseUrl();
  const token = localStorage.getItem('mall.token');
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
});

instance.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown>;
    // 先看 HTTP 状态码，再看业务码：业务失败也可能返回 HTTP 200
    if (body && typeof body.code === 'number' && body.code !== 0) {
      throw new ApiError({
        code: body.code,
        message: body.message || '请求失败',
        httpStatus: response.status,
        kind: 'business',
        data: body.data ?? null,
      });
    }
    return response;
  },
  (error) => {
    const apiError = toApiError(error);

    if (apiError.httpStatus === 401) {
      handleUnauthorized(currentRedirectPath());
      throw new ApiError({
        code: 401,
        message: apiError.message || '登录已过期，请重新登录',
        httpStatus: 401,
        kind: 'http',
      });
    }

    throw apiError;
  }
);

export interface RequestOptions {
  signal?: AbortSignal;
  timeout?: number;
}

async function request<T>(config: AxiosRequestConfig, options: RequestOptions = {}): Promise<T> {
  const merged: AxiosRequestConfig = { ...config };
  if (options.signal) {
    merged.signal = options.signal;
  }
  if (typeof options.timeout === 'number') {
    merged.timeout = options.timeout;
  }

  try {
    const response = await instance.request<ApiResponse<T>>(merged);
    return response.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

export const http = {
  get<T>(url: string, params?: Record<string, unknown>, options?: RequestOptions): Promise<T> {
    return request<T>({ method: 'GET', url, params }, options);
  },
  post<T>(url: string, data?: unknown, options?: RequestOptions): Promise<T> {
    return request<T>({ method: 'POST', url, data }, options);
  },
  put<T>(url: string, data?: unknown, options?: RequestOptions): Promise<T> {
    return request<T>({ method: 'PUT', url, data }, options);
  },
  patch<T>(url: string, data?: unknown, options?: RequestOptions): Promise<T> {
    return request<T>({ method: 'PATCH', url, data }, options);
  },
  delete<T>(url: string, options?: RequestOptions): Promise<T> {
    return request<T>({ method: 'DELETE', url }, options);
  },
  upload<T>(url: string, file: File, options?: RequestOptions & { onProgress?: (percent: number) => void }): Promise<T> {
    const form = new FormData();
    form.append('file', file);
    return request<T>(
      {
        method: 'POST',
        url,
        data: form,
        headers: { 'Content-Type': undefined },
        onUploadProgress: (event) => {
          if (!options?.onProgress || !event.total) return;
          options.onProgress(Math.round((event.loaded / event.total) * 100));
        },
      },
      options
    );
  },
};

export { ApiError, isApiError };
