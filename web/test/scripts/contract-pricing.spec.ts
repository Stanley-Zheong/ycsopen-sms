import { expect, test, type Route } from '@playwright/test';

function token(subject: string) {
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({ sub: subject, jti: `p37-${subject}`, exp: 4102444800 })}.signature`;
}

function response(data: unknown) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-10T00:00:00Z', traceId: 'trace-p37' };
}

const permissions = [
  { code: 'trial-prepaid:menu', resourceType: 'MENU' },
  { code: 'trial-prepaid:read', resourceType: 'API' },
  { code: 'trial-prepaid:write', resourceType: 'API' },
];

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/console/account-overview', async (route) => {
    const admin = route.request().headers().authorization?.includes('operator');
    await route.fulfill({
      json: response({
        id: admin ? 37 : 42,
        username: admin ? 'operator-37' : 'tenant-37',
        userType: admin ? 'OPERATOR' : 'TENANT_ADMIN',
        roleNames: admin ? ['运营'] : ['机构管理员'],
        permissions,
        lastLoginAt: null,
        lastLoginIp: null,
      }),
    });
  });
  await page.route('**/api/v1/console/trial-prepaid/tenants/42/overview', async (route) => route.fulfill({
    json: response({ tenantId: 42, trialStatus: 'TRIAL_FROZEN', quotaTotal: 500, quotaRemaining: 0, validFrom: '2026-09-09T00:00:00', validUntil: '2026-09-23T00:00:00', version: 3 }),
  }));
  await page.route('**/api/v1/console/trial-prepaid/balance-audits?**', async (route) => route.fulfill({
    json: response([]),
  }));
  await page.route('**/api/v1/console/contracts/tenants/42', async (route: Route) => {
    if (route.request().method() === 'POST') {
      await route.fulfill({ json: response({
        tenantId: 42,
        billingMode: 'POSTPAID',
        priceBookVersion: 'SMS_STANDARD_V1',
        contractNo: 'HT-2026-0001',
        signedAt: '2026-09-10',
        attachmentRef: 'oss://contracts/HT-2026-0001.pdf',
        creditLimitMil: 1000000,
        billingPeriod: 'MONTHLY',
        contractStatus: 'ACTIVE',
        approvedBy: 'operator',
      }) });
    }
  });
  await page.route('**/api/v1/console/contracts/tenants/42/overview', async (route) => route.fulfill({
    json: response({
      tenantId: 42,
      tenantState: 'CONTRACTED',
      billingMode: 'POSTPAID',
      priceBookVersion: 'SMS_STANDARD_V1',
      contractNo: 'HT-2026-0001',
      signedAt: '2026-09-10',
      attachmentRef: 'oss://contracts/HT-2026-0001.pdf',
      creditLimitMil: 1000000,
      billingPeriod: 'MONTHLY',
      contractStatus: 'ACTIVE',
      approvedBy: 'operator',
    }),
  }));
});

test('pw-p37-admin-contract C-P37-ADMIN-CONTRACT OBL-F-2-9-A pw-p37-billing-mode C-P37-BILLING-MODE OBL-F-2-5-A pw-p37-flow-contract C-P37-FLOW-CONTRACT OBL-FLOW-12-1-CONTRACT', async ({ page }) => {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('operator'), userType: 'OPERATOR', tenantId: null });
  await page.goto('/admin/tenant-trial-contracts');
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-page')).toBeVisible();
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-billing-mode')).toHaveValue('POSTPAID');
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-price-version')).toHaveValue('SMS_STANDARD_V1');
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-number')).toHaveValue('HT-2026-0001');
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-signed-date')).toHaveValue('2026-09-10');
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-attachment')).toHaveValue('oss://contracts/HT-2026-0001.pdf');
  await page.getByTestId('admin-contract-pricing-tenant-contract-approve').click();
  await expect(page.getByTestId('admin-trial-prepaid-message')).toContainText('POSTPAID');
});

test('pw-p37-credit-period C-P37-CREDIT-PERIOD OBL-F-2-5-B pw-p37-postpaid-fields C-P37-POSTPAID-FIELDS OBL-F-2-9-B', async ({ page }) => {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('operator'), userType: 'OPERATOR', tenantId: null });
  await page.goto('/admin/tenant-trial-contracts');
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-postpaid-fields')).toBeVisible();
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-credit-period')).toContainText('账期');
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-credit-limit')).toHaveValue('1000000');
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-billing-period')).toHaveValue('MONTHLY');
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
