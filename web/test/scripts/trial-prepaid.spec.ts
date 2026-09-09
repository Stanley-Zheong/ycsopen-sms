import { expect, test } from '@playwright/test';

function token(subject: string) {
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({ sub: subject, jti: `p22-${subject}`, exp: 4102444800 })}.signature`;
}

function response(data: unknown) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-09T00:00:00Z', traceId: 'trace-p22' };
}

const permissions = [
  { code: 'trial-prepaid:menu', resourceType: 'MENU' },
  { code: 'trial-prepaid:read', resourceType: 'API' },
  { code: 'trial-prepaid:write', resourceType: 'API' },
  { code: 'tenant:read', resourceType: 'API' },
];

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/console/account-overview', async (route) => {
    const admin = route.request().headers().authorization?.includes('admin');
    await route.fulfill({
      json: response({
        id: admin ? 22 : 42,
        username: admin ? 'operator-22' : 'tenant-22',
        userType: admin ? 'OPERATOR' : 'TENANT_ADMIN',
        roleNames: admin ? ['试用预付费管理员'] : ['机构管理员'],
        permissions,
        lastLoginAt: null,
        lastLoginIp: null,
      }),
    });
  });
  await page.route('**/api/v1/console/trial-prepaid/tenants/42/overview', async (route) => route.fulfill({
    json: response({ tenantId: 42, trialStatus: 'TRIAL_FROZEN', quotaTotal: 500, quotaRemaining: 0, validFrom: '2026-09-09T00:00:00', validUntil: '2026-09-23T00:00:00', version: 3 }),
  }));
  await page.route('**/api/v1/console/trial-prepaid/tenants/42/trial', async (route) => route.fulfill({
    json: response({ tenantId: 42, trialStatus: 'TRIAL', quotaTotal: 500, quotaRemaining: 500, validFrom: '2026-09-09T00:00:00', validUntil: '2026-09-23T00:00:00', version: 1 }),
  }));
  await page.route('**/api/v1/console/trial-prepaid/tenants/42/trial/consume', async (route) => route.fulfill({
    json: response({ tenantId: 42, trialStatus: 'TRIAL_FROZEN', quotaTotal: 500, quotaRemaining: 0, validFrom: '2026-09-09T00:00:00', validUntil: '2026-09-23T00:00:00', version: 4 }),
  }));
  await page.route('**/api/v1/console/trial-prepaid/tenants/42/conversion-request', async (route) => route.fulfill({
    json: response({ id: 1, tenantId: 42, trialStatus: 'TRIAL_FROZEN', status: 'REQUESTED' }),
  }));
  await page.route('**/api/v1/console/trial-prepaid/consumption?**', async (route) => route.fulfill({
    json: response([{ tenantId: 42, messageRef: 'MSG-22', businessType: 'SMS', quotaDelta: -1, amountMil: 0, entryType: 'TRIAL_CONSUME', state: 'CONFIRMED', actor: 'tenant', createdAt: '2026-09-09T01:00:00' }]),
  }));
  await page.route('**/api/v1/console/trial-prepaid/balance-audits?**', async (route) => route.fulfill({
    json: response([{ tenantId: 42, businessDocId: 'DOC-22', mutationType: 'RESERVE', amountMil: 200, beforeBalanceMil: 1000, afterBalanceMil: 1000, beforeFrozenMil: 0, afterFrozenMil: 200, accountVersion: 1, actor: 'operator', createdAt: '2026-09-09T01:00:00' }]),
  }));
  await page.route('**/api/v1/console/tenant/qualification', async (route) => route.fulfill({
    json: response({
      tenantId: 42,
      tenantNo: 'T-42',
      shortName: '测试机构',
      verificationStatus: 'VERIFIED',
      verifiedAt: '2026-09-09T00:00:00',
      verificationUpdatedAt: '2026-09-09T00:00:00',
      reason: null,
      revision: 1,
      identity: null,
      contacts: [],
      documents: [],
      admins: [],
      apiKeys: [],
      callbacks: [],
    }),
  }));
});

test('pw-p22-tenant-status C-P22-TENANT-TRIAL-STATUS OBL-F-2-8-B pw-p22-conversion C-P22-CONVERSION OBL-F-2-8-D pw-p22-freeze-state C-P22-TRIAL-FREEZE OBL-STATE-TENANT-TRIAL-FREEZE pw-p22-trial-flow C-P22-TRIAL-FLOW OBL-FLOW-12-1-TRIAL', async ({ page }) => {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('tenant'), userType: 'TENANT_ADMIN', tenantId: 42 });
  await page.goto('/tenant/overview');
  await expect(page.getByTestId('tenant-trial-prepaid-overview-page')).toBeVisible();
  await expect(page.getByTestId('tenant-trial-prepaid-overview-trial-status')).toContainText('TRIAL_FROZEN');
  await page.getByTestId('tenant-trial-prepaid-overview-consume-trial').click();
  await expect(page.getByTestId('tenant-trial-prepaid-overview-message')).toContainText('剩余额度 0');
  await page.getByTestId('tenant-trial-prepaid-overview-conversion-request').click();
  await expect(page.getByTestId('tenant-trial-prepaid-overview-message')).toContainText('REQUESTED');
});

test('pw-p22-consumption-page C-P22-CONSUMPTION-LEDGER OBL-F-8-4-A pw-p22-consumption-filter C-P22-CONSUMPTION-FILTER OBL-F-8-4-B', async ({ page }) => {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('tenant'), userType: 'TENANT_ADMIN', tenantId: 42 });
  await page.goto('/tenant/consumption-ledger');
  await expect(page.getByTestId('tenant-trial-prepaid-consumption-ledger-page')).toBeVisible();
  await expect(page.getByTestId('tenant-consumption-ledger')).toBeVisible();
  await expect(page.getByTestId('tenant-trial-prepaid-consumption-ledger-filters')).toBeVisible();
  await page.getByTestId('tenant-trial-prepaid-consumption-ledger-business-type').selectOption('SMS');
  await page.getByTestId('tenant-trial-prepaid-consumption-ledger-refresh').click();
  await expect(page.getByTestId('tenant-trial-prepaid-consumption-ledger-row')).toContainText('MSG-22');
});

test('pw-p22-trial-config C-P22-TRIAL-CONFIG OBL-F-2-8-A pw-p22-quota-field C-P22-QUOTA-FIELD OBL-FIELD-TENANT-TRIAL-QUOTA pw-p22-validity-field C-P22-VALIDITY-FIELD OBL-FIELD-TENANT-TRIAL-VALIDITY pw-p22-balance-audit C-P22-BALANCE-AUDIT OBL-F-8-9-A', async ({ page }) => {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('admin'), userType: 'OPERATOR', tenantId: null });
  await page.goto('/admin/tenant-trial-contracts');
  await expect(page.getByTestId('admin-trial-prepaid-tenant-trial-quota')).toHaveValue('500');
  await expect(page.getByTestId('admin-trial-prepaid-tenant-trial-validity')).toBeVisible();
  await page.getByTestId('admin-trial-prepaid-activate-trial').click();
  await expect(page.getByTestId('admin-trial-prepaid-message')).toContainText('试用已启用');
  await page.goto('/admin/balance-audit');
  await expect(page.getByTestId('admin-balance-audit')).toBeVisible();
  await expect(page.getByTestId('admin-trial-prepaid-balance-audit-table')).toBeVisible();
  await expect(page.getByTestId('admin-trial-prepaid-balance-audit-row')).toContainText('DOC-22');
});

test('pw-p22-qualification-state C-P22-QUALIFICATION-TRIAL OBL-STATE-TENANT-TRIAL', async ({ page }) => {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('tenant'), userType: 'TENANT_ADMIN', tenantId: 42 });
  await page.goto('/tenant/qualification');
  await expect(page.getByTestId('tenant-tenant-qualification-qualification-status')).toContainText('认证通过');
});
