import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './',
  testMatch: '**/*.spec.ts',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://localhost:5777',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      // 本机 mac13-arm64 无法安装捆绑 Chromium，改用系统 Chrome（channel 模式）
      use: { ...devices['Desktop Chrome'], channel: 'chrome' },
    },
  ],
  webServer: undefined, // No web server start since we assume the dev server is already running
});
