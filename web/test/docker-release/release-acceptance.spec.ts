import { expect, test } from '@playwright/test';

type ApiResult = { status: number; body: unknown };

test('pw-issue-60-docker-release C-ISSUE-60-BROWSER OBL-ISSUE-60-FRESH OBL-ISSUE-60-DASHBOARD OBL-ISSUE-60-SEED OBL-ISSUE-60-IDENTITY', async ({ page }) => {
  const environment = (globalThis as typeof globalThis & {
    process: { env: Record<string, string | undefined> };
  }).process.env;
  const buildCommit = environment.BUILD_COMMIT;
  expect(buildCommit, 'the acceptance run must identify the checked-out commit').toMatch(/^[0-9a-f]{40}$/);

  const dashboardFailures: string[] = [];
  page.on('response', (response) => {
    if (response.url().includes('/api/v1/console/dashboard/') && response.status() >= 500) {
      dashboardFailures.push(`${response.status()} ${response.url()}`);
    }
  });

  const loginResponse = await page.goto('/login');
  expect(loginResponse?.status()).toBe(200);
  await page.getByTestId('shared-auth-login-username').fill('admin');
  await page.getByTestId('shared-auth-login-password').fill('Admin@123456');
  await page.getByTestId('admin-console-identity-auth-login-submit').click();

  await expect(page).toHaveURL(/\/admin\/dashboard$/);
  await expect(page.getByTestId('admin-dashboard-page')).toBeVisible();
  await expect.poll(() => dashboardFailures).toEqual([]);

  const results = await page.evaluate(async () => {
    const rawSession = window.sessionStorage.getItem('ycsopen.console.auth-session');
    if (!rawSession) throw new Error('authenticated browser session was not persisted');
    const { accessToken } = JSON.parse(rawSession) as { accessToken?: string };
    if (!accessToken) throw new Error('authenticated browser session has no access token');

    const get = async (path: string): Promise<ApiResult> => {
      const response = await fetch(path, { headers: { Authorization: `Bearer ${accessToken}` } });
      return { status: response.status, body: await response.json() as unknown };
    };

    return {
      dashboardChannel: await get('/api/v1/console/dashboard/complaint-ratio/channel'),
      dashboardTenant: await get('/api/v1/console/dashboard/complaint-ratio/tenant'),
      channels: await get('/api/v1/console/channels'),
      templates: await get('/api/v1/console/templates/review'),
      prefixes: await get('/api/v1/console/number-attribution/prefixes/versions'),
      coreInfo: await get('/actuator/info'),
    };
  });

  expect(results.dashboardChannel.status).toBeLessThan(500);
  expect(results.dashboardTenant.status).toBeLessThan(500);
  expect(results.channels.status).toBe(200);
  expect(results.templates.status).toBe(200);
  expect(results.prefixes.status).toBe(200);
  expect(results.coreInfo.status).toBe(200);
  expect(dashboardFailures).toEqual([]);

  const channels = results.channels.body as { data: Array<{ channelName: string }> };
  const templates = results.templates.body as { data: { items: Array<{ templateCode: string }> } };
  const prefixes = results.prefixes.body as { data: Array<{ versionNo: string }> };
  const coreInfo = results.coreInfo.body as { build?: { commit?: string } };

  expect(channels.data.some((channel) => channel.channelName === 'DEV-CMPP-PRIMARY')).toBe(true);
  expect(templates.data.items.some((template) => template.templateCode === 'DEV-VERIFY-CODE')).toBe(true);
  expect(prefixes.data.some((version) => version.versionNo === 'DEV-PREFIX-2026-09')).toBe(true);
  expect(coreInfo.build?.commit).toBe(buildCommit);
  await expect(page.locator('meta[name="ycsopen-build-commit"]')).toHaveAttribute('content', buildCommit!);
});
