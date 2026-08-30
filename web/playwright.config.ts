import { defineConfig } from '@playwright/test';

const standardChromePath = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
export const DEFAULT_PHASE01_BASE_URL = 'http://127.0.0.1:41737';

export function resolvePhase01BaseURL(candidate = process.env.PHASE01_BASE_URL) {
  const rawValue = candidate ?? DEFAULT_PHASE01_BASE_URL;
  let parsed: URL;
  try {
    parsed = new URL(rawValue);
  } catch {
    throw new Error('PHASE01_BASE_URL_INVALID');
  }
  if (parsed.protocol !== 'http:' || parsed.hostname !== '127.0.0.1') {
    throw new Error('PHASE01_BASE_URL_LOOPBACK_REQUIRED');
  }
  if (parsed.username || parsed.password || parsed.pathname !== '/' || parsed.search || parsed.hash) {
    throw new Error('PHASE01_BASE_URL_COMPONENTS_FORBIDDEN');
  }
  const port = Number(parsed.port);
  if (!Number.isInteger(port) || port <= 0 || port > 65_535) {
    throw new Error('PHASE01_BASE_URL_PORT_REQUIRED');
  }
  return parsed.origin;
}
const baseURL = resolvePhase01BaseURL();
process.env.PHASE01_BASE_URL = baseURL;

export default defineConfig({
  testDir: './test/phase01',
  fullyParallel: false,
  forbidOnly: true,
  retries: 0,
  workers: 1,
  timeout: 30_000,
  expect: { timeout: 5_000 },
  outputDir: 'test-results/phase01-structural',
  reporter: [
    ['json', { outputFile: 'test-results/phase01-structural/report.json' }],
    ['junit', { outputFile: 'test-results/phase01-structural/junit.xml' }],
    ['line'],
  ],
  use: {
    baseURL,
    launchOptions: { executablePath: standardChromePath },
    viewport: { width: 1440, height: 900 },
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    video: 'off',
  },
  projects: [
    {
      name: 'local-google-chrome',
    },
  ],
});
