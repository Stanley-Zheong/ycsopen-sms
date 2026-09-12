import { defineConfig, devices } from '@playwright/test';

const chromePath = process.env.YCSOPEN_CHROME_PATH ?? '/usr/bin/google-chrome';
const webPort = process.env.YCSOPEN_WEB_PORT ?? '5173';

if (!/^\d{1,5}$/.test(webPort) || Number(webPort) < 1 || Number(webPort) > 65535) {
  throw new Error('YCSOPEN_WEB_PORT must be a TCP port between 1 and 65535');
}

export default defineConfig({
  testDir: './test/docker-release',
  fullyParallel: false,
  forbidOnly: true,
  retries: 0,
  reporter: [['line'], ['html', { open: 'never' }]],
  use: {
    baseURL: `http://127.0.0.1:${webPort}`,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    viewport: { width: 1440, height: 900 },
  },
  projects: [{
    name: 'installed-google-chrome',
    use: {
      ...devices['Desktop Chrome'],
      launchOptions: { executablePath: chromePath },
    },
  }],
});
