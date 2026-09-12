import { defineConfig, devices } from '@playwright/test';

const localChromePath = process.env.YCSOPEN_CHROME_PATH
  ?? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const webPort = process.env.YCSOPEN_WEB_PORT ?? '4173';

if (!/^\d{4,5}$/.test(webPort)) {
  throw new Error('YCSOPEN_WEB_PORT must be a numeric TCP port');
}
const baseURL = `http://127.0.0.1:${webPort}`;

export default defineConfig({
  testDir: './test/scripts',
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 2 : 0,
  reporter: 'html',
  use: { baseURL, trace: 'on-first-retry', viewport: { width: 1440, height: 900 } },
  projects: [{
    name: 'local-google-chrome',
    use: {
      ...devices['Desktop Chrome'],
      launchOptions: {
        executablePath: localChromePath,
        args: ['--disable-crashpad-for-testing'],
      },
    },
  }],
  webServer: {
    command: `npm run dev -- --host 127.0.0.1 --port ${webPort}`,
    url: `${baseURL}/login`,
    reuseExistingServer: !process.env.CI && !process.env.YCSOPEN_E2E_ISOLATED,
  },
});
