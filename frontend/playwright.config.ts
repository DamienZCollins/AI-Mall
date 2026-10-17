import { defineConfig, devices } from '@playwright/test';

/** 端到端验收以 Chromium 为基线；后端需先启动（backend/start.ps1） */
export default defineConfig({
  testDir: './tests/e2e',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://127.0.0.1:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
  },
  projects: [
    {
      name: 'chromium-desktop',
      use: {
        ...devices['Desktop Chrome'],
        channel: 'chromium',
        viewport: { width: 1280, height: 900 },
      },
    },
    {
      // 移动端基线仍用 Chromium：iPhone 13 预设会切到 WebKit，本机只安装 Chromium
      name: 'chromium-mobile',
      use: {
        ...devices['Pixel 7'],
        channel: 'chromium',
      },
      testMatch: /responsive\.spec\.ts/,
    },
  ],
  webServer: process.env.E2E_NO_SERVER
    ? undefined
    : {
        command: 'pnpm exec vite --host 127.0.0.1 --port 5173',
        url: 'http://127.0.0.1:5173',
        reuseExistingServer: true,
        timeout: 60_000,
      },
});
