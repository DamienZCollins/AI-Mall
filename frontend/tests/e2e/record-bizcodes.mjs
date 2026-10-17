/**
 * 业务码可达性验证：逐个构造前置条件，记录真实响应。
 * 目的：确认哪些码在后端确实会抛出，哪些是未被使用的常量。
 */
const API = process.env.MALL_API ?? 'http://localhost:8090';

async function call(method, path, body, token) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body) headers['Content-Type'] = 'application/json';
  const res = await fetch(`${API}${path}`, {
    method,
    headers,
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  let json = null;
  try {
    json = await res.json();
  } catch {
    /* 无 JSON 响应体 */
  }
  return { status: res.status, body: json };
}

function localDateTime(offsetMinutes) {
  const d = new Date(Date.now() + offsetMinutes * 60_000);
  const p = (v) => String(v).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:00`;
}

const log = (label, value) => console.log(`\n### ${label}\n${JSON.stringify(value)}`);

const admin = await call('POST', '/api/auth/login', { username: 'admin', password: 'admin123' });
const adminToken = admin.body.data.token;
const user = await call('POST', '/api/auth/login', { username: 'user', password: 'user123' });
const userToken = user.body.data.token;

const suffix = Date.now().toString();

// 准备一个上架商品
const product = await call(
  'POST',
  '/api/admin/products',
  {
    name: `业务码验证商品 ${suffix}`,
    brand: '验证',
    categoryId: 1001,
    description: '业务码可达性验证。',
    sellingPoint: '验证',
    price: 199,
    stock: 10,
    imageUrl: null,
  },
  adminToken
);
const productId = product.body.data.id;
await call('PATCH', `/api/admin/products/${productId}/status`, { status: 1 }, adminToken);

// ---- 4002 未开始：创建未来开始的活动，然后立即抢购 ----
const future = await call(
  'POST',
  '/api/admin/seckill/activities',
  {
    productId,
    title: `未开始活动 ${suffix}`,
    seckillPrice: 99,
    totalStock: 5,
    startTime: localDateTime(60),
    endTime: localDateTime(180),
  },
  adminToken
);
const futureId = future.body.data.id;
log('4002 活动状态应为 0 未开始', { id: futureId, status: future.body.data.status });
log('4002 抢购未开始的活动', await call('POST', `/api/seckill/${futureId}`, null, userToken));

// ---- 4003 已结束：创建已过期的活动窗口 ----
const past = await call(
  'POST',
  '/api/admin/seckill/activities',
  {
    productId,
    title: `已结束活动 ${suffix}`,
    seckillPrice: 99,
    totalStock: 5,
    startTime: localDateTime(-180),
    endTime: localDateTime(-60),
  },
  adminToken
);
const pastId = past.body.data.id;
log('4003 活动状态应为 2 已结束', { id: pastId, status: past.body.data.status });
log('4003 抢购已结束的活动', await call('POST', `/api/seckill/${pastId}`, null, userToken));

// ---- 4004 售罄：库存 1 的活动，抢完后第二人再抢 ----
const tiny = await call(
  'POST',
  '/api/admin/seckill/activities',
  {
    productId,
    title: `单库存活动 ${suffix}`,
    seckillPrice: 99,
    totalStock: 1,
    startTime: localDateTime(-5),
    endTime: localDateTime(120),
  },
  adminToken
);
const tinyId = tiny.body.data.id;
log('4004 先由 user 抢走唯一一件', await call('POST', `/api/seckill/${tinyId}`, null, userToken));
log('4004 再由 admin 抢购（库存应已为 0）', await call('POST', `/api/seckill/${tinyId}`, null, adminToken));

// 清理：停用三个活动
for (const id of [futureId, pastId, tinyId]) {
  await call('PUT', `/api/admin/seckill/activities/${id}`, { status: 0 }, adminToken);
}
await call('PATCH', `/api/admin/products/${productId}/status`, { status: 0 }, adminToken);
console.log('\n完成');
