/**
 * 阶段 4 联调证据采集：真实跑一次「创建活动 → 抢购 → 轮询 → 订单 → 支付」，
 * 打印每一步的原始响应，供验收记录引用。
 */
const API = 'http://localhost:8080';

function localDateTime(offsetMinutes) {
  const now = new Date();
  const d = new Date(now.getTime() + offsetMinutes * 60_000);
  const p = (v) => String(v).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:00`;
}

async function call(method, path, body, token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(`${API}${path}`, {
    method,
    headers,
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  let json;
  try {
    json = await res.json();
  } catch {
    json = null;
  }
  return { status: res.status, body: json };
}

const log = (label, value) => console.log(`\n### ${label}\n${JSON.stringify(value)}`);

const admin = await call('POST', '/api/auth/login', { username: 'admin', password: 'admin123' });
const adminToken = admin.body.data.token;

const suffix = Date.now().toString();
const product = await call(
  'POST',
  '/api/admin/products',
  {
    name: `阶段4 联调商品 ${suffix}`,
    brand: '联调',
    categoryId: 1001,
    description: '阶段 4 联调使用。',
    sellingPoint: '联调',
    price: 199.0,
    stock: 10,
    imageUrl: null,
  },
  adminToken
);
log('1. 创建商品', product);
const productId = product.body.data.id;

log('2. 上架商品', await call('PATCH', `/api/admin/products/${productId}/status`, { status: 1 }, adminToken));

const activity = await call(
  'POST',
  '/api/admin/seckill/activities',
  {
    productId,
    title: `阶段4 联调活动 ${suffix}`,
    seckillPrice: 99.0,
    totalStock: 3,
    perUserLimit: 1,
    startTime: localDateTime(-5),
    endTime: localDateTime(120),
    payTimeoutMinutes: 15,
  },
  adminToken
);
log('3. 创建活动', activity);
const activityId = activity.body.data.id;

log('4. 活动详情（status=1 进行中）', await call('GET', `/api/seckill/activities/${activityId}`));

const user = await call('POST', '/api/auth/login', { username: 'user', password: 'user123' });
const userToken = user.body.data.token;

log('5. 抢购（code=0 仅代表已受理，data=null）', await call('POST', `/api/seckill/${activityId}`, null, userToken));

let orderNo = null;
for (let i = 0; i < 20; i += 1) {
  const result = await call('GET', `/api/seckill/${activityId}/result`, null, userToken);
  console.log(`6.${i + 1} 轮询结果 state=${result.body.data?.state} orderNo=${result.body.data?.orderNo}`);
  if (result.body.data?.state !== 'QUEUING') {
    log('6. 最终结果', result.body);
    orderNo = result.body.data.orderNo;
    break;
  }
  await new Promise((r) => setTimeout(r, 1000));
}

if (orderNo) {
  log('7. 订单详情（待支付）', await call('GET', `/api/seckill/orders/${orderNo}`, null, userToken));
  log('8. 重复抢购（4005 限购）', await call('POST', `/api/seckill/${activityId}`, null, userToken));
  log('9. 模拟支付', await call('POST', `/api/seckill/orders/${orderNo}/pay`, null, userToken));
  log('10. 支付后再次支付（4007 状态不允许）', await call('POST', `/api/seckill/orders/${orderNo}/pay`, null, userToken));
  log('11. 支付后订单状态', await call('GET', `/api/seckill/orders/${orderNo}`, null, userToken));
}

log('12. 不存在的订单 4006', await call('GET', '/api/seckill/orders/NOSUCHORDER', null, userToken));
log('13. 停用活动', await call('PUT', `/api/admin/seckill/activities/${activityId}`, { status: 0 }, adminToken));
log('14. 停用后活动状态（3 已关闭）', await call('GET', `/api/seckill/activities/${activityId}`));
log('15. 停用后抢购（4003 已结束/不可抢）', await call('POST', `/api/seckill/${activityId}`, null, userToken));

// 清理：下架商品
await call('PATCH', `/api/admin/products/${productId}/status`, { status: 0 }, adminToken);
console.log('\n清理完成');
