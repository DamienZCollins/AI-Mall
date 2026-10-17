import { isApiError, type ApiError } from '@/api/types';

export interface ErrorCopy {
  title: string;
  description: string;
  retryable: boolean;
}

/**
 * 把 ApiError 翻译成页面文案：网络错误、超时、业务错误互不混淆。
 * 页面只按业务码补充更具体的文案，通用分支集中在这里。
 */
export function describeError(error: unknown, overrides: Record<number, ErrorCopy> = {}): ErrorCopy {
  if (isApiError(error)) {
    const override = overrides[error.code];
    if (override) {
      return override;
    }

    switch (error.kind) {
      case 'timeout':
        return { title: '请求超时', description: '服务器响应超时，请稍后重试。', retryable: true };
      case 'network':
        return { title: '网络异常', description: '无法连接服务器，请检查网络后重试。', retryable: true };
      case 'canceled':
        return { title: '请求已取消', description: '本次请求已被新的请求替代。', retryable: false };
      case 'business':
        return { title: '操作未完成', description: error.message || '业务处理失败，请稍后重试。', retryable: true };
      default:
        return {
          title: '请求失败',
          description: error.message || `服务返回错误（HTTP ${error.httpStatus ?? '未知'}），请稍后重试。`,
          retryable: (error.httpStatus ?? 500) >= 500,
        };
    }
  }

  return { title: '出现错误', description: '发生未知错误，请刷新页面重试。', retryable: true };
}

/** 提取业务码，供页面按码分支 */
export function errorCode(error: unknown): number | null {
  return isApiError(error) ? error.code : null;
}

export type { ApiError };
