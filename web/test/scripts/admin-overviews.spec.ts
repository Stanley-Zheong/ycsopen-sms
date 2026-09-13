import { expect, test } from '@playwright/test';

function token(subject: string) {
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({ sub: subject, jti: `overview-${subject}`, exp: 4102444800 })}.signature`;
}

function response(data: unknown) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-13T00:00:00Z', traceId: 'trace-overview' };
}

test.beforeEach(async ({ page }) => {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('admin'), userType: 'ADMIN', tenantId: null });
  await page.route('**/api/v1/console/account-overview', (route) => route.fulfill({ json: response({
    id: 1, username: 'admin', userType: 'ADMIN', roleNames: ['系统管理员'], permissions: [], lastLoginAt: null, lastLoginIp: null,
  }) }));
  await page.route('**/api/v1/console/operational-dashboards/resource-statistics**', (route) => route.fulfill({ json: response({
    resources: [{ tenantId: 7, signatureId: 11, templateId: 12, submitCount: 10, successCount: 9, rejectedCount: 1, freshnessAt: '2026-09-13T00:00:00' }],
    channelComparisons: [], accessibleColumns: ['tenant_id'], empty: false, errorState: 'NONE',
    source: { registry: 'statistics_aggregates', formula: 'success/send', freshnessAt: null, permissionScope: 'PLATFORM', formulaVersion: 'v1' },
  }) }));
});

test('admin overview routes expose real pages and consolidated detail actions', async ({ page }) => {
  await page.goto('/admin/tools');
  await expect(page.getByTestId('admin-tools-overview-page')).toBeVisible();
  await expect(page.getByTestId('admin-tools-overview-table')).toContainText('号码归属与携号转网');

  await page.goto('/admin/statistics');
  await expect(page.getByTestId('admin-statistics-overview-page')).toBeVisible();
  await expect(page.getByTestId('admin-statistics-overview-row')).toContainText('10');

  await page.goto('/admin/records');
  await expect(page.getByTestId('admin-message-receipt-operations-page')).toBeVisible();
  await expect(page.getByRole('heading', { name: '详单总览' })).toBeVisible();
  await expect(page.getByTestId('admin-message-receipt-tab-submissions')).toBeVisible();
  await expect(page.getByTestId('admin-message-receipt-tab-sends')).toBeVisible();
  await expect(page.getByTestId('admin-message-receipt-tab-receipts')).toBeVisible();
});
