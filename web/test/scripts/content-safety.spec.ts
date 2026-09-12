import { test, expect } from '@playwright/test';

function token(subject: string) {
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({ sub: subject, jti: `p17-${subject}`, exp: 4102444800 })}.signature`;
}

function response(data: unknown) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-09T00:00:00Z', traceId: 'trace-p17' };
}

const policy = {
  id: 1,
  word: '营销',
  category: 'MARKETING',
  level: 'HIGH',
  replacement: '通知',
  action: 'REPLACE',
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
      roleNames: ['内容审核员'],
      permissions: [
        { code: 'content-safety:menu', resourceType: 'MENU' },
        { code: 'content-safety:read', resourceType: 'API' },
        { code: 'content-safety:write', resourceType: 'API' },
        { code: 'content-safety:import', resourceType: 'BUTTON' },
        { code: 'content-safety:export', resourceType: 'BUTTON' },
        { code: 'content-safety:scan', resourceType: 'API' },
      ],
      lastLoginAt: null,
      lastLoginIp: null,
    }),
  }));
  await page.route('**/api/v1/console/content-safety/policies', async (route) => {
    if (route.request().method() === 'POST') await route.fulfill({ json: response(policy) });
    else await route.fulfill({ json: response([policy]) });
  });
  await page.route('**/api/v1/console/content-safety/policies?**', async (route) => route.fulfill({ json: response([policy]) }));
  await page.route('**/api/v1/console/content-safety/policies/import', async (route) => route.fulfill({
    json: response({ success: 2, failed: 0, errors: [] }),
  }));
  await page.route('**/api/v1/console/content-safety/policies/export-request', async (route) => route.fulfill({
    json: response({ requestId: 'CONTENT_EXPORT_1', matchedRows: 1, status: 'REQUESTED' }),
  }));
  await page.route('**/api/v1/console/content-safety/policies/export-request?**', async (route) => route.fulfill({
    json: response({ requestId: 'CONTENT_EXPORT_1', matchedRows: 1, status: 'REQUESTED' }),
  }));
  await page.route('**/api/v1/console/content-safety/policies/1/delete', async (route) => route.fulfill({
    json: response({ ...policy, status: 'DISABLED' }),
  }));
  await page.route('**/api/v1/console/content-safety/analytics', async (route) => route.fulfill({
    json: response({ total: 3, active: 2, hits: 10, intercepts: 4, replacements: 5, alerts: 1, interceptRate: 0.4, coverageRate: 0.67 }),
  }));
  await page.route('**/api/v1/console/content-safety/scan', async (route) => route.fulfill({
    json: response({ blocked: false, reason: null, finalContent: '【签名】变量填入abc，高危通知' }),
  }));
});

test('pw-p17-policy C-P17-POLICY-METRICS OBL-F-5-5-A pw-p17-import C-P17-POLICY-CRUD OBL-F-5-5-B pw-p17-scan C-P17-FINAL-CONTENT OBL-F-5-5-C pw-p17-action C-P17-ACTION-PRECEDENCE OBL-F-5-5-D', async ({ page }) => {
  await page.goto('/admin/content-safety');
  await expect(page.getByTestId('admin-runtime-content-content-safety-page')).toBeVisible();
  await expect(page.getByTestId('admin-runtime-content-content-safety-cards')).toContainText('词库总数 3');
  await expect(page.getByTestId('admin-runtime-content-content-safety-cards')).toContainText('今日拦截 4');
  await expect(page.getByTestId('admin-runtime-content-content-safety-policy-page')).toContainText('营销');
  await expect(page.getByTestId('admin-runtime-content-content-safety-filter-word')).toHaveValue('');
  await expect(page.getByTestId('admin-runtime-content-content-safety-filter-category')).toHaveValue('');
  await expect(page.getByTestId('admin-runtime-content-content-safety-filter-level')).toHaveValue('');
  await expect(page.getByTestId('admin-runtime-content-content-safety-filter-action')).toHaveValue('');
  await expect(page.getByTestId('admin-runtime-content-content-safety-filter-status')).toHaveValue('ACTIVE');
  await expect(page.getByTestId('admin-runtime-content-content-safety-create-dialog')).toHaveCount(0);
  await page.getByTestId('admin-runtime-content-content-safety-create-open').click();
  await expect(page.getByTestId('admin-runtime-content-content-safety-word')).toHaveValue('营销');
  await expect(page.getByTestId('admin-runtime-content-content-safety-category')).toHaveValue('MARKETING');
  await expect(page.getByTestId('admin-runtime-content-content-safety-level')).toHaveValue('HIGH');
  await expect(page.getByTestId('admin-runtime-content-content-safety-replacement')).toHaveValue('通知');
  await expect(page.getByTestId('admin-runtime-content-content-safety-action')).toHaveValue('REPLACE');
  await expect(page.getByTestId('admin-runtime-content-content-safety-scope')).toHaveValue('GLOBAL');
  await expect(page.getByTestId('admin-runtime-content-content-safety-row')).toContainText('7');
  await page.getByTestId('admin-runtime-content-content-safety-save').click();
  await expect(page.getByTestId('admin-runtime-content-content-safety-message')).toContainText('热更新');
  await page.getByTestId('admin-runtime-content-content-safety-import').click();
  await expect(page.getByTestId('admin-runtime-content-content-safety-import-dialog')).toBeVisible();
  await expect(page.getByTestId('admin-runtime-content-content-safety-import-category')).toHaveValue('MARKETING');
  await page.getByTestId('admin-runtime-content-content-safety-import-submit').click();
  await expect(page.getByTestId('admin-runtime-content-content-safety-message')).toContainText('词库导入完成');
  await page.getByTestId('admin-runtime-content-content-safety-export').click();
  await expect(page.getByTestId('admin-runtime-content-content-safety-message')).toContainText('导出请求已登记');
  await page.getByTestId('admin-runtime-content-content-safety-delete').click();
  await expect(page.getByTestId('admin-runtime-content-content-safety-message')).toContainText('历史命中证据保留');
  await expect(page.getByTestId('admin-runtime-content-content-safety-scan-content')).toHaveValue('【签名】变量填入ＡＢＣ，高危营销');
  await page.getByTestId('admin-runtime-content-content-safety-scan').click();
  await expect(page.getByTestId('admin-runtime-content-content-safety-scan-result')).toContainText('高危通知');
});
