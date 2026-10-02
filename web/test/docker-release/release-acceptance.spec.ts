import { expect, test, type Locator, type Page } from '@playwright/test';

type ApiResult = { status: number; body: unknown };

async function loginAsAdmin(page: Page) {
  const loginResponse = await page.goto('/login');
  expect(loginResponse?.status()).toBe(200);
  await page.getByTestId('shared-auth-login-username').fill('admin');
  await page.getByTestId('shared-auth-login-password').fill('Admin@123456');
  await page.getByTestId('admin-console-identity-auth-login-submit').click();
  await expect(page).toHaveURL(/\/admin\/dashboard$/);
}

function isConsoleGet(responseUrl: string, pathname: string): boolean {
  const url = new URL(responseUrl);
  return url.pathname === pathname;
}

async function expectAssociatedField(panel: Locator, name: string) {
  const label = panel.getByTestId(`query-label-${name}`);
  const control = panel.getByTestId(`query-input-${name}`).locator('input, select, textarea');
  await expect(label).toHaveAttribute('for', await control.getAttribute('id') ?? 'missing-control-id');
}

async function expectEmptyFields(panel: Locator, names: string[]) {
  for (const name of names) {
    await expect(panel.getByTestId(`query-input-${name}`).locator('input, select, textarea')).toHaveValue('');
  }
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

test('pw-issue-58-docker-real-service C-ISSUE-58-REAL-SERVICE OBL-ISSUE-58-REAL-SERVICE', async ({ page }) => {
  test.setTimeout(300_000);
  const environment = (globalThis as typeof globalThis & {
    process: { env: Record<string, string | undefined> };
  }).process.env;
  const buildCommit = environment.BUILD_COMMIT;
  expect(buildCommit, 'the acceptance run must identify the checked-out commit').toMatch(/^[0-9a-f]{40}$/);

  await loginAsAdmin(page);
  await expect(page.locator('meta[name="ycsopen-build-commit"]')).toHaveAttribute('content', buildCommit!);
  expect(await page.evaluate(() => navigator.userAgent)).toContain('Chrome/');

  const tenantsLoaded = page.waitForResponse((response) => (
    response.request().method() === 'GET'
      && isConsoleGet(response.url(), '/api/v1/console/admin/tenants')
  ));
  await page.goto('/admin/tenants');
  expect((await tenantsLoaded).status()).toBe(200);

  const tenantPanel = page.getByTestId('query-panel');
  const tenantFields = ['keyword', 'verification-status', 'operating-status'];
  await expect(tenantPanel.getByTestId('query-panel-fields')).toBeVisible();
  await expect(tenantPanel.getByTestId('query-fields')).toBeVisible();
  await expect(tenantPanel.getByTestId('query-actions')).toBeVisible();
  await expect(tenantPanel.getByTestId('query-panel-toggle')).toHaveCount(0);
  for (const name of tenantFields) await expectAssociatedField(tenantPanel, name);
  await expect(tenantPanel.getByTestId('query-result-table')).toBeVisible();
  const tenantPageStatus = page.getByTestId('admin-tenant-qualification-tenants-page-status');
  await expect(tenantPageStatus).toBeVisible();
  const initialTenantPageStatus = await tenantPageStatus.innerText();

  await tenantPanel.getByTestId('query-input-keyword').locator('input').fill('Issue58查询机构');
  await tenantPanel.getByTestId('query-input-verification-status').locator('select').selectOption('VERIFIED');
  await tenantPanel.getByTestId('query-input-operating-status').locator('select').selectOption('NORMAL');
  const tenantsFiltered = page.waitForResponse((response) => (
    response.request().method() === 'GET'
      && isConsoleGet(response.url(), '/api/v1/console/admin/tenants')
  ));
  await tenantPanel.getByTestId('query-submit').click();
  expect((await tenantsFiltered).status()).toBe(200);
  await expect(page.getByTestId('admin-tenant-qualification-tenants-page-status')).toContainText('第 1 / 2 页，共 11 条');
  await expect(page.getByTestId('admin-tenant-qualification-tenants-row')).toHaveCount(10);
  const tenantsPageTwo = page.waitForResponse((response) => (
    response.request().method() === 'GET'
      && isConsoleGet(response.url(), '/api/v1/console/admin/tenants')
  ));
  await page.getByTestId('admin-tenant-qualification-tenants-next').click();
  expect((await tenantsPageTwo).status()).toBe(200);
  await expect(page.getByTestId('admin-tenant-qualification-tenants-page-status')).toContainText('第 2 / 2 页，共 11 条');
  await expect(page.getByTestId('admin-tenant-qualification-tenants-row')).toHaveCount(1);

  const tenantsReset = page.waitForResponse((response) => (
    response.request().method() === 'GET'
      && isConsoleGet(response.url(), '/api/v1/console/admin/tenants')
  ));
  await tenantPanel.getByTestId('query-reset').click();
  expect((await tenantsReset).status()).toBe(200);
  await expectEmptyFields(tenantPanel, tenantFields);
  await expect(page.getByTestId('admin-tenant-qualification-tenants-page-status')).toContainText('第 1 /');
  await expect(page.getByTestId('admin-tenant-qualification-tenants-previous')).toBeDisabled();
  await expect(page.getByTestId('admin-tenant-qualification-tenants-page-status')).toHaveText(
    initialTenantPageStatus,
  );

  const auditSeedStatuses = await page.evaluate(async () => {
    const rawSession = window.sessionStorage.getItem('ycsopen.console.auth-session');
    if (!rawSession) throw new Error('authenticated browser session was not persisted');
    const { accessToken } = JSON.parse(rawSession) as { accessToken?: string };
    if (!accessToken) throw new Error('authenticated browser session has no access token');
    const responses = await Promise.all(Array.from({ length: 25 }, () => (
      fetch('/api/v1/console/channels', {
        headers: { Authorization: `Bearer ${accessToken}` },
      })
    )));
    return responses.map((response) => response.status);
  });
  expect(auditSeedStatuses).toEqual(Array.from({ length: 25 }, () => 200));

  const auditsLoaded = page.waitForResponse((response) => (
    response.request().method() === 'GET'
      && isConsoleGet(response.url(), '/api/v1/console/operation-audits')
  ));
  await page.goto('/admin/system/logs');
  expect((await auditsLoaded).status()).toBe(200);

  const auditPanel = page.getByTestId('query-panel');
  const auditFields = ['actor', 'operation', 'result', 'from', 'to'];
  await expect(auditPanel.getByTestId('query-panel-fields')).toBeHidden();
  await auditPanel.getByTestId('query-panel-toggle').click();
  await expect(auditPanel.getByTestId('query-fields')).toBeVisible();
  await expect(auditPanel.getByTestId('query-actions')).toBeVisible();
  for (const name of auditFields) await expectAssociatedField(auditPanel, name);
  await auditPanel.getByTestId('query-input-actor').locator('input').fill('admin');
  await auditPanel.getByTestId('query-input-operation').locator('input').fill('GET /api/v1/console/channels');
  await auditPanel.getByTestId('query-input-result').locator('select').selectOption('SUCCESS');
  const auditFrom = '2000-01-01T00:00';
  const auditTo = '2099-12-31T23:59';
  const [expectedAuditFrom, expectedAuditTo] = await page.evaluate(
    ([from, to]) => [new Date(from).toISOString(), new Date(to).toISOString()],
    [auditFrom, auditTo],
  );
  await auditPanel.getByTestId('query-input-from').locator('input').fill(auditFrom);
  await auditPanel.getByTestId('query-input-to').locator('input').fill(auditTo);

  const hasAuditQuery = (responseUrl: string, pageNumber: string) => {
    const params = new URL(responseUrl).searchParams;
    return params.get('actor') === 'admin'
      && params.get('operation') === 'GET /api/v1/console/channels'
      && params.get('result') === 'SUCCESS'
      && params.get('from') === expectedAuditFrom
      && params.get('to') === expectedAuditTo
      && params.get('page') === pageNumber
      && params.get('size') === '20';
  };

  const filteredAudits = page.waitForResponse((response) => {
    if (response.request().method() !== 'GET' || !isConsoleGet(response.url(), '/api/v1/console/operation-audits')) return false;
    return hasAuditQuery(response.url(), '0');
  });
  await auditPanel.getByTestId('query-submit').click();
  expect((await filteredAudits).status()).toBe(200);
  const auditRows = page.getByTestId('admin-privileged-data-system-logs-row');
  await expect(auditRows).toHaveCount(20);
  for (const text of await auditRows.allTextContents()) {
    expect(text).toContain('GET /api/v1/console/channels');
    expect(text).toContain('SUCCESS');
  }
  const filteredTotal = Number((await page.getByTestId('admin-privileged-data-system-logs-page-status').innerText()).match(/共 (\d+) 条/)?.[1]);
  expect(filteredTotal).toBeGreaterThanOrEqual(25);

  const auditPageTwo = page.waitForResponse((response) => {
    if (response.request().method() !== 'GET' || !isConsoleGet(response.url(), '/api/v1/console/operation-audits')) return false;
    return hasAuditQuery(response.url(), '1');
  });
  await page.getByTestId('admin-privileged-data-system-logs-next').click();
  expect((await auditPageTwo).status()).toBe(200);
  await expect(page.getByTestId('admin-privileged-data-system-logs-page-status')).toContainText('第 2 页');
  await expect(auditRows.first()).toBeVisible();
  for (const text of await auditRows.allTextContents()) {
    expect(text).toContain('GET /api/v1/console/channels');
    expect(text).toContain('SUCCESS');
  }

  const auditsReset = page.waitForResponse((response) => {
    if (response.request().method() !== 'GET' || !isConsoleGet(response.url(), '/api/v1/console/operation-audits')) return false;
    const params = new URL(response.url()).searchParams;
    return !params.has('actor') && !params.has('operation') && !params.has('result')
      && !params.has('from') && !params.has('to')
      && params.get('page') === '0' && params.get('size') === '20';
  });
  await auditPanel.getByTestId('query-reset').click();
  expect((await auditsReset).status()).toBe(200);
  await expectEmptyFields(auditPanel, auditFields);
  await expect(page.getByTestId('admin-privileged-data-system-logs-page-status')).toContainText('第 1 页');
  await expect(auditPanel.getByTestId('query-result-table')).toContainText('GET /api/v1/console/operation-audits');
  await auditPanel.getByTestId('query-panel-toggle').click();
  await page.reload();
  await expect(page.getByTestId('query-panel').getByTestId('query-panel-fields')).toBeHidden();

  const uplinksLoaded = page.waitForResponse((response) => (
    response.request().method() === 'GET'
      && isConsoleGet(response.url(), '/api/v1/console/uplinks')
      && new URL(response.url()).search === ''
  ));
  await page.goto('/admin/uplink');
  expect((await uplinksLoaded).status()).toBe(200);

  const uplinkPanel = page.getByTestId('query-panel').first();
  const uplinkFields = ['tenant-id', 'phone-number', 'keyword', 'carrier', 'push-state', 'start-time', 'end-time'];
  await expect(uplinkPanel.getByTestId('query-panel-fields')).toBeHidden();
  await uplinkPanel.getByTestId('query-panel-toggle').click();
  await expect(uplinkPanel.getByTestId('query-fields')).toBeVisible();
  await expect(uplinkPanel.getByTestId('query-actions')).toBeVisible();
  for (const name of uplinkFields) await expectAssociatedField(uplinkPanel, name);
  const uplinkResult = uplinkPanel.getByTestId('query-result-table');
  await expect(uplinkResult).toContainText('Issue58查询目标');
  await expect(uplinkResult).toContainText('Issue58基线');
  const targetRow = page.getByTestId('admin-uplink-normalization-uplinks-row').filter({ hasText: 'Issue58查询目标' });
  const tenantId = (await targetRow.locator('td').first().innerText()).trim();

  await uplinkPanel.getByTestId('query-input-tenant-id').locator('input').fill(tenantId);
  await uplinkPanel.getByTestId('query-input-phone-number').locator('input').fill('13800138058');
  await uplinkPanel.getByTestId('query-input-keyword').locator('input').fill('Issue58查询目标');
  await uplinkPanel.getByTestId('query-input-carrier').locator('input').fill('CMCC');
  await uplinkPanel.getByTestId('query-input-push-state').locator('select').selectOption('PUSH_FAILED');
  await uplinkPanel.getByTestId('query-input-start-time').locator('input').fill('2026-09-12T00:00');
  await uplinkPanel.getByTestId('query-input-end-time').locator('input').fill('2026-09-13T00:00');

  const uplinksFiltered = page.waitForResponse((response) => {
    if (response.request().method() !== 'GET' || !isConsoleGet(response.url(), '/api/v1/console/uplinks')) return false;
    const params = new URL(response.url()).searchParams;
    return params.get('tenantId') === tenantId
      && params.get('phoneNumber') === '13800138058'
      && params.get('keyword') === 'Issue58查询目标'
      && params.get('carrier') === 'CMCC'
      && params.get('pushState') === 'PUSH_FAILED'
      && params.get('startTime') === '2026-09-12T00:00'
      && params.get('endTime') === '2026-09-13T00:00';
  });
  await uplinkPanel.getByTestId('query-submit').click();
  expect((await uplinksFiltered).status()).toBe(200);
  await expect(page.getByTestId('admin-uplink-normalization-uplinks-row')).toHaveCount(1);
  await expect(uplinkResult).toContainText('Issue58查询目标');
  await expect(uplinkResult).not.toContainText('Issue58基线');
  await expect(page.getByTestId('admin-uplink-normalization-uplinks-card-total')).toContainText('1');

  const uplinksReset = page.waitForResponse((response) => (
    response.request().method() === 'GET'
      && isConsoleGet(response.url(), '/api/v1/console/uplinks')
      && new URL(response.url()).search === ''
  ));
  await uplinkPanel.getByTestId('query-reset').click();
  expect((await uplinksReset).status()).toBe(200);
  await expectEmptyFields(uplinkPanel, uplinkFields);
  await expect(uplinkResult).toContainText('Issue58查询目标');
  await expect(uplinkResult).toContainText('Issue58基线');
  await uplinkPanel.getByTestId('query-panel-toggle').click();
  await page.reload();
  await expect(page.getByTestId('query-panel').first().getByTestId('query-panel-fields')).toBeHidden();
});

test('pw-issue-90-release-tenant-status-action C-ISSUE-90-RELEASE-TENANT-ACCOUNT OBL-ISSUE-90-RELEASE-TENANT-ACCOUNT', async ({ page }) => {
  await loginAsAdmin(page);
  await page.goto('/admin/tenants');

  const releaseTenant = page.getByRole('row').filter({ hasText: 'DEV-TENANT' });
  await expect(releaseTenant).toBeVisible();
  await expect(releaseTenant.getByTestId('admin-tenant-qualification-tenants-status-action')).toBeVisible();
});

test('pw-issue-91-docker-release C-ISSUE-91-REAL-SERVICE OBL-ISSUE-91-REAL-SERVICE', async ({ page }) => {
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
