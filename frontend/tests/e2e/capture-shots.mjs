import { chromium } from '@playwright/test';

const browser = await chromium.launch({ channel: 'chromium' });
const shots = [
  ['home-desktop', '/', 1280, 900],
  ['products-desktop', '/products', 1280, 900],
  ['search-desktop', '/search?keyword=%E8%B7%91%E9%9E%8B', 1280, 900],
  ['seckill-desktop', '/seckill', 1280, 900],
  ['login-desktop', '/login', 1280, 900],
  ['home-mobile', '/', 375, 812],
];

for (const [name, path, width, height] of shots) {
  const page = await browser.newPage({ viewport: { width, height } });
  await page.goto(`http://127.0.0.1:5173${path}`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(1200);
  await page.screenshot({ path: `docs/screenshots/${name}.png`, fullPage: false });
  console.log('captured', name);
  await page.close();
}

// 管理端需要登录
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
const p = await ctx.newPage();
await p.goto('http://127.0.0.1:5173/login');
await p.getByLabel('用户名').fill('admin');
await p.getByLabel('密码').fill('admin123');
await p.getByRole('button', { name: '登录', exact: true }).click();
await p.waitForURL((u) => !u.pathname.includes('/login'));
await p.goto('http://127.0.0.1:5173/admin/products', { waitUntil: 'networkidle' });
await p.waitForTimeout(1200);
await p.screenshot({ path: 'docs/screenshots/admin-products.png' });
console.log('captured admin-products');
await p.goto('http://127.0.0.1:5173/admin/products/new', { waitUntil: 'networkidle' });
await p.waitForTimeout(800);
await p.screenshot({ path: 'docs/screenshots/admin-product-new.png' });
console.log('captured admin-product-new');

await browser.close();
