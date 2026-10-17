import { chromium } from '@playwright/test';

/** 截图脚本：端口与后端地址由环境变量覆盖，默认对齐本地开发配置 */
const BASE = process.env.SHOT_BASE ?? 'http://127.0.0.1:5180';
const LOGIN_USER = process.env.SHOT_USER ?? 'admin';
const LOGIN_PASS = process.env.SHOT_PASS ?? 'admin123';

const browser = await chromium.launch({ channel: 'chromium' });

const shots = [
  ['home-desktop', '/', 1280, 1800],
  ['products-desktop', '/products', 1280, 1400],
  ['detail-desktop', '/products/1', 1280, 1000],
  ['search-desktop', '/search?keyword=%E8%80%B3%E6%9C%BA', 1280, 1000],
  ['seckill-desktop', '/seckill', 1280, 1000],
  ['login-desktop', '/login', 1280, 900],
  ['home-mobile', '/', 375, 900],
];

for (const [name, path, width, height] of shots) {
  const page = await browser.newPage({ viewport: { width, height } });
  const failed = [];
  page.on('requestfailed', (req) => failed.push(req.url()));
  await page.goto(`${BASE}${path}`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(1200);
  await page.screenshot({ path: `docs/screenshots/${name}.png`, fullPage: false });
  console.log(`captured ${name}  failedRequests=${failed.length}`);
  await page.close();
}

// 管理端需要登录
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
const p = await ctx.newPage();
await p.goto(`${BASE}/login`);
await p.getByLabel('用户名').fill(LOGIN_USER);
await p.getByLabel('密码').fill(LOGIN_PASS);
await p.getByRole('button', { name: '登录', exact: true }).click();
await p.waitForURL((u) => !u.pathname.includes('/login'));
await p.goto(`${BASE}/admin/products`, { waitUntil: 'networkidle' });
await p.waitForTimeout(1200);
await p.screenshot({ path: 'docs/screenshots/admin-products.png' });
console.log('captured admin-products');
await p.goto(`${BASE}/admin/products/new`, { waitUntil: 'networkidle' });
await p.waitForTimeout(800);
await p.screenshot({ path: 'docs/screenshots/admin-product-new.png' });
console.log('captured admin-product-new');

await browser.close();
