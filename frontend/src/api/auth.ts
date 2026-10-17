import { http } from './http';
import type { AuthUser } from '@/stores/auth';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  expiresIn: number;
  user: AuthUser;
}

export interface UserInfo {
  id: number;
  username: string;
  nickname: string;
  role: 'USER' | 'ADMIN';
}

/** 登录：接口函数只负责请求与类型，不写全局状态 */
export function login(payload: LoginRequest, signal?: AbortSignal): Promise<LoginResponse> {
  return http.post<LoginResponse>('/api/auth/login', payload, { signal });
}

/** 查询当前登录用户 */
export function fetchMe(signal?: AbortSignal): Promise<UserInfo> {
  return http.get<UserInfo>('/api/auth/me', undefined, { signal });
}
