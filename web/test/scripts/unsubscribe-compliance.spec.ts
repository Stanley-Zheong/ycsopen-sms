import { expect, test, type Page, type Route } from '@playwright/test';
import { apiResponse, loginAs, mockEmptyDashboard } from './helpers';

test.use({ viewport: { width: 1440, height: 900 } });

const record = {
  id: 701,
  tenantId: 7,
  signatureId: 88,
  productCode: 'STANDARD',
  maskedMobile: '138****8000',
  triggerKeyword: 'TD',
  method: 'UPLINK',
  result: 'TENANT_BLACKLISTED',
  handlingState: 'TENANT_BLACKLISTED',
  notificationState: 'PENDING',
  notificationEventId: 501,
  confirmationState: 'DISABLED',
  replyEventId: null,
  uplinkRecordId: 9001,
  unsubscribedAt: '2026-09-10T10:00:00',
  updatedAt: '2026-09-10T10:01:00',
};

async function mockUnsubscribeApis(page: Page) {
  await mockEmptyDashboard(page);
  await page.route(/\/api\/v1\/console\/unsubscribes(?:\?.*)?$/, async (route: Route) => {
    await route.fulfill({ json: apiResponse([record]) });
  });
  await page.route(/\/api\/v1\/console\/unsubscribe\/keywords(?:\?.*)?$/, async (route: Route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({ json: apiResponse([
        { id: 1, keyword: 'TD', keywordNormalized: 'TD', scope: 'GLOBAL', tenantId: null, status: 'ACTIVE', createdBy: 'system', updatedAt: null },
        { id: 2, keyword: '退订', keywordNormalized: '退订', scope: 'GLOBAL', tenantId: null, status: 'ACTIVE', createdBy: 'system', updatedAt: null },
      ]) });
      return;
    }
    expect(route.request().postDataJSON()).toEqual(expect.objectContaining({ keyword: 'QUIT' }));
    await route.fulfill({ json: apiResponse({ id: 3, keyword: 'QUIT', keywordNormalized: 'QUIT', scope: 'GLOBAL', tenantId: null, status: 'ACTIVE', createdBy: 'operator', updatedAt: null }) });
  });
  await page.route(/\/api\/v1\/console\/unsubscribe\/statistics(?:\?.*)?$/, async (route: Route) => {
    await route.fulfill({ json: apiResponse([
      { tenantId: 7, signatureId: 88, productCode: 'STANDARD', unsubscribeCount: 2, finalSentCount: 200, rate: 0.01 },
    ]) });
  });
  await page.route('**/api/v1/console/unsubscribe/alerts/evaluate', async (route: Route) => {
    await route.fulfill({ json: apiResponse([
      { id: 1, tenantId: 7, signatureId: 88, productCode: 'STANDARD', periodStart: null, periodEnd: null, unsubscribeCount: 2, finalSentCount: 20, rate: 0.1, thresholdRate: 0.03, formula: 'unsubscribe_count/final_sent_count', freshnessAt: null, sourceEvent: 'UNSUBSCRIBE_RATE_ABNORMAL' },
    ]) });
  });
  await page.route(/\/api\/v1\/console\/tenant\/unsubscribes(?:\?.*)?$/, async (route: Route) => {
    await route.fulfill({ json: apiResponse([record]) });
  });
  await page.route('**/api/v1/console/tenant/unsubscribes/export-request', async (route: Route) => {
    await route.fulfill({ json: apiResponse({ taskId: 66, status: 'PENDING', recordCount: 1, fileFormat: 'CSV' }) });
  });
  await page.route('**/api/v1/console/tenant/unsubscribe/keywords', async (route: Route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({ json: apiResponse([
        { id: 1, keyword: 'TD', keywordNormalized: 'TD', scope: 'GLOBAL', tenantId: null, status: 'ACTIVE', createdBy: 'system', updatedAt: null },
      ]) });
      return;
    }
    expect(route.request().postDataJSON()).toEqual(expect.objectContaining({ keyword: 'STOP', scope: 'TENANT' }));
    await route.fulfill({ json: apiResponse({ id: 9, keyword: 'STOP', keywordNormalized: 'STOP', scope: 'TENANT', tenantId: 7, status: 'ACTIVE', createdBy: '7', updatedAt: null }) });
  });
}

test.describe('Phase 33 unsubscribe compliance', () => {
  test('pw-p33-tenant-unsubscribes C-P33-TENANT-UNSUBSCRIBES OBL-F-7-9-A pw-p33-tenant-notification C-P33-TENANT-NOTIFICATION OBL-F-10-2-E', async ({ page }) => {
    await mockUnsubscribeApis(page);
    await loginAs(page, 'TENANT_ADMIN');
    await page.goto('/tenant/unsubscribes');
    await expect(page.getByTestId('tenant-unsubscribe-compliance-unsubscribes-page')).toBeVisible();
    await expect(page.getByTestId('tenant-unsubscribe-compliance-unsubscribe-row')).toContainText('138****8000');
    await expect(page.getByTestId('tenant-unsubscribe-compliance-unsubscribes-notification-state')).toContainText('PENDING');
    await page.getByTestId('tenant-secure-async-unsubscribes-export').click();
    await expect(page.getByTestId('tenant-unsubscribe-compliance-message')).toContainText('导出任务已创建');
  });

  test('pw-p33-admin-keywords C-P33-ADMIN-KEYWORDS OBL-F-10-2-A', async ({ page }) => {
    await mockUnsubscribeApis(page);
    await loginAs(page, 'OPERATOR');
    await page.goto('/admin/unsubscribes');
    await expect(page.getByTestId('admin-unsubscribe-compliance-keywords-page')).toBeVisible();
    await expect(page.getByTestId('admin-unsubscribe-compliance-keywords-table')).toContainText('退订');
    await page.getByTestId('admin-unsubscribe-compliance-keyword-input').fill('QUIT');
    await page.getByTestId('admin-unsubscribe-compliance-keyword-save').click();
    await expect(page.getByTestId('admin-unsubscribe-compliance-message')).toContainText('退订关键词已保存');
  });

  test('pw-p33-admin-statistics C-P33-ADMIN-STATISTICS OBL-F-10-3-A pw-p33-alert C-P33-ALERT OBL-F-10-3-B', async ({ page }) => {
    await mockUnsubscribeApis(page);
    await loginAs(page, 'OPERATOR');
    await page.goto('/admin/unsubscribes');
    await expect(page.getByTestId('admin-unsubscribe-compliance-statistics-page')).toBeVisible();
    await expect(page.getByTestId('admin-unsubscribe-compliance-statistics-row')).toContainText('1.00%');
    await page.getByTestId('admin-unsubscribe-compliance-alert-evaluate').click();
    await expect(page.getByTestId('admin-unsubscribe-compliance-message')).toContainText('告警评估完成');
  });
});
