import { expect, test } from '@playwright/test';

function token(subject: string) {
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({ sub: subject, jti: `p40-${subject}`, exp: 4102444800 })}.signature`;
}

function response(data: unknown) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-10T00:00:00Z', traceId: 'trace-p40' };
}

const permissions = [
  { code: 'trial-prepaid:menu', resourceType: 'MENU' },
  { code: 'trial-prepaid:read', resourceType: 'API' },
  { code: 'trial-prepaid:write', resourceType: 'API' },
  { code: 'fee-warning:menu', resourceType: 'MENU' },
  { code: 'fee-warning:read', resourceType: 'API' },
  { code: 'fee-warning:write', resourceType: 'API' },
];

const rule = {
  id: 9,
  ruleName: '授信比例预警',
  tenantId: 42,
  metricType: 'POSTPAID_CREDIT_RATIO',
  thresholdValue: 0.85,
  action: 'MANUAL_APPROVAL',
  notifyChannels: '["SMS","EMAIL"]',
  notificationTargets: '["tenant:42","finance","operations"]',
  status: 'ACTIVE',
  updatedAt: '2026-09-10T12:00:00',
};

const episode = {
  id: 501,
  tenantId: 42,
  ruleId: 9,
  alertRecordId: 3001,
  metricType: 'POSTPAID_CREDIT_RATIO',
  sourceKey: 'fee-warning:42:9:POSTPAID_CREDIT_RATIO',
  sourceAmountMil: 950,
  creditLimitMil: 1000,
  usedAmountMil: 900,
  thresholdValue: 0.85,
  ratio: 0.95,
  action: 'MANUAL_APPROVAL',
  status: 'ACTIVE',
  approvalState: 'PENDING',
  deliveryState: 'DELIVERED',
  sourceSnapshot: 'usedMil=900,estimatedAmountMil=50,creditLimitMil=1000,ratio=0.9500',
  actor: 'finance',
  resolutionNote: null,
  createdAt: '2026-09-10T12:00:00',
  updatedAt: '2026-09-10T12:00:00',
};

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/console/account-overview', async (route) => {
    await route.fulfill({
      json: response({
        id: 40,
        username: 'finance-40',
        userType: 'FINANCE',
        roleNames: ['财务'],
        permissions,
        lastLoginAt: null,
        lastLoginIp: null,
      }),
    });
  });
  await page.route('**/api/v1/console/fee-warnings/rules**', async (route) => {
    if (route.request().method() === 'POST') {
      await route.fulfill({ json: response({ ...rule, id: 10 }) });
      return;
    }
    await route.fulfill({ json: response([rule]) });
  });
  await page.route('**/api/v1/console/fee-warnings/evaluate', async (route) => {
    await route.fulfill({ json: response([episode]) });
  });
  await page.route('**/api/v1/console/fee-warnings/episodes**', async (route) => {
    if (route.request().method() === 'POST') {
      await route.fulfill({ json: response({ ...episode, status: 'APPROVED', approvalState: 'APPROVED' }) });
      return;
    }
    await route.fulfill({ json: response([episode]) });
  });
  await page.route('**/api/v1/tenant/fee-warnings/episodes', async (route) => {
    await route.fulfill({ json: response([{ ...episode, metricType: 'PREPAID_AMOUNT', sourceAmountMil: 1000 }]) });
  });
});

test('pw-p40-threshold-rules C-P40-FEE-WARNING-THRESHOLDS OBL-F-8-10-A pw-p40-notification-targets C-P40-FEE-WARNING-TARGETS OBL-F-8-10-B pw-p40-lifecycle-fee-warning C-P40-LIFECYCLE-FEE-WARNING OBL-FLOW-12-1-FEE-WARNING', async ({ page }) => {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('finance'), userType: 'FINANCE', tenantId: null });
  await page.goto('/admin/fee/warning');
  await expect(page.getByTestId('admin-fee-warning-credit-page')).toBeVisible();
  await expect(page.getByTestId('admin-fee-warning-fee-warning-targets')).toHaveValue('["tenant:42","finance","operations"]');
  await expect(page.getByTestId('admin-fee-warning-fee-warning-notification-targets')).toBeVisible();
  await expect(page.getByTestId('admin-fee-warning-fee-warning-credit-action')).toContainText('MANUAL_APPROVAL');
  await page.getByTestId('admin-fee-warning-fee-warning-rule-save').click();
  await expect(page.getByTestId('admin-fee-warning-message')).toContainText('已保存');
});

test('pw-p40-credit-action C-P40-CREDIT-ACTION OBL-F-8-2-B pw-p40-ingress-enforcement C-P40-INGRESS-ENFORCEMENT OBL-F-8-10-C pw-p40-finance-supervision C-P40-FINANCE-SUPERVISION OBL-FLOW-12-2-FINANCE', async ({ page }) => {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('finance'), userType: 'FINANCE', tenantId: null });
  await page.goto('/admin/fee/warning');
  await expect(page.getByTestId('admin-fee-warning-fee-warning-credit-action')).toContainText('MANUAL_APPROVAL');
  await page.getByTestId('admin-fee-warning-fee-warning-evaluate').click();
  await expect(page.getByTestId('admin-fee-warning-message')).toContainText('评估完成');
  await expect(page.getByTestId('admin-fee-warning-fee-warning-enforcement-action')).toContainText('PENDING');
  await expect(page.getByTestId('admin-fee-warning-source-snapshot')).toContainText('creditLimitMil');
  await page.getByTestId('admin-fee-warning-fee-warning-approve').click();
  await expect(page.getByTestId('admin-fee-warning-message')).toContainText('审批已通过');
});

test('pw-p40-tenant-low-balance C-P40-TENANT-FEE-WARNING OBL-F-8-1-C', async ({ page }) => {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('40'), userType: 'TENANT_ADMIN', tenantId: 42 });
  await page.goto('/tenant/balance');
  await expect(page.getByTestId('tenant-fee-warning-overview-low-balance-warning')).toContainText('PREPAID_AMOUNT');
  await expect(page.getByTestId('tenant-fee-warning-overview-low-balance-warning')).toContainText('1000');
  await expect(page.getByTestId('tenant-fee-warning-overview-delivery-evidence')).toContainText('DELIVERED');
});
