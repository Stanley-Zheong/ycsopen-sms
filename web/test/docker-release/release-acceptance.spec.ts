import { expect, test, type Page } from '@playwright/test';

type ApiResult = { status: number; body: unknown };

async function loginAsAdmin(page: Page) {
  const loginResponse = await page.goto('/login');
  expect(loginResponse?.status()).toBe(200);
  await page.getByTestId('shared-auth-login-username').fill('admin');
  await page.getByTestId('shared-auth-login-password').fill('Admin@123456');
  await page.getByTestId('admin-console-identity-auth-login-submit').click();
  await expect(page).toHaveURL(/\/admin\/dashboard$/);
}

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

  await loginAsAdmin(page);
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

test('pw-issue-90-release-tenant-status-action C-ISSUE-90-RELEASE-TENANT-ACCOUNT OBL-ISSUE-90-RELEASE-TENANT-ACCOUNT', async ({ page }) => {
  await loginAsAdmin(page);
  await page.goto('/admin/tenants');

  const releaseTenant = page.getByRole('row').filter({ hasText: 'DEV-TENANT' });
  await expect(releaseTenant).toBeVisible();
  await expect(releaseTenant.getByTestId('admin-tenant-qualification-tenants-status-action')).toBeVisible();
});

test('pw-issue-91-docker-release C-ISSUE-91-REAL-SERVICE OBL-ISSUE-91-ACTION-REASON', async ({ page }) => {
  const environment = (globalThis as typeof globalThis & {
    process: { env: Record<string, string | undefined> };
  }).process.env;
  const buildCommit = environment.BUILD_COMMIT;
  expect(buildCommit, 'the acceptance run must identify the checked-out commit').toMatch(/^[0-9a-f]{40}$/);

  await loginAsAdmin(page);
  await expect(page.locator('meta[name="ycsopen-build-commit"]')).toHaveAttribute('content', buildCommit!);
  expect(await page.evaluate(() => navigator.userAgent)).toContain('Chrome/');

  const routes = [
    { path: '/admin/tenant-recharge-review', pageId: 'admin-tenant-recharge-operations-review-page', reasons: ['admin-tenant-recharge-operations-review-reason'] },
    { path: '/admin/uplink', pageId: 'admin-uplink-normalization-uplinks-page', reasons: ['admin-uplink-normalization-uplink-replay-reason', 'admin-uplink-normalization-push-action-reason'] },
    { path: '/admin/submission/details', pageId: 'admin-message-receipt-submission-details-page', reasons: ['admin-message-receipt-action-reason'] },
    { path: '/admin/send/details', pageId: 'admin-message-receipt-send-details-page', reasons: ['admin-message-receipt-action-reason'] },
    { path: '/admin/receipt/details', pageId: 'admin-message-receipt-receipt-details-page', reasons: ['admin-message-receipt-action-reason'] },
    { path: '/admin/error/details', pageId: 'admin-message-receipt-error-details-page', reasons: ['admin-message-receipt-action-reason'] },
    { path: '/admin/alerts', pageId: 'admin-alert-engine-page', reasons: ['admin-alert-engine-resolve-reason', 'admin-alert-engine-mute-reason'] },
    { path: '/admin/push/failures', pageId: 'admin-webhook-delivery-push-failures-page', reasons: ['admin-webhook-delivery-action-reason'] },
    { path: '/admin/send/jobs', pageId: 'admin-bulk-scheduled-send-jobs-page', reasons: ['admin-bulk-scheduled-send-jobs-reason'] },
  ];

  for (const route of routes) {
    await page.goto(route.path);
    await expect(page.getByTestId(route.pageId)).toBeVisible();
    for (const reasonId of route.reasons) await expect(page.getByTestId(reasonId)).toHaveCount(0);
  }

  await page.goto('/admin/tenant-recharge-review');
  const fixtureRow = page.getByTestId('admin-tenant-recharge-operations-review-row').filter({ hasText: 'ISSU****0091' });
  await expect(fixtureRow).toContainText('PENDING');
  await fixtureRow.getByTestId('admin-tenant-recharge-operations-review-approve').click();
  await expect(page.getByTestId('admin-tenant-recharge-operations-review-action-target')).toContainText('充值申请');
  await expect(page.getByTestId('admin-tenant-recharge-operations-review-action-consequence')).toContainText('一次性计入机构预付费可用余额');
  await page.getByTestId('admin-tenant-recharge-operations-review-reason').fill('Issue 91 Google Chrome 实际服务验收');

  let reviewRequestCount = 0;
  page.on('request', (request) => {
    const path = new URL(request.url()).pathname;
    if (request.method() === 'POST' && /^\/api\/v1\/console\/recharges\/\d+\/review$/.test(path)) reviewRequestCount += 1;
  });
  const reviewResponse = page.waitForResponse((response) => /^\/api\/v1\/console\/recharges\/\d+\/review$/.test(new URL(response.url()).pathname));
  await page.getByTestId('admin-tenant-recharge-operations-review-action-confirm').dblclick();
  expect((await reviewResponse).status()).toBe(200);
  await expect(page.getByTestId('admin-tenant-recharge-operations-review-message')).toContainText('APPROVED');
  expect(reviewRequestCount).toBe(1);

  const readback = await page.evaluate(async () => {
    const rawSession = window.sessionStorage.getItem('ycsopen.console.auth-session');
    if (!rawSession) throw new Error('authenticated browser session was not persisted');
    const { accessToken } = JSON.parse(rawSession) as { accessToken?: string };
    if (!accessToken) throw new Error('authenticated browser session has no access token');
    const headers = { Authorization: `Bearer ${accessToken}` };
    const reviewResult = await fetch('/api/v1/console/recharges/reviews?status=APPROVED', { headers });
    const reviewBody = await reviewResult.json() as { data: Array<{ id: number; tenantId: number; transactionRefMask: string; status: string; reviewReason: string | null }> };
    const record = reviewBody.data.find((row) => row.transactionRefMask === 'ISSU****0091');
    if (!record) throw new Error('approved Issue 91 fixture was not returned');
    const auditResult = await fetch(`/api/v1/console/trial-prepaid/balance-audits?tenantId=${record.tenantId}`, { headers });
    const auditBody = await auditResult.json() as { data: Array<{ businessDocId: string; mutationType: string }> };
    return {
      reviewStatus: reviewResult.status,
      auditStatus: auditResult.status,
      record,
      matchingAudits: auditBody.data.filter((row) => row.businessDocId === `RECHARGE-${record.id}` && row.mutationType === 'RECHARGE_APPROVE'),
    };
  });

  expect(readback.reviewStatus).toBe(200);
  expect(readback.auditStatus).toBe(200);
  expect(readback.record.status).toBe('APPROVED');
  expect(readback.record.reviewReason).toBe('Issue 91 Google Chrome 实际服务验收');
  expect(readback.matchingAudits).toHaveLength(1);
});
