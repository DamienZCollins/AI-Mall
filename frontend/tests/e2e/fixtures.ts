import { test as base, expect, type Page } from '@playwright/test';

/**
 * 后端地址：默认跟随前端 `.env.local` 的 VITE_API_BASE_URL，
 * 可用 E2E_API_BASE_URL 覆盖（例如本机 8080 被占用时后端临时跑在 8090）。
 */
export const API_BASE =
  process.env.E2E_API_BASE_URL ?? process.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

export const ADMIN = { username: 'admin', password: 'admin123' };
export const USER = { username: 'user', password: 'user123' };

/** 拦截后端请求时使用完整域名，避免误命中前端 dev 模块（如 /src/api/search.ts） */
export const BACKEND_GLOB = `${API_BASE}/api/**`;

/** 按后端地址构造拦截用的正则，端口变化时不用改测试 */
export function backendUrl(path: string): RegExp {
  const escaped = API_BASE.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return new RegExp(`${escaped}/${path}`);
}

export async function login(page: Page, account: { username: string; password: string }): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('用户名').fill(account.username);
  await page.getByLabel('密码').fill(account.password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page).not.toHaveURL(/\/login/);
}

/** 直接调后端接口准备数据，避免依赖页面交互顺序 */
export async function adminToken(): Promise<string> {
  const response = await fetch(`${API_BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(ADMIN),
  });
  const body = (await response.json()) as { data?: { token?: string } };
  const token = body.data?.token;
  if (!token) throw new Error('管理员登录失败，无法取得令牌');
  return token;
}

export async function createProductViaApi(token: string, suffix: string): Promise<{ id: number }> {
  const response = await fetch(`${API_BASE}/api/admin/products`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({
      name: `E2E 测试商品 ${suffix}`,
      brand: 'E2E',
      categoryId: 1001,
      description: '端到端测试创建。',
      sellingPoint: '自动化测试',
      price: 199.0,
      stock: 20,
      imageUrl: null,
    }),
  });
  const body = (await response.json()) as { code: number; data?: { id: number }; message?: string };
  if (body.code !== 0 || !body.data) {
    throw new Error(`创建测试商品失败：${body.code} ${body.message ?? ''}`);
  }
  return { id: body.data.id };
}

export async function setProductStatus(token: string, id: number, status: 0 | 1): Promise<void> {
  await fetch(`${API_BASE}/api/admin/products/${id}/status`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ status }),
  });
}

/** 本地时间（Asia/Shanghai）格式化，后端 LocalDateTime 不接受时区偏移 */
function localDateTime(offsetMinutes: number): string {
  const now = new Date();
  const shifted = new Date(now.getTime() + offsetMinutes * 60_000);
  const pad = (value: number) => String(value).padStart(2, '0');
  return (
    `${shifted.getFullYear()}-${pad(shifted.getMonth() + 1)}-${pad(shifted.getDate())}` +
    `T${pad(shifted.getHours())}:${pad(shifted.getMinutes())}:00`
  );
}

export interface CreatedActivity {
  id: number;
  title: string;
  productId: number;
}

/** 创建一个时间窗口覆盖当前时刻的进行中活动 */
export async function createRunningActivity(
  token: string,
  suffix: string,
  productId: number
): Promise<CreatedActivity> {
  const title = `E2E 秒杀活动 ${suffix}`;
  const response = await fetch(`${API_BASE}/api/admin/seckill/activities`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({
      productId,
      title,
      seckillPrice: 99.0,
      totalStock: 5,
      perUserLimit: 1,
      startTime: localDateTime(-5),
      endTime: localDateTime(120),
      payTimeoutMinutes: 15,
    }),
  });
  const body = (await response.json()) as { code: number; data?: { id: number }; message?: string };
  if (body.code !== 0 || !body.data) {
    throw new Error(`创建测试活动失败：${body.code} ${body.message ?? ''}`);
  }
  return { id: body.data.id, title, productId };
}

/** 停用活动（后端没有删除接口，用 status=0 使其不可抢购） */
export async function disableActivity(token: string, activityId: number): Promise<void> {
  await fetch(`${API_BASE}/api/admin/seckill/activities/${activityId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ status: 0 }),
  });
}

export interface ActivityWindowOptions {
  /** 开始时间相对现在的分钟数，负数表示已开始 */
  startOffsetMinutes: number;
  /** 结束时间相对现在的分钟数，负数表示已结束 */
  endOffsetMinutes: number;
  totalStock?: number;
}

/** 按指定时间窗口创建活动，用于复现 4002 未开始 / 4003 已结束 */
export async function createActivityWithWindow(
  token: string,
  suffix: string,
  productId: number,
  options: ActivityWindowOptions
): Promise<CreatedActivity> {
  const title = `E2E 窗口活动 ${suffix}`;
  const response = await fetch(`${API_BASE}/api/admin/seckill/activities`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({
      productId,
      title,
      seckillPrice: 99.0,
      totalStock: options.totalStock ?? 5,
      perUserLimit: 1,
      startTime: localDateTime(options.startOffsetMinutes),
      endTime: localDateTime(options.endOffsetMinutes),
    }),
  });
  const body = (await response.json()) as { code: number; data?: { id: number }; message?: string };
  if (body.code !== 0 || !body.data) {
    throw new Error(`创建窗口活动失败：${body.code} ${body.message ?? ''}`);
  }
  return { id: body.data.id, title, productId };
}

/** 直接调后端抢购，用于验证业务码而无需经过页面 */
export async function placeOrderViaApi(
  userToken: string,
  activityId: number
): Promise<{ code: number; message: string }> {
  const response = await fetch(`${API_BASE}/api/seckill/${activityId}`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${userToken}` },
  });
  const body = (await response.json()) as { code: number; message: string };
  return { code: body.code, message: body.message };
}

/** 普通用户登录令牌 */
export async function userToken(): Promise<string> {
  const response = await fetch(`${API_BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(USER),
  });
  const body = (await response.json()) as { data?: { token?: string } };
  const token = body.data?.token;
  if (!token) throw new Error('普通用户登录失败，无法取得令牌');
  return token;
}

/** 查询活动当前详情 */
export async function getActivityState(activityId: number): Promise<{ remainingStock: number; status: number }> {
  const response = await fetch(`${API_BASE}/api/seckill/activities/${activityId}`);
  const body = (await response.json()) as { data?: { remainingStock: number; status: number } };
  if (!body.data) throw new Error('查询活动详情失败');
  return body.data;
}

export const test = base;
export { expect };
