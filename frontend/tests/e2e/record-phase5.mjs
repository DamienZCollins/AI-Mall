/** 阶段 5 联调证据：管理端商品维护、图片上传、活动维护、索引重建 */
const API = 'http://localhost:8080';

async function call(method, path, body, token, extraHeaders) {
  const headers = { ...(extraHeaders ?? {}) };
  if (token) headers.Authorization = `Bearer ${token}`;
  let payload;
  if (body instanceof FormData) {
    payload = body;
  } else if (body) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }
  const res = await fetch(`${API}${path}`, { method, headers, ...(payload ? { body: payload } : {}) });
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
const user = await call('POST', '/api/auth/login', { username: 'user', password: 'user123' });
const userToken = user.body.data.token;

const suffix = Date.now().toString();

// 1. 新增商品（默认下架）
const created = await call(
  'POST',
  '/api/admin/products',
  {
    name: `阶段5 商品 ${suffix}`,
    brand: '阶段5',
    categoryId: 1001,
    description: '阶段 5 联调。',
    sellingPoint: '联调',
    price: 299.0,
    stock: 15,
    imageUrl: null,
  },
  adminToken
);
log('1. 新增商品（默认下架）', created);
const productId = created.body.data.id;

// 2. 重复设置状态 2002
await call('PATCH', `/api/admin/products/${productId}/status`, { status: 1 }, adminToken);
log('2. 对已上架商品再次上架（2002）', await call('PATCH', `/api/admin/products/${productId}/status`, { status: 1 }, adminToken));

// 3. 编辑只改售价
log('3. 编辑只改售价', await call('PUT', `/api/admin/products/${productId}`, { price: 279.0 }, adminToken));

// 4. 管理端列表含下架商品
const adminList = await call('GET', `/api/admin/products?page=1&size=5&keyword=${encodeURIComponent('阶段5 商品 ' + suffix)}`, null, adminToken);
log('4. 管理端列表（可按关键字查到）', adminList);

// 5. 用户侧列表必须看不到下架商品
const userList = await call('GET', `/api/products?page=1&size=100&keyword=${encodeURIComponent('阶段5 商品 ' + suffix)}`);
log('5. 用户侧列表（上架前 total 应为 0）', { total: userList.body.data.total });

// 6. 上传图片（生成 1x1 PNG）
const png = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
  'base64'
);
const form = new FormData();
form.append('file', new Blob([png], { type: 'image/png' }), 'test.png');
log('6. 上传图片', await call('POST', '/api/admin/products/images', form, adminToken));

// 7. 上传非图片文件（2003）
const badForm = new FormData();
badForm.append('file', new Blob([Buffer.from('not an image')], { type: 'image/png' }), 'fake.png');
log('7. 上传伪装成 png 的文本（2003）', await call('POST', '/api/admin/products/images', badForm, adminToken));

// 8. 普通用户上传（403）
const userForm = new FormData();
userForm.append('file', new Blob([png], { type: 'image/png' }), 'test.png');
log('8. 普通用户上传（403）', await call('POST', '/api/admin/products/images', userForm, userToken));

// 9. 活动校验 4009 / 4008
log('9. 秒杀价不低于原价（4009）', await call(
  'POST',
  '/api/admin/seckill/activities',
  {
    productId,
    title: `阶段5 活动 ${suffix}`,
    seckillPrice: 299.0,
    totalStock: 5,
    startTime: '2026-10-18T10:00:00',
    endTime: '2026-10-18T12:00:00',
  },
  adminToken
));

log('10. 结束时间早于开始时间（4008）', await call(
  'POST',
  '/api/admin/seckill/activities',
  {
    productId,
    title: `阶段5 活动 2 ${suffix}`,
    seckillPrice: 99.0,
    totalStock: 5,
    startTime: '2026-10-18T12:00:00',
    endTime: '2026-10-18T10:00:00',
  },
  adminToken
));

// 11. 未上架商品建活动（2001）
await call('PATCH', `/api/admin/products/${productId}/status`, { status: 0 }, adminToken);
log('11. 下架商品建活动（2001）', await call(
  'POST',
  '/api/admin/seckill/activities',
  {
    productId,
    title: `阶段5 活动 3 ${suffix}`,
    seckillPrice: 99.0,
    totalStock: 5,
    startTime: '2026-10-18T10:00:00',
    endTime: '2026-10-18T12:00:00',
  },
  adminToken
));

// 12. 索引重建
log('12. 重建搜索索引', await call('POST', '/api/admin/search/reindex', null, adminToken));

// 13. 普通用户索引重建（403）
log('13. 普通用户索引重建（403）', await call('POST', '/api/admin/search/reindex', null, userToken));

console.log('\n完成');
