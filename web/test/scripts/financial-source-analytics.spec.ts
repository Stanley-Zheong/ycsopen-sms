import { expect, test } from '@playwright/test';

function token(subject: string) {
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({ sub: subject, jti: `p39-${subject}`, exp: 4102444800 })}.signature`;
}

function response(data: unknown) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-10T00:00:00Z', traceId: 'trace-p39' };
}

const permissions = [
  { code: 'trial-prepaid:menu', resourceType: 'MENU' },
  { code: 'trial-prepaid:read', resourceType: 'API' },
  { code: 'trial-prepaid:write', resourceType: 'API' },
];

const summary = {
  tenantId: 42,
  channelId: 7,
  periodStart: '2026-09-01',
  periodEnd: '2026-09-30',
  sourceCount: 3,
  billableCount: 2,
  providerCostMil: 120,
  revenueMil: 100,
  profitMil: -20,
  priceBookVersion: 'SMS_STANDARD_V1',
  unitPriceMil: 50,
  formulaVersion: 'FINANCIAL_SOURCE_V1',
  formula: 'providerCostMil=sum(message_tasks.cost*1000), revenueMil=billableFinalCount*tenant_price_books.unit_price_mil, profitMil=revenueMil-providerCostMil',
  freshnessAt: '2026-09-03T12:00:00',
};

const source = {
  taskId: 1001,
  messageId: 'MSG-39-A',
  tenantId: 42,
  channelId: 7,
  sendStatus: 'SENT',
  finalStatus: 'DELIVERED',
  providerCostMil: 40,
  revenueMil: 50,
  profitMil: 10,
  priceBookVersion: 'SMS_STANDARD_V1',
  unitPriceMil: 50,
  formulaVersion: 'FINANCIAL_SOURCE_V1',
  formula: summary.formula,
  freshnessAt: '2026-09-03T12:00:00',
};

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/console/account-overview', async (route) => {
    await route.fulfill({
      json: response({
        id: 39,
        username: 'finance-39',
        userType: 'FINANCE',
        roleNames: ['财务'],
        permissions,
        lastLoginAt: null,
        lastLoginIp: null,
      }),
    });
  });
  await page.route('**/api/v1/console/financial/analytics?**', async (route) => {
    await route.fulfill({ json: response([summary]) });
  });
  await page.route('**/api/v1/console/financial/analytics/drilldown?**', async (route) => {
    await route.fulfill({ json: response([source]) });
  });
});

test('pw-p39-finance-analytics C-P39-FINANCE-ANALYTICS OBL-F-8-8-A pw-p39-channel-statistics C-P39-CHANNEL-STATISTICS OBL-F-4-5-A', async ({ page }) => {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('finance'), userType: 'FINANCE', tenantId: null });
  await page.goto('/admin/finance');
  await expect(page.getByTestId('admin-financial-source-financial-analytics-page')).toBeVisible();
  await page.getByTestId('admin-financial-source-financial-analytics-apply').click();
  await expect(page.getByTestId('admin-financial-source-financial-analytics-row')).toContainText('SMS_STANDARD_V1');
  await expect(page.getByTestId('admin-financial-source-financial-analytics-row')).toContainText('-0.020');
  await page.goto('/admin/statistics');
  await expect(page.getByTestId('admin-financial-source-channel-statistics-page')).toBeVisible();
  await expect(page.getByTestId('admin-financial-source-channel-statistics-row')).toContainText('7');
});

test('pw-p39-drilldown C-P39-DRILLDOWN OBL-F-8-8-B', async ({ page }) => {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('finance'), userType: 'FINANCE', tenantId: null });
  await page.goto('/admin/finance');
  await page.getByTestId('admin-financial-source-financial-analytics-drilldown').click();
  await expect(page.getByTestId('admin-financial-source-financial-analytics-drilldown-row')).toContainText('MSG-39-A');
  await expect(page.getByTestId('admin-financial-source-financial-analytics-drilldown-row')).toContainText('FINANCIAL_SOURCE_V1');
  await expect(page.getByTestId('admin-financial-source-financial-analytics-drilldown-row')).toContainText('SMS_STANDARD_V1');
});
