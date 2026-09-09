import { expect, test } from '@playwright/test';

function token(subject: string) {
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({ sub: subject, jti: `p19-${subject}`, exp: 4102444800 })}.signature`;
}

function response(data: unknown) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-09T00:00:00Z', traceId: 'trace-p19' };
}

test.beforeEach(async ({ page }) => {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('7'), userType: 'OPERATOR', tenantId: null });
  await page.route('**/api/v1/console/account-overview', async (route) => route.fulfill({
    json: response({
      id: 7,
      username: 'operator-7',
      userType: 'OPERATOR',
      roleNames: ['号码管理员'],
      permissions: [
        { code: 'number-attribution:menu', resourceType: 'MENU' },
        { code: 'number-attribution:read', resourceType: 'API' },
        { code: 'number-attribution:import', resourceType: 'BUTTON' },
        { code: 'number-attribution:portability', resourceType: 'API' },
      ],
      lastLoginAt: null,
      lastLoginIp: null,
    }),
  }));
  await page.route('**/api/v1/console/number-attribution/prefixes/versions', async (route) => route.fulfill({
    json: response([{ id: 1, versionNo: 'V20260909', updateType: 'FULL', status: 'ACTIVE', sourceName: '官方号段', totalRows: 2, conflictCount: 0, actor: 'operator', createdAt: '2026-09-09T00:00:00', activatedAt: '2026-09-09T00:00:00' }]),
  }));
  await page.route('**/api/v1/console/number-attribution/portability', async (route) => {
    if (route.request().method() === 'POST') {
      await route.fulfill({ json: response({ id: 2, maskedMobile: '139****0001', originalCarrier: 'MOBILE', currentCarrier: 'TELECOM', portedAt: '2026-09-09', sourceName: '携转缓存', freshnessExpiresAt: '2026-09-10T00:00:00', status: 'ACTIVE', updatedAt: '2026-09-09T00:00:00' }) });
      return;
    }
    await route.fulfill({ json: response([{ id: 2, maskedMobile: '139****0001', originalCarrier: 'MOBILE', currentCarrier: 'TELECOM', portedAt: '2026-09-09', sourceName: '携转缓存', freshnessExpiresAt: '2026-09-10T00:00:00', status: 'ACTIVE', updatedAt: '2026-09-09T00:00:00' }]) });
  });
  await page.route('**/api/v1/console/number-attribution/prefixes/import', async (route) => route.fulfill({
    json: response({ versionNo: 'V20260909', success: 2, failed: 0, errors: [] }),
  }));
  await page.route('**/api/v1/console/number-attribution/lookup?**', async (route) => route.fulfill({
    json: response({ mobile: '13912345678', carrier: 'UNICOM', prefixCarrier: 'MOBILE', province: '广东', city: '深圳', source: 'PORTABILITY_CACHE', sourceName: '携转缓存', freshnessExpiresAt: '2026-09-10T00:00:00', providerFailure: false }),
  }));
});

test('pw-p19-attribution C-P19-ATTRIBUTION OBL-F-5-7-A pw-p19-portability C-P19-PORTABILITY OBL-F-5-7-B pw-p19-prefixes C-P19-PREFIX-IMPORT OBL-F-13-4-A', async ({ page }) => {
  await page.goto('/admin/number-attribution');
  await expect(page.getByTestId('admin-number-attribution-portability-attribution-page')).toBeVisible();
  await expect(page.getByTestId('admin-number-attribution-portability-prefixes-page')).toContainText('V20260909');
  await page.getByTestId('admin-prefixes-import').click();
  await expect(page.getByTestId('admin-number-attribution-message')).toContainText('已导入');
  await page.getByTestId('admin-number-attribution-lookup').click();
  await expect(page.getByTestId('admin-number-attribution-result')).toContainText('UNICOM');
  await expect(page.getByTestId('admin-number-attribution-fallback-source')).toContainText('PORTABILITY_CACHE');
  await page.getByTestId('admin-number-portability-save').click();
  await expect(page.getByTestId('admin-number-attribution-message')).toContainText('携号转网缓存已保存');

  await page.goto('/admin/number-portability');
  await expect(page.getByTestId('admin-number-attribution-portability-portability-page')).toBeVisible();
  await page.goto('/admin/prefixes');
  await expect(page.getByTestId('admin-number-attribution-portability-prefixes-page')).toBeVisible();
});
