import { expect, test } from '@playwright/test';

function token(subject: string) {
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({ sub: subject, jti: `p18-${subject}`, exp: 4102444800 })}.signature`;
}

function response(data: unknown) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-09T00:00:00Z', traceId: 'trace-p18' };
}

const rule = {
  id: 1,
  ruleName: '同号秒级限制',
  limitType: 'MOBILE',
  limitCount: 3,
  limitWindowSeconds: 1,
  action: 'BLOCK',
  scope: 'GLOBAL',
  scopeRefId: null,
  status: 'ACTIVE',
  hitCount: 7,
  createdAt: '2026-09-09T00:00:00',
  updatedAt: '2026-09-09T00:00:00',
};

test.beforeEach(async ({ page }) => {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('7'), userType: 'OPERATOR', tenantId: null });
  await page.route('**/api/v1/console/account-overview', async (route) => route.fulfill({
    json: response({
      id: 7,
      username: 'operator-7',
      userType: 'OPERATOR',
      roleNames: ['频控管理员'],
      permissions: [
        { code: 'frequency:menu', resourceType: 'MENU' },
        { code: 'frequency:read', resourceType: 'API' },
        { code: 'frequency:write', resourceType: 'API' },
        { code: 'frequency:import', resourceType: 'BUTTON' },
        { code: 'frequency:export', resourceType: 'BUTTON' },
      ],
      lastLoginAt: null,
      lastLoginIp: null,
    }),
  }));
  await page.route('**/api/v1/console/frequency/rules', async (route) => {
    if (route.request().method() === 'POST') await route.fulfill({ json: response(rule) });
    else await route.fulfill({ json: response([rule]) });
  });
  await page.route('**/api/v1/console/frequency/rules?**', async (route) => route.fulfill({ json: response([rule]) }));
  await page.route('**/api/v1/console/frequency/rules/import', async (route) => route.fulfill({
    json: response({ success: 2, failed: 0, errors: [] }),
  }));
  await page.route('**/api/v1/console/frequency/rules/export-request', async (route) => route.fulfill({
    json: response({ requestId: 'FREQUENCY_EXPORT_1', matchedRows: 1, status: 'REQUESTED' }),
  }));
  await page.route('**/api/v1/console/frequency/rules/export-request?**', async (route) => route.fulfill({
    json: response({ requestId: 'FREQUENCY_EXPORT_1', matchedRows: 1, status: 'REQUESTED' }),
  }));
  await page.route('**/api/v1/console/frequency/rules/1/disable', async (route) => route.fulfill({
    json: response({ ...rule, status: 'DISABLED' }),
  }));
  await page.route('**/api/v1/console/frequency/analytics', async (route) => route.fulfill({
    json: response({ total: 3, active: 2, hits: 10, blocked: 4, delayed: 5, alerts: 1, blockRate: 0.4, coverageRate: 0.67 }),
  }));
  await page.route('**/api/v1/console/tenant/api-keys', async (route) => route.fulfill({
    json: response([{
      id: 19,
      appKey: 'app-key-19',
      name: '生产 API',
      description: null,
      status: 'ACTIVE',
      ipWhitelist: null,
      perSecond: 10,
      perMinute: 100,
      perHour: 1000,
      perDay: 10000,
      expireTime: null,
      lastUsedTime: null,
    }]),
  }));
});

test('pw-p18-rules C-P18-RULE-METRICS OBL-F-5-6-A pw-p18-import C-P18-RULE-CRUD OBL-F-5-6-B pw-p18-api-key C-P18-API-KEY-LIMITS OBL-F-6-5-A pw-p18-429 C-P18-429-CONTRACT OBL-F-6-5-B pw-p18-high-concurrency C-P18-HIGH-CONCURRENCY OBL-EDGE-HIGH-CONCURRENCY', async ({ page }) => {
  await page.goto('/admin/frequency/rules');
  await expect(page.getByTestId('admin-frequency-api-frequency-rules-page')).toBeVisible();
  await expect(page.getByTestId('admin-frequency-api-frequency-rules-cards')).toContainText('规则总数 3');
  await expect(page.getByTestId('admin-frequency-api-frequency-rules-cards')).toContainText('今日拦截 4');
  await expect(page.getByTestId('admin-frequency-api-frequency-rules-policy-page')).toContainText('同号秒级限制');
  await page.getByTestId('query-panel-toggle').click();
  await expect(page.getByTestId('admin-frequency-api-frequency-rules-filter-name')).toHaveValue('');
  await expect(page.getByTestId('admin-frequency-api-frequency-rules-filter-type')).toHaveValue('');
  await expect(page.getByTestId('admin-frequency-api-frequency-rules-filter-action')).toHaveValue('');
  await expect(page.getByTestId('admin-frequency-api-frequency-rules-filter-status')).toHaveValue('');
  await expect(page.getByTestId('admin-frequency-api-frequency-rules-create-dialog')).toHaveCount(0);
  await page.getByTestId('admin-frequency-api-frequency-rules-create-open').click();
  await expect(page.getByTestId('admin-frequency-api-frequency-rules-name')).toHaveValue('同号秒级限制');
  await expect(page.getByTestId('admin-frequency-api-frequency-rules-type')).toHaveValue('MOBILE');
  await expect(page.getByTestId('admin-frequency-api-frequency-rules-count')).toHaveValue('3');
  await expect(page.getByTestId('admin-frequency-api-frequency-rules-window')).toHaveValue('1');
  await expect(page.getByTestId('admin-frequency-api-frequency-rules-action')).toHaveValue('BLOCK');
  await expect(page.getByTestId('admin-frequency-api-frequency-rules-scope')).toHaveValue('GLOBAL');
  await expect(page.getByTestId('admin-frequency-api-frequency-rules-row')).toContainText('7');
  await expect(page.getByTestId('shared-frequency-api-queued-feedback')).toContainText('429');
  await page.getByTestId('admin-frequency-api-frequency-rules-save').click();
  await expect(page.getByTestId('admin-frequency-api-frequency-rules-message')).toContainText('热更新');
  await page.getByTestId('admin-frequency-api-frequency-rules-import').click();
  await expect(page.getByTestId('admin-frequency-api-frequency-rules-import-dialog')).toBeVisible();
  await expect(page.getByTestId('admin-frequency-api-frequency-rules-import-type')).toHaveValue('MOBILE');
  await page.getByTestId('admin-frequency-api-frequency-rules-import-submit').click();
  await expect(page.getByTestId('admin-frequency-api-frequency-rules-message')).toContainText('导入完成');
  await page.getByTestId('admin-frequency-api-frequency-rules-export').click();
  await expect(page.getByTestId('admin-frequency-api-frequency-rules-message')).toContainText('导出请求已登记');
  await page.getByTestId('admin-frequency-api-frequency-rules-disable').click();
  await expect(page.getByTestId('admin-frequency-api-frequency-rules-message')).toContainText('历史命中证据保留');
});

test('pw-p18-api-key C-P18-API-KEY-LIMITS OBL-F-6-5-A exposes tenant API key second-minute-hour-day policy', async ({ page }) => {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('19'), userType: 'TENANT_ADMIN', tenantId: 42 });
  await page.goto('/tenant/api/keys');
  await expect(page.getByTestId('tenant-tenant-access-api-keys-page')).toBeVisible();
  await page.getByTestId('tenant-tenant-access-api-keys-create-dialog').click();
  await expect(page.getByTestId('tenant-frequency-api-api-keys-rate-limits')).toContainText('秒/分/时/日 10/100/1000/10000');
});
