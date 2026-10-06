import { expect, test, type Page, type Route } from '@playwright/test';

const workbenchRoute = /\/api\/v1\/console\/contracts\/workbench(?:\?.*)?$/;

function token(subject: string) {
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({ sub: subject, jti: `p37-${subject}`, exp: 4102444800 })}.signature`;
}

function response(data: unknown) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-10T00:00:00Z', traceId: 'trace-p37' };
}

function candidate(tenantId: number, eligible = true) {
  return {
    tenantId,
    tenantNo: `TENANT-${tenantId}`,
    shortName: tenantId === 42 ? 'Acme 短信' : 'Beacon 短信',
    fullName: tenantId === 42 ? 'Acme Messaging Ltd' : 'Beacon Messaging Ltd',
    salesOwner: tenantId === 42 ? 'Alice' : 'Bob',
    industry: 'SaaS',
    configurationSnapshotVersion: `TRIAL-SNAPSHOT-V1-${tenantId}34567890ABCDEF`,
    lifecycleStatus: 'TRIAL_FROZEN',
    trialStatus: 'TRIAL_FROZEN',
    trialStartAt: '2026-09-01T00:00:00',
    trialEndAt: '2026-09-30T00:00:00',
    remainingDays: 0,
    quotaUsed: 400,
    quotaTotal: 500,
    messageCount: 2,
    successCount: 1,
    successRate: 0.5,
    complaintCount: 1,
    complaintRate: 0.5,
    statisticsAt: '2026-09-07T12:00:00',
    dataQuality: 'COMPLETE',
    sourceRegistry: 'tenants:trial_accounts:message_tasks:complaints',
    conversionEligible: eligible,
    ineligibilityReasons: eligible ? [] : ['机构账户已停用或冻结'],
  };
}

async function operatorSession(page: Page) {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('operator'), userType: 'OPERATOR', tenantId: null });
}

async function fillContract(page: Page, contractNo = 'HT-2026-0001') {
  await page.getByTestId('admin-contract-pricing-tenant-contract-number').fill(contractNo);
  await page.getByTestId('admin-contract-pricing-tenant-contract-signed-date').fill('2026-09-10');
  await page.getByTestId('admin-contract-pricing-tenant-contract-attachment').fill(`oss://contracts/${contractNo}.pdf`);
  await page.getByTestId('admin-contract-pricing-tenant-contract-credit-limit').fill('1000000');
}

test.beforeEach(async ({ page }) => {
  let rows = [candidate(42), candidate(43, false)];
  await page.route('**/api/v1/console/account-overview', (route) => route.fulfill({
    json: response({ id: 37, username: 'operator-37', userType: 'OPERATOR', roleNames: ['运营'], permissions: [
      { code: 'trial-prepaid:menu', resourceType: 'MENU' },
      { code: 'trial-prepaid:read', resourceType: 'API' },
      { code: 'trial-prepaid:write', resourceType: 'API' },
    ], lastLoginAt: null, lastLoginIp: null }),
  }));
  await page.route(workbenchRoute, (route) => route.fulfill({ json: response(rows) }));
  await page.route('**/api/v1/console/contracts/price-books', (route) => route.fulfill({
    json: response([{ priceBookVersion: 'SMS_STANDARD_V1', productCode: 'SMS', unitPriceMil: 50 }]),
  }));
  await page.route('**/api/v1/console/contracts/workbench/tenants/42/analysis', (route) => route.fulfill({
    json: response({ tenant: candidate(42), periodStart: '2026-09-01T00:00:00', periodEnd: '2026-09-30T00:00:00',
      trend: [{ date: '2026-09-05', messageCount: 2, successCount: 1, failureCount: 1, complaintCount: 1 }],
      messageStatuses: [{ status: 'DELIVERED', count: 1 }, { status: 'FAILED', count: 1 }],
      complaints: [{ id: 7, source: 'OPERATOR', messageId: 'MSG-7', summary: '内容投诉', status: 'PROCESSING', createdAt: '2026-09-05T12:00:00' }],
      sourceRegistry: 'tenants:trial_accounts:message_tasks:complaints', statisticsAt: '2026-09-07T12:00:00', dataQuality: 'COMPLETE' }),
  }));
  await page.route('**/api/v1/console/trial-prepaid/balance-audits?**', (route) => route.fulfill({ json: response([]) }));
  await page.route('**/api/v1/console/contracts/tenants/42', async (route: Route) => {
    if (route.request().method() !== 'POST') return route.fallback();
    rows = rows.filter((row) => row.tenantId !== 42);
    await route.fulfill({ json: response({ tenantId: 42, billingMode: 'POSTPAID', priceBookVersion: 'SMS_STANDARD_V1',
      contractNo: 'HT-2026-0001', signedAt: '2026-09-10', attachmentRef: 'oss://contracts/HT-2026-0001.pdf',
      creditLimitMil: 1000000, billingPeriod: 'MONTHLY', contractStatus: 'ACTIVE', approvedBy: 'operator' }) });
  });
  await page.route('**/api/v1/console/contracts/tenants/42/overview', (route) => route.fulfill({
    json: response({ tenantId: 42, tenantState: 'CONTRACTED', billingMode: 'POSTPAID', priceBookVersion: 'SMS_STANDARD_V1',
      contractNo: 'HT-2026-0001', signedAt: '2026-09-10', attachmentRef: 'oss://contracts/HT-2026-0001.pdf',
      creditLimitMil: 1000000, billingPeriod: 'MONTHLY', contractStatus: 'ACTIVE', approvedBy: 'operator' }),
  }));
  await page.route('**/api/v1/console/trial-prepaid/tenants/42/overview', (route) => route.fulfill({
    json: response({ tenantId: 42, trialStatus: 'TRIAL_FROZEN', quotaTotal: 500, quotaRemaining: 0,
      validFrom: '2026-09-09T00:00:00', validUntil: '2026-09-23T00:00:00', version: 3 }),
  }));
});

test('pw-p37-admin-contract C-P37-ADMIN-CONTRACT OBL-F-2-9-A pw-p37-billing-mode C-P37-BILLING-MODE OBL-F-2-5-A pw-p37-postpaid-fields C-P37-POSTPAID-FIELDS OBL-F-2-9-B pw-p37-flow-contract C-P37-FLOW-CONTRACT OBL-FLOW-12-1-CONTRACT', async ({ page }) => {
  await operatorSession(page);
  const filtered = page.waitForRequest((request) => request.url().includes('/contracts/workbench?') && request.url().includes('keyword=Acme'));
  await page.goto('/admin/tenant-trial-contracts');
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-page')).toBeVisible();
  await expect(page.getByTestId('admin-trial-conversion-workbench-row')).toHaveCount(2);
  const filters = page.getByTestId('admin-trial-conversion-workbench-filters');
  await page.getByTestId('admin-trial-conversion-workbench-filter-keyword').fill('Acme');
  await page.getByTestId('admin-trial-conversion-workbench-filter-sales-owner').fill('Alice');
  await page.getByTestId('admin-trial-conversion-workbench-filter-industry').fill('SaaS');
  await page.getByTestId('admin-trial-conversion-workbench-filter-status').selectOption('TRIAL_FROZEN');
  await filters.getByTestId('query-submit').click();
  await filtered;

  const selectedRow = page.getByTestId('admin-trial-conversion-workbench-row').filter({ hasText: 'TENANT-42' });
  await selectedRow.getByTestId('admin-trial-conversion-workbench-row-analysis').click();
  await expect(page.getByTestId('admin-trial-conversion-workbench-analysis-dialog')).toContainText('内容投诉');
  await page.getByTestId('admin-trial-conversion-workbench-analysis-close').click();

  await selectedRow.getByTestId('admin-trial-conversion-workbench-row-convert').click();
  await expect(page.getByTestId('admin-trial-conversion-workbench-selected-tenant')).toContainText('TENANT-42');
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-billing-mode')).toHaveValue('POSTPAID');
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-postpaid-fields')).toBeVisible();
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-number')).toHaveValue('');
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-price-version')).toHaveValue('SMS_STANDARD_V1');
  await fillContract(page);
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-credit-period')).toContainText('账期');
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-billing-period')).toHaveValue('MONTHLY');
  let posts = 0;
  page.on('request', (request) => { if (request.method() === 'POST' && request.url().endsWith('/contracts/tenants/42')) posts += 1; });
  await page.getByTestId('admin-contract-pricing-tenant-contract-approve').click();
  await expect(page.getByTestId('admin-trial-prepaid-message')).toContainText('POSTPAID');
  await expect(page.getByTestId('admin-trial-conversion-workbench-row').filter({ hasText: 'TENANT-42' })).toHaveCount(0);
  await expect(page.getByTestId('admin-trial-conversion-workbench-row').filter({ hasText: 'TENANT-43' })).toHaveCount(1);
  expect(posts).toBe(1);
});

test('pw-p37-credit-period C-P37-CREDIT-PERIOD OBL-F-2-5-B preserves rejected selected-tenant form', async ({ page }) => {
  await operatorSession(page);
  await page.unroute('**/api/v1/console/contracts/tenants/42');
  await page.route('**/api/v1/console/contracts/tenants/42', (route) => route.fulfill({
    status: 400,
    json: { code: 'TRIAL_CONVERSION_NOT_ELIGIBLE', message: '机构生命周期不允许转正式', data: null },
  }));
  await page.goto('/admin/tenant-trial-contracts');
  const selectedRow = page.getByTestId('admin-trial-conversion-workbench-row').filter({ hasText: 'TENANT-42' });
  await selectedRow.getByTestId('admin-trial-conversion-workbench-row-convert').click();
  await fillContract(page, 'HT-KEEP-122');
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-postpaid-fields')).toBeVisible();
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-credit-period')).toContainText('账期');
  await page.getByTestId('admin-contract-pricing-tenant-contract-approve').click();
  await expect(page.getByTestId('admin-trial-conversion-workbench-conversion-feedback')).toContainText('机构生命周期不允许转正式');
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-number')).toHaveValue('HT-KEEP-122');
  await expect(page.getByTestId('admin-trial-conversion-workbench-selected-tenant')).toContainText('TENANT-42');
});

test('selected-row trial adjustment keeps tenant identity immutable', async ({ page }) => {
  await operatorSession(page);
  await page.route('**/api/v1/console/trial-prepaid/tenants/42/trial', (route) => route.fulfill({
    json: response({ tenantId: 42, trialStatus: 'TRIAL', quotaTotal: 600, quotaRemaining: 600,
      validFrom: '2026-09-09T00:00:00', validUntil: '2026-09-30T00:00:00', version: 4 }),
  }));
  await page.goto('/admin/tenant-trial-contracts');
  const selectedRow = page.getByTestId('admin-trial-conversion-workbench-row').filter({ hasText: 'TENANT-42' });
  await selectedRow.getByTestId('admin-trial-conversion-workbench-row-adjust').click();
  const dialog = page.getByTestId('admin-trial-conversion-workbench-adjust-dialog');
  await expect(dialog).toContainText('TENANT-42');
  await expect(dialog.getByTestId('entity-form')).toBeVisible();
  await dialog.getByTestId('admin-trial-prepaid-tenant-trial-quota').fill('600');
  await expect(dialog.getByTestId('form-submit')).toBeEnabled();
  await expect(dialog.getByTestId('form-cancel')).toBeEnabled();
  await dialog.getByTestId('admin-trial-conversion-workbench-adjust-cancel').click();
  await expect(dialog).toBeHidden();
});

test('workbench hides stale rows on error and retry restores the exact table contract', async ({ page }) => {
  await operatorSession(page);
  await page.unroute(workbenchRoute);
  let attempts = 0;
  await page.route(workbenchRoute, (route) => {
    attempts += 1;
    return attempts === 1 ? route.fulfill({ status: 500, json: { code: 500, message: 'failed' } })
      : route.fulfill({ json: response([candidate(42)]) });
  });
  await page.goto('/admin/tenant-trial-contracts');
  await expect(page.getByTestId('admin-trial-conversion-workbench-query-status')).toHaveAttribute('data-state', 'error');
  await expect(page.getByTestId('admin-trial-conversion-workbench-row')).toHaveCount(0);
  await expect(page.getByTestId('data-table')).toBeVisible();
  await expect(page.getByTestId('table-empty')).toBeVisible();
  await page.getByTestId('admin-trial-conversion-workbench-filters').getByTestId('query-refresh').click();
  await expect(page.getByTestId('admin-trial-conversion-workbench-row')).toHaveCount(1);
});

test('analysis exposes loading, error, then no-data readback', async ({ page }) => {
  await operatorSession(page);
  await page.unroute('**/api/v1/console/contracts/workbench/tenants/42/analysis');
  let attempts = 0;
  await page.route('**/api/v1/console/contracts/workbench/tenants/42/analysis', async (route) => {
    attempts += 1;
    await new Promise((resolve) => setTimeout(resolve, 150));
    if (attempts === 1) return route.fulfill({ status: 500, json: { code: 500, message: 'failed' } });
    return route.fulfill({ json: response({ tenant: candidate(42), periodStart: '2026-09-01T00:00:00',
      periodEnd: '2026-09-30T00:00:00', trend: [], messageStatuses: [], complaints: [],
      sourceRegistry: 'tenants:trial_accounts:message_tasks:complaints', statisticsAt: '2026-09-07T12:00:00', dataQuality: 'NO_DATA' }) });
  });
  await page.goto('/admin/tenant-trial-contracts');
  const selectedRow = page.getByTestId('admin-trial-conversion-workbench-row').filter({ hasText: 'TENANT-42' });
  await selectedRow.getByTestId('admin-trial-conversion-workbench-row-analysis').click();
  await expect(page.getByText('正在加载试用分析…')).toBeVisible();
  await expect(page.getByRole('alert').filter({ hasText: '试用分析加载失败' })).toBeVisible();
  await page.getByTestId('admin-trial-conversion-workbench-analysis-close').click();
  await selectedRow.getByTestId('admin-trial-conversion-workbench-row-analysis').click();
  await expect(page.getByText('试用期内暂无消息与投诉数据。')).toBeVisible();
});

test('active price error retries inside the selected-tenant dialog', async ({ page }) => {
  await operatorSession(page);
  await page.unroute('**/api/v1/console/contracts/price-books');
  let attempts = 0;
  await page.route('**/api/v1/console/contracts/price-books', (route) => {
    attempts += 1;
    return attempts === 1
      ? route.fulfill({ status: 500, json: { code: 500, message: 'failed' } })
      : route.fulfill({ json: response([{
        priceBookVersion: 'SMS_STANDARD_V1', productCode: 'SMS', unitPriceMil: 50,
      }]) });
  });
  await page.goto('/admin/tenant-trial-contracts');
  const selectedRow = page.getByTestId('admin-trial-conversion-workbench-row').filter({ hasText: 'TENANT-42' });
  await selectedRow.getByTestId('admin-trial-conversion-workbench-row-convert').click();
  await page.getByTestId('admin-trial-conversion-workbench-price-retry').click();
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-price-version'))
    .toHaveValue('SMS_STANDARD_V1');
});

test('pw-p37-tenant-contract-status C-P37-TENANT-CONTRACT-STATUS OBL-F-2-9-C pw-p37-state-contract C-P37-STATE-CONTRACT OBL-STATE-TENANT-CONTRACT', async ({ page }) => {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('tenant'), userType: 'TENANT_ADMIN', tenantId: 42 });
  await page.goto('/tenant/overview');
  await expect(page.getByTestId('tenant-contract-pricing-overview-contract-status')).toContainText('CONTRACTED');
  await expect(page.getByTestId('tenant-contract-pricing-overview-contract-status')).toContainText('SMS_STANDARD_V1');
  await expect(page.getByTestId('tenant-contract-pricing-overview-contract-status')).toContainText('MONTHLY');
});
