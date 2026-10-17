import {
  ADMIN,
  USER,
  adminToken,
  createProductViaApi,
  createRunningActivity,
  disableActivity,
  expect,
  login,
  setProductStatus,
  test,
} from './fixtures';

/**
 * 用户侧关键路径：登录 → 商品 → 搜索 → 秒杀 → 抢购受理 → 轮询 → 订单 → 模拟支付
 * 数据由后端接口准备，测试结束清理。
 */
test.describe('用户侧关键路径', () => {
  test('登录后可浏览商品，刷新后登录态不丢', async ({ page }) => {
    await login(page, USER);

    await page.goto('/me');
    await expect(page.getByRole('heading', { name: '当前用户' })).toBeVisible();
    await expect(page.getByText('user', { exact: true })).toBeVisible();

    // 刷新后登录态不丢，不闪回登录页
    await page.reload();
    await expect(page.getByRole('heading', { name: '当前用户' })).toBeVisible();
    await expect(page).toHaveURL(/\/me/);

    await page.goto('/products');
    await expect(page.getByRole('heading', { name: '全部商品' })).toBeVisible();
    const firstCard = page.locator('.product-card').first();
    await expect(firstCard).toBeVisible();

    // 卡片名称链接进入详情
    await firstCard.locator('.product-name a').click();
    await expect(page).toHaveURL(/\/products\/\d+$/);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(page.getByText('商品描述')).toBeVisible();
  });

  test('AI 搜索展示真实召回结果，前进后退可恢复条件', async ({ page }) => {
    await page.goto('/search?keyword=%E8%B7%91%E9%9E%8B');
    await expect(page.getByRole('heading', { name: '搜索结果' })).toBeVisible();

    const cards = page.locator('.product-card');
    const empty = page.getByText('没有找到相关商品');
    await expect(cards.first().or(empty)).toBeVisible({ timeout: 30_000 });
    expect(page.url()).toContain('keyword=');

    // 有结果时进入详情，验证 productId 跳转
    if ((await cards.count()) > 0) {
      const href = await cards.first().locator('.product-name a').getAttribute('href');
      expect(href).toMatch(/^\/products\/\d+$/);
      await cards.first().locator('.product-name a').click();
      await expect(page).toHaveURL(/\/products\/\d+$/);
      await page.goBack();
      expect(page.url()).toContain('/search');
      expect(page.url()).toContain('keyword=');
    }
  });

  test('秒杀列表可筛选，详情按钮状态与状态矩阵一致', async ({ page }) => {
    await page.goto('/seckill');
    await expect(page.getByRole('heading', { name: '限时秒杀' })).toBeVisible();

    const cards = page.locator('.activity-card');
    const count = await cards.count();
    if (count > 0) {
      await cards.first().getByRole('link', { name: '查看活动' }).click();
      await expect(page).toHaveURL(/\/seckill\/\d+$/);
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
      await expect(page.locator('.detail__info .badge').first()).toBeVisible();
    }

    // runningOnly 筛选同步到 URL
    await page.goto('/seckill');
    await page.getByRole('button', { name: /显示全部活动/ }).click();
    await expect(page).toHaveURL(/runningOnly=true/);
    await page.getByRole('button', { name: /只看进行中/ }).click();
    await expect(page).not.toHaveURL(/runningOnly=true/);
  });

  test('未登录点击抢购跳转登录并带回跳参数', async ({ page }) => {
    const token = await adminToken();
    const suffix = Date.now().toString();
    const product = await createProductViaApi(token, suffix);
    await setProductStatus(token, product.id, 1);
    const activity = await createRunningActivity(token, suffix, product.id);

    try {
      await page.goto(`/seckill/${activity.id}`);
      const purchase = page.getByRole('button', { name: '立即抢购' });
      await expect(purchase).toBeEnabled();
      await purchase.click();
      await expect(page).toHaveURL(/\/login/);
      expect(decodeURIComponent(page.url())).toContain(`/seckill/${activity.id}`);
    } finally {
      await disableActivity(token, activity.id);
      await setProductStatus(token, product.id, 0);
    }
  });

  test('抢购受理后进入结果页，轮询到 SUCCESS 后订单可模拟支付', async ({ page }) => {
    const token = await adminToken();
    const suffix = Date.now().toString();
    const product = await createProductViaApi(token, suffix);
    await setProductStatus(token, product.id, 1);
    const activity = await createRunningActivity(token, suffix, product.id);

    try {
      await login(page, USER);
      await page.goto(`/seckill/${activity.id}`);

      const purchase = page.getByRole('button', { name: '立即抢购' });
      await expect(purchase).toBeEnabled();
      await purchase.click();

      // 受理后跳到结果页，且不能直接展示「下单成功」
      await expect(page).toHaveURL(new RegExp(`/seckill/${activity.id}/result`));
      await expect(page.getByRole('heading', { name: '正在为你排队' }).or(page.getByRole('heading', { name: '抢购成功' }))).toBeVisible({ timeout: 30_000 });

      // 等待异步下单完成
      await expect(page.getByRole('heading', { name: '抢购成功' })).toBeVisible({ timeout: 60_000 });

      const orderNo = (await page.locator('.result__list dd').first().innerText()).trim();
      expect(orderNo).toMatch(/^SK[\w-]+$/);

      await page.getByRole('button', { name: '前往订单详情' }).click();
      await expect(page).toHaveURL(new RegExp(`/orders/${orderNo}`));
      await expect(page.locator('.order-list dd').nth(1)).toHaveText('待支付');

      // 模拟支付后用响应替换订单，状态与按钮同时更新
      await page.getByRole('button', { name: '模拟支付' }).click();
      await expect(page.locator('.order-list dd').nth(1)).toHaveText('已支付', { timeout: 20_000 });
      await expect(page.getByRole('button', { name: '模拟支付' })).toHaveCount(0);
    } finally {
      await disableActivity(token, activity.id);
      await setProductStatus(token, product.id, 0);
    }
  });

  test('订单已支付后刷新仍是终态且没有支付按钮', async ({ page }) => {
    const token = await adminToken();
    const suffix = Date.now().toString();
    const product = await createProductViaApi(token, suffix);
    await setProductStatus(token, product.id, 1);
    const activity = await createRunningActivity(token, suffix, product.id);

    try {
      await login(page, USER);
      await page.goto(`/seckill/${activity.id}`);
      await page.getByRole('button', { name: '立即抢购' }).click();
      await expect(page.getByRole('heading', { name: '抢购成功' })).toBeVisible({ timeout: 60_000 });
      const orderNo = (await page.locator('.result__list dd').first().innerText()).trim();

      await page.goto(`/orders/${orderNo}`);
      await page.getByRole('button', { name: '模拟支付' }).click();
      await expect(page.locator('.order-list dd').nth(1)).toHaveText('已支付', { timeout: 20_000 });

      await page.reload();
      await expect(page.locator('.order-list dd').nth(1)).toHaveText('已支付');
      await expect(page.getByRole('button', { name: '模拟支付' })).toHaveCount(0);
      await expect(page.getByText('订单已支付，无需再次操作。')).toBeVisible();
    } finally {
      await disableActivity(token, activity.id);
      await setProductStatus(token, product.id, 0);
    }
  });
});

test.describe('管理端关键路径', () => {
  test('新增商品默认下架，上架后用户侧可见', async ({ page }) => {
    const token = await adminToken();
    const suffix = Date.now().toString();
    const created = await createProductViaApi(token, suffix);

    try {
      // 新增后默认下架：用户侧详情返回 2001 占位
      await page.goto(`/products/${created.id}`);
      await expect(page.getByText('商品不可用')).toBeVisible();

      await setProductStatus(token, created.id, 1);

      // 上架后用户侧详情可见
      await page.goto(`/products/${created.id}`);
      await expect(page.getByRole('heading', { level: 1 })).toContainText('E2E 测试商品');

      await page.goto('/products?page=1&size=100');
      await expect(page.getByText(`E2E 测试商品 ${suffix}`).first()).toBeVisible();
    } finally {
      await setProductStatus(token, created.id, 0);
    }
  });

  test('普通用户访问管理端被拦截且不发管理端请求', async ({ page }) => {
    await login(page, USER);

    const adminCalls: string[] = [];
    page.on('request', (req) => {
      if (req.url().includes('/api/admin/')) adminCalls.push(req.url());
    });

    await page.goto('/admin/products');
    await expect(page.getByText('没有访问权限')).toBeVisible();
    expect(adminCalls).toHaveLength(0);
  });

  test('管理员可进入管理端，商品与活动列表可渲染', async ({ page }) => {
    await login(page, ADMIN);

    await page.goto('/admin');
    await expect(page.getByRole('heading', { name: '管理端概览' })).toBeVisible();
    await expect(page.getByText('当前管理员')).toBeVisible();

    await page.goto('/admin/products');
    await expect(page.getByRole('heading', { name: '商品管理' })).toBeVisible();
    await expect(page.locator('.admin-table')).toBeVisible();

    await page.goto('/admin/seckill');
    await expect(page.getByRole('heading', { name: '秒杀活动管理' })).toBeVisible();
    await expect(page.locator('.admin-table')).toBeVisible();
  });

  test('后台新增商品后可在列表看到并入内上架', async ({ page }) => {
    const token = await adminToken();
    const suffix = Date.now().toString();

    await login(page, ADMIN);
    await page.goto('/admin/products/new');
    await expect(page.getByRole('heading', { name: '新增商品' })).toBeVisible();

    await page.getByLabel('商品名称').fill(`UI 新增商品 ${suffix}`);
    await page.getByLabel('品牌').fill('UI');
    await page.getByLabel('分类 ID').fill('1001');
    await page.getByLabel('售价').fill('88.80');
    await page.getByLabel('库存').fill('7');
    await page.getByRole('button', { name: '创建商品' }).click();

    await expect(page.getByText('商品已创建，当前为下架状态')).toBeVisible();
    await page.getByRole('link', { name: '去上架' }).click();
    await expect(page.getByRole('heading', { name: '商品管理' })).toBeVisible();

    await page.getByLabel('关键字').fill(`UI 新增商品 ${suffix}`);
    await page.getByRole('button', { name: '筛选' }).click();

    const row = page.locator('.admin-table tbody tr').first();
    await expect(row).toContainText(`UI 新增商品 ${suffix}`);
    await expect(row.locator('.status-tag')).toHaveText('已下架');

    // 行内上架：二次确认后该行状态立即变化，不重载整页
    await row.getByRole('button', { name: /上架/ }).click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await page.getByRole('dialog').getByRole('button', { name: '上架' }).click();
    await expect(row.locator('.status-tag')).toHaveText('在售');

    // 清理：下架该商品
    const rowText = await row.innerText();
    const id = Number(rowText.match(/^\d+/)?.[0]);
    if (Number.isInteger(id) && id > 0) {
      await setProductStatus(token, id, 0);
    }
  });
});

test.describe('鉴权与错误反馈', () => {
  test('未登录访问受保护页面跳转登录并带回跳参数', async ({ page }) => {
    await page.goto('/me');
    await expect(page).toHaveURL(/\/login/);
    expect(page.url()).toContain('redirect=');
  });

  test('非法商品 ID 落到 404', async ({ page }) => {
    for (const path of ['/products/abc', '/products/0', '/products/-1']) {
      await page.goto(path);
      await expect(page.getByText('页面不存在')).toBeVisible();
    }
  });

  test('搜索业务码 3002 有独立文案而不是空结果', async ({ page }) => {
    // 只拦截后端完整地址，避免命中前端 dev 模块 /src/api/search.ts
    await page.route(/localhost:8080\/api\/search/, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 3002, message: '搜索服务暂不可用', data: null }),
      });
    });

    await page.goto('/search?keyword=%E8%B7%91%E9%9E%8B');
    await expect(page.locator('.search-state__title', { hasText: '搜索服务暂不可用' })).toBeVisible();
    await expect(page.locator('.search-state__title', { hasText: '没有找到相关商品' })).toHaveCount(0);
  });

  test('搜索业务码 3001 与 3002 文案不同', async ({ page }) => {
    await page.route(/localhost:8080\/api\/search/, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 3001, message: '文本向量化失败', data: null }),
      });
    });

    await page.goto('/search?keyword=%E8%B7%91%E9%9E%8B');
    await expect(page.locator('.search-state__title', { hasText: '语义检索暂不可用' })).toBeVisible();
    await expect(page.locator('.search-state__title', { hasText: '搜索服务暂不可用' })).toHaveCount(0);
  });

  test('401 清理登录态并跳转登录页', async ({ page, context }) => {
    await context.addInitScript(() => {
      localStorage.setItem('mall.token', 'invalid-token-value');
      localStorage.setItem('mall.expiresAt', String(Date.now() + 3_600_000));
    });

    await page.goto('/me');
    await expect(page).toHaveURL(/\/login/);
    const token = await page.evaluate(() => localStorage.getItem('mall.token'));
    expect(token).toBeNull();
  });

  test('后端不可达时展示网络错误而不是空结果', async ({ page }) => {
    await page.route(/localhost:8080\/api\/products/, async (route) => {
      await route.abort('failed');
    });
    await page.goto('/products');
    await expect(page.getByText('网络异常')).toBeVisible();
    await expect(page.getByText('没有找到商品')).toHaveCount(0);
  });
});
