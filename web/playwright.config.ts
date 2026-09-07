import { defineConfig, devices } from '@playwright/test';

const localChromePath = process.env.YCSOPEN_CHROME_PATH
  ?? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';

export default defineConfig({
  testDir: './test/scripts',
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 2 : 0,
  reporter: 'html',
  use: { baseURL: 'http://127.0.0.1:4173', trace: 'on-first-retry', viewport: { width: 1440, height: 900 } },
  projects: [{
    name: 'local-google-chrome',
    use: { ...devices['Desktop Chrome'], launchOptions: { executablePath: localChromePath } },
  }],
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1 --port 4173',
    url: 'http://127.0.0.1:4173/login',
    reuseExistingServer: !process.env.CI,
  },
});
