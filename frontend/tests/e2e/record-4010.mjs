/** 4010 验证：先停掉 RocketMQ proxy，再抢购，确认消息投递失败时返回 4010 且库存已回补 */
const API = process.env.MALL_API ?? 'http://localhost:8090';

async function call(method, path, body, token) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body) headers['Content-Type'] = 'application/json';
  const res = await fetch(`${API}${path}`, { method, headers, ...(body ? { body: JSON.stringify(body) } : {}) });
  let json = null;
  try {
    json = await res.json();
  } catch {
    /* ignore */
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
const product = await call(
  'POST',
  '/api/admin/products',
  { name: `4010 验证商品 ${suffix}`, brand: '验证', categoryId: 1001, price: 199, stock: 10, imageUrl: null },
  adminToken
);
const productId = product.body.data.id;
await call('PATCH', `/api/admin/products/${productId}/status`, { status: 1 }, adminToken);

const activity = await call(
  'POST',
  '/api/admin/seckill/activities',
  {
    productId,
    title: `4010 验证活动 ${suffix}`,
    seckillPrice: 99,
    totalStock: 5,
    startTime: localDateTime(-5),
    endTime: localDateTime(120),
  },
  adminToken
);
const activityId = activity.body.data.id;
log('活动就绪（MQ 已停，抢购应返回 4010）', { id: activityId, status: activity.body.data.status });

log('4010 抢购', await call('POST', `/api/seckill/${activityId}`, null, userToken));

// 库存应已回补：再查活动剩余库存
log('回补后活动剩余库存（应与总量一致）', await call('GET', `/api/seckill/activities/${activityId}`));

// 结果接口：回补后买家标记已移除，应返回 FAILED（说明未被当作成功）
log('回补后查询抢购结果', await call('GET', `/api/seckill/${activityId}/result`, null, userToken));

await call('PUT', `/api/admin/seckill/activities/${activityId}`, { status: 0 }, adminToken);
await call('PATCH', `/api/admin/products/${productId}/status`, { status: 0 }, adminToken);
console.log('\n完成');
