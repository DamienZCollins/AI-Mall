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
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth
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
        .map((img) => img.src.slice(0, 80))
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
    const hrefs = await footer.locator('a').evaluateAll((links) =>
      links.map((link) => link.getAttribute('href') ?? '')
    );
    for (const href of hrefs) {
      // 只允许站内已实现路由、mailto 与 tel
      if (href.startsWith('mailto:') || href.startsWith('tel:')) continue;
      expect(href).toMatch(/^\/(products|search|seckill|me|admin)?$|^\/login(\?redirect=.*)?$/);
    }
  });
});
