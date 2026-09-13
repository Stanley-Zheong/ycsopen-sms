import { expect, test } from '@playwright/test';

function token(subject: string) {
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({ sub: subject, jti: `p43-${subject}`, exp: 4102444800 })}.signature`;
}

function response(data: unknown) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-10T00:00:00Z', traceId: 'trace-p43' };
}

const permissions = [
  { code: 'custom-report:menu', resourceType: 'MENU' },
  { code: 'custom-report:read', resourceType: 'API' },
  { code: 'custom-report:write', resourceType: 'API' },
];

const capability = {
  metricCode: 'CHANNEL_DELIVERY',
  metricName: '通道发送成功成本延迟指标',
  dimensions: ['period', 'tenant_id', 'channel_id', 'carrier', 'province', 'message_type'],
  measures: ['send_count', 'success_count', 'fee_amount'],
  formula: 'send/success/failure/cost/latency grouped by channel and geography',
  freshnessRule: 'freshness_at >= latest source updated_at',
  permissionScope: 'PLATFORM',
  formulaVersion: 'v1',
};

const preview = {
  metricCode: 'CHANNEL_DELIVERY',
  metricName: '通道发送成功成本延迟指标',
  formula: capability.formula,
  formulaVersion: 'v1',
  freshnessRule: capability.freshnessRule,
  freshnessAt: '2026-09-01T01:03:00',
  qualityState: 'FRESH',
  accessibleColumns: ['period', 'tenant_id', 'channel_id', 'carrier', 'province', 'message_type', 'send_count', 'success_count', 'fee_amount'],
  truncated: false,
  rows: [{
    values: {
      period: '2026-09-01',
      tenant_id: 7,
      channel_id: 11,
      carrier: 'MOBILE',
      province: '广东',
      message_type: 'VERIFY',
      send_count: 20,
      success_count: 18,
      fee_amount: 0.32,
    },
    drilldownKey: 'drill-43',
    qualityState: 'FRESH',
    freshnessAt: '2026-09-01T01:03:00',
  }],
};

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/console/account-overview', async (route) => {
    await route.fulfill({
      json: response({
        id: 43,
        username: 'finance-43',
        userType: 'FINANCE',
        roleNames: ['财务'],
        permissions,
        lastLoginAt: null,
        lastLoginIp: null,
      }),
    });
  });
  await page.route('**/api/v1/console/custom-reports/capabilities', async (route) => {
    await route.fulfill({ json: response([capability]) });
  });
  await page.route('**/api/v1/console/custom-reports/preview', async (route) => {
    await route.fulfill({ json: response(preview) });
  });
  await page.route('**/api/v1/console/custom-reports/definitions', async (route) => {
    if (route.request().method() === 'POST') {
      await route.fulfill({ json: response({ id: 5, reportName: '通道日报', metricCode: 'CHANNEL_DELIVERY', tenantId: 7, roleScope: 'PLATFORM', definitionSnapshot: '{"formulaVersion":"v1"}', status: 'ACTIVE', createdBy: 'finance-43', createdAt: '2026-09-01T02:00:00' }) });
      return;
    }
    await route.fulfill({ json: response([]) });
  });
  await page.route('**/api/v1/console/custom-reports/definitions/5/export', async (route) => {
    await route.fulfill({ json: response({ id: 6, reportDefinitionId: 5, definitionSnapshot: '{"formulaVersion":"v1"}', status: 'REQUESTED', requestedBy: 'finance-43', requestedAt: '2026-09-01T02:01:00' }) });
  });
});

test('pw-p43-custom-reports C-P43-CUSTOM-REPORTS OBL-F-11-4-A pw-p43-results C-P43-RESULTS OBL-F-11-4-B', async ({ page }) => {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('finance'), userType: 'FINANCE', tenantId: null });
  await page.goto('/admin/custom/reports');
  await expect(page.getByTestId('admin-custom-report-custom-reports-page')).toBeVisible();
  await expect(page.getByTestId('admin-custom-report-custom-reports-builder')).toContainText('period');
  await page.getByTestId('admin-custom-report-custom-reports-name').fill('通道日报');
  await page.getByTestId('admin-custom-report-custom-reports-channel').fill('11');
  await page.getByTestId('admin-custom-report-custom-reports-preview').click();
  await expect(page.getByTestId('admin-custom-report-custom-reports-results')).toContainText('drill-43');
  await expect(page.getByTestId('admin-custom-report-custom-reports-formula')).toContainText('v1');
  await expect(page.getByTestId('admin-custom-report-custom-reports-freshness')).toContainText('2026-09-01T01:03:00');
  await expect(page.getByTestId('admin-custom-report-custom-reports-accessible-table')).toContainText('MOBILE');
  await page.getByTestId('admin-custom-report-custom-reports-save').click();
  await expect(page.getByTestId('admin-custom-report-custom-reports-saved-definition')).toContainText('通道日报');
  await page.getByTestId('admin-custom-report-custom-reports-export').click();
  await expect(page.getByTestId('admin-custom-report-custom-reports-export-status')).toContainText('REQUESTED');
});
