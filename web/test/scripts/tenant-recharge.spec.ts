import { expect, test, type Route } from '@playwright/test';

function token(subject: string) {
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({ sub: subject, jti: `p36-${subject}`, exp: 4102444800 })}.signature`;
}

function response(data: unknown) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-10T00:00:00Z', traceId: 'trace-p36' };
}

const permissions = [
  { code: 'trial-prepaid:menu', resourceType: 'MENU' },
  { code: 'trial-prepaid:read', resourceType: 'API' },
  { code: 'trial-prepaid:write', resourceType: 'API' },
];

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/console/account-overview', async (route) => {
    const admin = route.request().headers().authorization?.includes('finance');
    await route.fulfill({
      json: response({
        id: admin ? 36 : 42,
        username: admin ? 'finance-36' : 'tenant-36',
        userType: admin ? 'FINANCE' : 'TENANT_ADMIN',
        roleNames: admin ? ['财务'] : ['机构管理员'],
        permissions,
        lastLoginAt: null,
        lastLoginIp: null,
      }),
    });
  });
  await page.route('**/api/v1/console/recharges/tenant/42', async (route: Route) => {
    if (route.request().method() === 'POST') {
      await route.fulfill({ json: response({
        id: 2,
        tenantId: 42,
        amountMil: 200000,
        rechargeMethod: 'ALIPAY',
        transactionRefMask: 'ALI-****0002',
        evidenceText: '支付宝凭证',
        status: 'PENDING',
        submitterActor: 'tenant-user',
        reviewerActor: null,
        reviewReason: null,
        reviewedAt: null,
        createdAt: '2026-09-10T01:00:00',
      }) });
      return;
    }
    await route.fulfill({ json: response([{
      id: 1,
      tenantId: 42,
      amountMil: 100000,
      rechargeMethod: 'BANK_TRANSFER',
      transactionRefMask: 'BANK****0001',
      evidenceText: '银行回单',
      status: 'PENDING',
      submitterActor: 'tenant-user',
      reviewerActor: null,
      reviewReason: null,
      reviewedAt: null,
      createdAt: '2026-09-10T00:00:00',
    }]) });
  });
  await page.route('**/api/v1/console/recharges/reviews?**', async (route) => route.fulfill({ json: response([{
    id: 3,
    tenantId: 42,
    amountMil: 300000,
    rechargeMethod: 'WECHAT',
    transactionRefMask: 'WX-R****0003',
    evidenceText: '微信凭证',
    status: 'PENDING',
    submitterActor: 'tenant-user',
    reviewerActor: null,
    reviewReason: null,
    reviewedAt: null,
    createdAt: '2026-09-10T02:00:00',
  }]) }));
  await page.route('**/api/v1/console/recharges/3/review', async (route) => route.fulfill({ json: response({
    id: 3,
    tenantId: 42,
    amountMil: 300000,
    rechargeMethod: 'WECHAT',
    transactionRefMask: 'WX-R****0003',
    evidenceText: '微信凭证',
    status: 'APPROVED',
    submitterActor: 'tenant-user',
    reviewerActor: 'finance',
    reviewReason: '到账一致',
    reviewedAt: '2026-09-10T03:00:00',
    createdAt: '2026-09-10T02:00:00',
  }) }));
});

test('pw-p36-tenant-recharge C-P36-TENANT-RECHARGE OBL-F-8-3-A', async ({ page }) => {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('tenant'), userType: 'TENANT_ADMIN', tenantId: 42 });
  await page.goto('/tenant/recharge');
  await expect(page.getByTestId('tenant-recharge-operations-recharge-form')).toBeVisible();
  await page.getByTestId('tenant-recharge-operations-recharge-amount').fill('200000');
  await page.getByTestId('tenant-recharge-operations-recharge-method').selectOption('ALIPAY');
  await page.getByTestId('tenant-recharge-operations-recharge-transaction').fill('ALI-RECHARGE-0002');
  await page.getByTestId('tenant-recharge-operations-recharge-evidence').fill('支付宝凭证');
  await page.getByTestId('tenant-recharge-operations-recharge-submit').click();
  await expect(page.getByTestId('tenant-recharge-operations-recharge-message')).toContainText('PENDING');
  await expect(page.getByTestId('tenant-recharge-operations-recharge-state')).toContainText('PENDING');
});

test('pw-p36-finance-review C-P36-FINANCE-REVIEW OBL-F-8-3-B', async ({ page }) => {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('finance'), userType: 'FINANCE', tenantId: null });
  await page.goto('/admin/tenant-recharge-review');
  await expect(page.getByTestId('admin-tenant-recharge-operations-review-table')).toBeVisible();
  await expect(page.getByTestId('admin-tenant-recharge-operations-review-row')).toContainText('WX-R****0003');
  await page.getByTestId('admin-tenant-recharge-operations-review-reason').fill('到账一致');
  await expect(page.getByTestId('admin-tenant-recharge-operations-review-reject')).toBeVisible();
  await page.getByTestId('admin-tenant-recharge-operations-review-approve').click();
  await expect(page.getByTestId('admin-tenant-recharge-operations-review-message')).toContainText('APPROVED');
});
