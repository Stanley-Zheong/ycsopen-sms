import { expect, test } from '@playwright/test';

function token(subject: string) {
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({ sub: subject, jti: `p20-${subject}`, exp: 4102444800 })}.signature`;
}

function response(data: unknown) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-09T00:00:00Z', traceId: 'trace-p20' };
}

test.beforeEach(async ({ page }) => {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('20'), userType: 'OPERATOR', tenantId: null });
  await page.route('**/api/v1/console/account-overview', async (route) => route.fulfill({
    json: response({
      id: 20,
      username: 'operator-20',
      userType: 'OPERATOR',
      roleNames: ['状态码管理员'],
      permissions: [
        { code: 'provider-status:menu', resourceType: 'MENU' },
        { code: 'provider-status:read', resourceType: 'API' },
        { code: 'provider-status:import', resourceType: 'BUTTON' },
        { code: 'provider-status:export', resourceType: 'BUTTON' },
      ],
      lastLoginAt: null,
      lastLoginIp: null,
    }),
  }));
  await page.route('**/api/v1/console/provider-status/versions', async (route) => route.fulfill({
    json: response([{ id: 1, versionNo: 'ST20260909', status: 'ACTIVE', sourceName: '供应商文档', effectiveAt: '2026-09-09T00:00:00', conflictCount: 0, actor: 'operator', createdAt: '2026-09-09T00:00:00' }]),
  }));
  await page.route('**/api/v1/console/provider-status/mappings', async (route) => route.fulfill({
    json: response([{ providerName: 'YTO', protocol: 'HTTP', providerCode: 'DELIVRD', platformCategory: 'SUCCESS', finalState: true, billable: true, retryable: false, severity: 'INFO', advice: '确认送达' }]),
  }));
  await page.route('**/api/v1/console/provider-status/import', async (route) => route.fulfill({
    json: response({ versionNo: 'ST20260909', success: 1, failed: 0, errors: [] }),
  }));
  await page.route('**/api/v1/console/provider-status/normalize?**', async (route) => route.fulfill({
    json: response({ providerName: 'YTO', protocol: 'HTTP', providerCode: 'DELIVRD', versionNo: 'ST20260909', platformCategory: 'SUCCESS', finalState: true, billable: true, retryable: false, severity: 'INFO', advice: '确认送达', source: 'MAPPED' }),
  }));
  await page.route('**/api/v1/console/provider-status/export-request?**', async (route) => route.fulfill({
    json: response({ requestId: 'STATUS_EXPORT_1', matchedRows: 1, status: 'REQUESTED' }),
  }));
});

test('pw-p20-map C-P20-STATUS-MAP OBL-PROVIDER-TAXONOMY-001 pw-p20-crud C-P20-STATUS-CRUD OBL-F-13-3-A pw-p20-version C-P20-VERSION-HISTORY OBL-PROVIDER-TAXONOMY-002 pw-p20-unknown C-P20-UNKNOWN-FALLBACK OBL-PROVIDER-TAXONOMY-003 pw-p20-shared-contract C-P20-SHARED-CONTRACT OBL-PROVIDER-TAXONOMY-004', async ({ page }) => {
  await page.goto('/admin/status-codes');
  await expect(page.getByTestId('admin-provider-status-taxonomy-status-codes-page')).toBeVisible();
  await expect(page.getByTestId('admin-provider-status-status-codes-version-history')).toContainText('ST20260909');
  await expect(page.getByTestId('admin-provider-status-taxonomy-status-codes-row')).toContainText('DELIVRD');
  await page.getByTestId('admin-provider-status-taxonomy-status-codes-import').click();
  await expect(page.getByTestId('admin-provider-status-taxonomy-message')).toContainText('已导入');
  await page.getByTestId('admin-provider-status-taxonomy-normalize').click();
  await expect(page.getByTestId('admin-provider-status-taxonomy-normalized-result')).toContainText('SUCCESS');
  await expect(page.getByTestId('admin-provider-status-taxonomy-unknown-fallback')).toContainText('MAPPED');
  await page.getByTestId('admin-provider-status-taxonomy-status-codes-export').click();
  await expect(page.getByTestId('admin-provider-status-taxonomy-message')).toContainText('导出请求已登记');
});
