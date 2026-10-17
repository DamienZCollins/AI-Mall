import {
  adminToken,
  createActivityWithWindow,
  createProductViaApi,
  disableActivity,
  expect,
  getActivityState,
  login,
  placeOrderViaApi,
  setProductStatus,
  test,
  USER,
  userToken,
} from './fixtures';

/**
 * 秒杀业务码可达性验收。
 *
 * 这些用例不复用 mock，而是构造真实前置条件（未来/过去的时间窗口、库存耗尽）后
 * 调用真实接口，再在页面上确认文案。4011 在后端没有抛出点，不在本文件断言。
 */
test.describe('秒杀业务码可达性', () => {
  test('4002 未开始的活动：详情按钮禁用，接口返回未开始', async ({ page }) => {
    const token = await adminToken();
    const suffix = `4002-${Date.now()}`;
    const product = await createProductViaApi(token, suffix);
    await setProductStatus(token, product.id, 1);
    const activity = await createActivityWithWindow(token, suffix, product.id, {
      startOffsetMinutes: 60,
      endOffsetMinutes: 180,
    });

    try {
      // 后端推导的状态应为 0 未开始
      const state = await getActivityState(activity.id);
      expect(state.status).toBe(0);

      // 接口层：真实抢购返回 4002
      const user = await userToken();
      const result = await placeOrderViaApi(user, activity.id);
      expect(result.code).toBe(4002);
      expect(result.message).toContain('尚未开始');

      // 页面层：按钮文案为「未开始」且不可点击
      await login(page, USER);
      await page.goto(`/seckill/${activity.id}`);
      const button = page.getByRole('button', { name: '未开始' });
      await expect(button).toBeVisible();
      await expect(button).toBeDisabled();
    } finally {
      await disableActivity(token, activity.id);
      await setProductStatus(token, product.id, 0);
    }
  });

  test('4003 已结束的活动：详情按钮禁用，接口返回已结束', async ({ page }) => {
    const token = await adminToken();
    const suffix = `4003-${Date.now()}`;
    const product = await createProductViaApi(token, suffix);
    await setProductStatus(token, product.id, 1);
    const activity = await createActivityWithWindow(token, suffix, product.id, {
      startOffsetMinutes: -180,
      endOffsetMinutes: -60,
    });

    try {
      const state = await getActivityState(activity.id);
      expect(state.status).toBe(2);

      const user = await userToken();
      const result = await placeOrderViaApi(user, activity.id);
      expect(result.code).toBe(4003);
      expect(result.message).toContain('已结束');

      await login(page, USER);
      await page.goto(`/seckill/${activity.id}`);
      const button = page.getByRole('button', { name: '已结束' });
      await expect(button).toBeVisible();
      await expect(button).toBeDisabled();
    } finally {
      await disableActivity(token, activity.id);
      await setProductStatus(token, product.id, 0);
    }
  });

  test('4004 售罄：库存 1 被抢完后，另一账号抢购返回已售罄', async ({ page }) => {
    const token = await adminToken();
    const suffix = `4004-${Date.now()}`;
    const product = await createProductViaApi(token, suffix);
    await setProductStatus(token, product.id, 1);
    const activity = await createActivityWithWindow(token, suffix, product.id, {
      startOffsetMinutes: -5,
      endOffsetMinutes: 120,
      totalStock: 1,
    });

    try {
      // 普通用户抢走唯一一件
      const user = await userToken();
      const first = await placeOrderViaApi(user, activity.id);
      expect(first.code).toBe(0);

      // 管理员账号再抢，Redis 库存已为 0
      const admin = await adminToken();
      const second = await placeOrderViaApi(admin, activity.id);
      expect(second.code).toBe(4004);
      expect(second.message).toContain('售罄');

      // 页面层：库存为 0 时按钮显示「已售罄」且不可点击
      await login(page, USER);
      await page.goto(`/seckill/${activity.id}`);
      const button = page.getByRole('button', { name: '已售罄' });
      await expect(button).toBeVisible();
      await expect(button).toBeDisabled();
    } finally {
      await disableActivity(token, activity.id);
      await setProductStatus(token, product.id, 0);
    }
  });

  test('4005 重复抢购：同一活动第二次抢购返回限购', async () => {
    const token = await adminToken();
    const suffix = `4005-${Date.now()}`;
    const product = await createProductViaApi(token, suffix);
    await setProductStatus(token, product.id, 1);
    const activity = await createActivityWithWindow(token, suffix, product.id, {
      startOffsetMinutes: -5,
      endOffsetMinutes: 120,
    });

    try {
      const user = await userToken();
      expect((await placeOrderViaApi(user, activity.id)).code).toBe(0);
      const again = await placeOrderViaApi(user, activity.id);
      expect(again.code).toBe(4005);
      expect(again.message).toContain('限购');
    } finally {
      await disableActivity(token, activity.id);
      await setProductStatus(token, product.id, 0);
    }
  });

  test('4010 排队服务不可用：接口返回 4010 且库存已回补', async () => {
    // 4010 需要停掉 RocketMQ proxy 才能触发，属破坏性操作，
    // 由 tests/e2e/record-4010.mjs 在专门的联调窗口执行并记录真实响应。
    test.skip(
      true,
      '4010 需停用 RocketMQ proxy 才能复现，见 record-4010.mjs 与阶段4验收记录'
    );
  });
});
