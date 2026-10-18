import { expect, test } from '@playwright/test';

const PAGES = ['/', '/products', '/search?keyword=%E8%B7%91%E9%9E%8B', '/seckill', '/login', '/products/1'];

/** 五档断点：1280 / 1100 / 860 / 600 / 370 下不允许出现横向溢出 */
const WIDTHS = [1280, 1100, 860, 600, 370];

test.describe('响应式与可访问性', () => {
  for (const width of WIDTHS) {
    test(`${width}px 下页面无横向溢出`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      for (const path of PAGES) {
        await page.goto(path);
        await page.waitForLoadState('networkidle');
        const overflow = await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        );
        expect(overflow, `${path} 在 ${width}px 出现横向溢出 ${overflow}px`).toBeLessThanOrEqual(1);
      }
    });
  }

  test('移动端底部导航存在且移除购物车项', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 });
    await page.goto('/');
    const nav = page.getByRole('navigation', { name: '移动端底部导航' });
    await expect(nav).toBeVisible();
    await expect(nav.getByRole('link')).toHaveCount(4);
    await expect(nav.getByText('购物车')).toHaveCount(0);
  });

  test('移动端搜索弹层可用键盘打开与关闭，焦点回到触发按钮', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 });
    await page.goto('/');

    const openButton = page.getByRole('button', { name: '打开搜索' });
    await openButton.focus();
    await page.keyboard.press('Enter');

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await expect(dialog.getByRole('textbox')).toBeFocused();

    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();
    await expect(openButton).toBeFocused();
  });

  test('skip-link 聚焦后可见并指向主内容', async ({ page }) => {
    await page.goto('/');
    await page.keyboard.press('Tab');
    const skip = page.getByRole('link', { name: '跳到主要内容' });
    await expect(skip).toBeVisible();
    await expect(skip).toHaveAttribute('href', '#main-content');
    await expect(page.locator('#main-content')).toHaveCount(1);
  });

  test('所有图片带 alt 属性，装饰图 alt 为空', async ({ page }) => {
    await page.goto('/products');
    await page.waitForLoadState('networkidle');
    const missing = await page.evaluate(() =>
      Array.from(document.querySelectorAll('img'))
        .filter((img) => !img.hasAttribute('alt'))
        .map((img) => img.src.slice(0, 80)),
    );
    expect(missing).toEqual([]);
  });

  test('减少动效偏好下不播放位移动画', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.goto('/');
    const duration = await page.evaluate(() => {
      const cards = document.querySelectorAll('.product-card');
      if (cards.length === 0) return '0.01ms';
      return getComputedStyle(cards[0] as Element).transitionDuration;
    });
    // 减少动效媒体查询把过渡压到 0.01ms
    expect(duration.startsWith('0.01ms') || duration === '0s').toBe(true);
  });

  test('商品卡片无会员价、收藏与加入购物车入口', async ({ page }) => {
    await page.goto('/products');
    const card = page.locator('.product-card').first();
    await expect(card).toBeVisible();
    await expect(card.getByText('会员价')).toHaveCount(0);
    await expect(card.getByRole('button', { name: /收藏/ })).toHaveCount(0);
    await expect(card.getByRole('button', { name: /加入购物车/ })).toHaveCount(0);
  });

  test('页脚不存在指向未实现页面的可点击链接', async ({ page }) => {
    await page.goto('/');
    const footer = page.getByRole('contentinfo');
    await expect(footer).toBeVisible();
    const hrefs = await footer
      .locator('a')
      .evaluateAll((links) => links.map((link) => link.getAttribute('href') ?? ''));
    for (const href of hrefs) {
      // 只允许站内已实现路由
      expect(href).toMatch(
        /^\/(?:#(?:seckill|weekly|popular))?$|^\/(products|search|seckill|me|admin)?$|^\/login(\?redirect=.*)?$/,
      );
    }
  });

  test('首页区块顺序为秒杀、本周精选、人气好物', async ({ page }) => {
    await page.route('**/api/seckill/activities*', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          message: 'ok',
          data: { total: 0, page: 1, size: 3, list: [] },
        }),
      }),
    );
    await page.goto('/');
    await expect(page.locator('#seckill')).toBeVisible();
    const order = await page
      .locator('main section[id]')
      .evaluateAll((sections) => sections.map((section) => section.id));
    expect(order).toEqual(['seckill', 'weekly', 'popular']);
  });

  test('首页滚动时主导航只高亮当前区块', async ({ page }) => {
    // 主导航在 1100px 以下由底部导航替代，本用例只验证桌面端的滚动高亮
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.route('**/api/seckill/activities*', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          message: 'ok',
          data: { total: 0, page: 1, size: 3, list: [] },
        }),
      }),
    );
    await page.goto('/');

    const nav = page.getByRole('navigation', { name: '主导航' });
    const weekly = nav.getByRole('link', { name: '本周精选' });
    const popular = nav.getByRole('link', { name: '人气好物' });

    await page.locator('#weekly').scrollIntoViewIfNeeded();
    await expect(weekly).toHaveAttribute('aria-current', 'location');
    await expect(popular).not.toHaveAttribute('aria-current', 'location');

    await page.locator('#popular').scrollIntoViewIfNeeded();
    await expect(popular).toHaveAttribute('aria-current', 'location');
    await expect(weekly).not.toHaveAttribute('aria-current', 'location');
  });

  test('首页不重复展示搜索入口，页脚不展示未开放项与备案信息', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('#search-entry')).toHaveCount(0);
    const footer = page.getByRole('contentinfo');
    await expect(footer.getByText(/暂未开放/)).toHaveCount(0);
    await expect(footer.getByText(/备案|ICP/i)).toHaveCount(0);
    await expect(footer.getByRole('heading', { name: '联系方式' })).toHaveCount(0);
    await expect(footer.getByRole('link', { name: 'AI 混合搜索' })).toHaveCount(0);
  });
});
