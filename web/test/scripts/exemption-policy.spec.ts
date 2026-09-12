import { test, expect } from '@playwright/test';

function token(subject: string) {
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({ sub: subject, jti: `p14-${subject}`, exp: 4102444800 })}.signature`;
}

const policy = {
  id: 1401,
  tenantId: 42,
  exemptionType: 'SIGNATURE',
  resourceId: 'sig-1201',
  productCode: 'DOMESTIC_SMS',
  scopeExpression: 'LOGIN',
  approvalStatus: 'APPROVED',
  validFrom: '2026-09-01T00:00:00',
  validUntil: '2026-10-01T00:00:00',
  revoked: false,
  versionNo: 1,
  usageCount: 0,
  reason: '临时业务豁免',
  createdBy: 'operator-7',
  revokedBy: null,
  revokeReason: null,
  revokedAt: null,
  createdAt: '2026-09-09T00:00:00',
};

function response(data: unknown) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-09T00:00:00Z', traceId: 'trace-p14' };
}

test.beforeEach(async ({ page }) => {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('7'), userType: 'ADMIN', tenantId: null });
  await page.route('**/api/v1/console/exemptions', async (route) => {
    if (route.request().method() === 'POST') return route.fulfill({ json: response({ ...policy, id: 1402 }) });
    return route.fulfill({ json: response([policy]) });
  });
  await page.route('**/api/v1/console/exemptions/effective-preview', async (route) => route.fulfill({
    json: response({ result: 'ACTIVE', exemptionRuleId: 1401, versionNo: 1, reason: '匹配已批准且有效的豁免', subjectType: 'SIGNATURE', subjectId: 'sig-1201', productCode: 'DOMESTIC_SMS', scopeExpression: 'LOGIN' }),
  }));
  await page.route('**/api/v1/console/exemptions/usage-history', async (route) => route.fulfill({
    json: response([{ id: 1, exemptionRuleId: 1401, versionNo: 1, tenantId: 42, subjectType: 'SIGNATURE', subjectId: 'sig-1201', productCode: 'DOMESTIC_SMS', scopeExpression: 'LOGIN', controlCode: 'SIGNATURE_REVIEW', actor: 'operator-7', reason: '范围验证', result: 'ACTIVE', createdAt: '2026-09-09T00:00:00' }]),
  }));
  await page.route('**/api/v1/console/exemptions/1401/revoke', async (route) => route.fulfill({ json: response({ ...policy, revoked: true }) }));
});

test('pw-p14-exemption-config C-P14-EXEMPTION-CONFIG OBL-F-3-6-A', async ({ page }) => {
  await page.goto('/admin/exemption/policy');
  await expect(page.getByTestId('admin-auditable-exemption-exemption-policy-page')).toBeVisible();
  await expect(page.getByTestId('admin-auditable-exemption-exemption-policy-create-dialog')).toHaveCount(0);
  await page.getByTestId('admin-auditable-exemption-exemption-policy-create-open').click();
  await page.getByTestId('admin-auditable-exemption-exemption-policy-type').selectOption('CONTENT');
  await page.getByTestId('admin-auditable-exemption-exemption-policy-resource').fill('tpl-1');
  await page.getByTestId('admin-auditable-exemption-exemption-policy-save').click();
  await expect(page.getByText('豁免策略已保存。')).toBeVisible();
});

test('pw-p14-exemption-preview C-P14-EXEMPTION-PREVIEW OBL-F-3-6-B', async ({ page }) => {
  await page.goto('/admin/exemption/policy');
  await expect(page.getByTestId('admin-auditable-exemption-exemption-effective-preview')).toBeVisible();
  await page.getByTestId('admin-auditable-exemption-exemption-preview-run').click();
  await expect(page.getByTestId('admin-auditable-exemption-exemption-preview-result')).toContainText('ACTIVE');
});

test('pw-p14-exemption-usage-history C-P14-EXEMPTION-USAGE-HISTORY OBL-F-3-6-C', async ({ page }) => {
  await page.goto('/admin/exemption/policy');
  await expect(page.getByTestId('admin-auditable-exemption-exemption-usage-history')).toBeVisible();
  await expect(page.getByTestId('admin-auditable-exemption-exemption-usage-history')).toContainText('SIGNATURE_REVIEW');
});
