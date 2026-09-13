import { expect, test, type Page, type Route } from '@playwright/test';
import { apiResponse, loginAs, mockEmptyDashboard } from './helpers';

test.use({ viewport: { width: 1440, height: 900 } });

const jobs = [
  { id: 46, requestId: 'REQ-46', tenantId: 42, exportType: 'SEND_DETAIL', producer: 'MESSAGE_OPERATIONS', jobName: '发送详单导出', createdBy: '7', format: 'CSV', status: 'COMPLETED', progress: 100, recordCount: 2, fileSizeBytes: 128, fileSha256: 'abc', encryptionState: 'ENCRYPTED', retryCount: 0, splitCount: 1, partialFailureCount: 0, failureReason: null, createdAt: '2026-09-10T08:00:00', completedAt: '2026-09-10T08:00:01' },
  { id: 47, requestId: 'REQ-47', tenantId: 42, exportType: 'RECEIPT_DETAIL', producer: 'MESSAGE_OPERATIONS', jobName: '回执详单导出', createdBy: '7', format: 'JSON', status: 'FAILED', progress: 40, recordCount: 1, fileSizeBytes: 64, fileSha256: 'def', encryptionState: 'ENCRYPTED', retryCount: 0, splitCount: 1, partialFailureCount: 1, failureReason: '测试失败', createdAt: '2026-09-10T08:01:00', completedAt: null },
];

async function mockExportCenter(page: Page) {
  await mockEmptyDashboard(page);
  await page.route('**/api/v1/console/exports', (route: Route) => route.fulfill({ json: apiResponse(jobs) }));
  await page.route('**/api/v1/console/exports?**', (route: Route) => route.fulfill({ json: apiResponse(jobs) }));
  await page.route('**/api/v1/console/exports/46/download', (route: Route) => route.fulfill({
    json: apiResponse({ id: 46, requestId: 'REQ-46', fileName: 'send_detail-46.csv', format: 'CSV', encryptionState: 'ENCRYPTED', artifactBase64: 'ZmFrZQ==', sha256: 'abc', sizeBytes: 128 }),
  }));
  await page.route('**/api/v1/console/exports/47/retry', (route: Route) => route.fulfill({
    json: apiResponse({ ...jobs[1], status: 'COMPLETED', retryCount: 1, progress: 100, partialFailureCount: 0, failureReason: null }),
  }));
}

test('OBL-F-7-8-A C-P46-CENTER pw-p46-export-center OBL-F-7-8-C C-P46-DOWNLOAD pw-p46-download OBL-EDGE-EXPORT-FAILURE C-P46-RETRY pw-p46-retry', async ({ page }) => {
  await mockExportCenter(page);
  await loginAs(page, 'OPERATOR');
  await page.goto('/admin/export-center');
  await expect(page.getByTestId('admin-secure-async-export-center-page')).toBeVisible();
  await expect(page.getByTestId('admin-secure-async-export-center-cards')).toContainText('总任务');
  await expect(page.getByTestId('admin-secure-async-export-center-cards')).toContainText('EXCEL/CSV/JSON/PDF');
  await expect(page.getByTestId('admin-secure-async-export-center-download')).toHaveCount(2);
  await expect(page.getByTestId('admin-secure-async-export-center-retry')).toHaveCount(2);
  await page.getByTestId('admin-secure-async-export-center-download').first().click();
  await expect(page.getByTestId('admin-secure-async-export-center-message')).toContainText('已获取加密下载包');
  await page.getByTestId('admin-secure-async-export-center-retry').nth(1).click();
  await expect(page.getByTestId('admin-secure-async-export-center-message')).toContainText('导出任务已重试');
});

test('OBL-F-7-2-C C-P46-SEND-EXPORT pw-p46-send-export OBL-F-7-4-C C-P46-RECEIPT-EXPORT pw-p46-receipt-export', async ({ page }) => {
  await mockEmptyDashboard(page);
  await page.route('**/api/v1/console/message-operations/submissions?**', (route) => route.fulfill({ json: apiResponse([]) }));
  await page.route('**/api/v1/console/message-operations/sends?**', (route) => route.fulfill({ json: apiResponse([]) }));
  await page.route('**/api/v1/console/message-operations/receipts?**', (route) => route.fulfill({ json: apiResponse([]) }));
  await page.route('**/api/v1/console/message-operations/errors?**', (route) => route.fulfill({ json: apiResponse([]) }));
  await page.route('**/api/v1/console/message-operations/exports?**', (route) => route.fulfill({
    json: apiResponse({ actionId: 'EXPORT-1', action: 'EXPORT_REQUEST', target: 'snapshot', status: 'COMPLETED', resultCode: 'EXPORT_REQUESTED', resultMessage: '导出任务:46，匹配行数:2' }),
  }));
  await loginAs(page, 'OPERATOR');
  await page.goto('/admin/send/details');
  await page.getByTestId('admin-secure-async-send-details-export').click();
  await expect(page.getByTestId('admin-message-receipt-operation-message')).toContainText('导出任务');

  await page.goto('/admin/receipt/details');
  await page.getByTestId('admin-secure-async-receipt-export').click();
  await expect(page.getByTestId('admin-message-receipt-operation-message')).toContainText('导出任务');
});

test('OBL-F-7-9-B C-P46-UNSUBSCRIBE-EXPORT pw-p46-unsubscribe-export', async ({ page }) => {
  await page.route('**/api/v1/console/tenant/unsubscribes?**', (route) => route.fulfill({ json: apiResponse([]) }));
  await page.route('**/api/v1/console/tenant/unsubscribe/keywords', (route) => route.fulfill({ json: apiResponse([]) }));
  await page.route('**/api/v1/console/tenant/unsubscribes/export-request', (route) => route.fulfill({
    json: apiResponse({ taskId: 48, status: 'COMPLETED', recordCount: 1, fileFormat: 'CSV' }),
  }));
  await loginAs(page, 'TENANT_ADMIN');
  await page.goto('/tenant/unsubscribes');
  await page.getByTestId('tenant-secure-async-unsubscribes-export').click();
  await expect(page.getByTestId('tenant-unsubscribe-compliance-message')).toContainText('导出任务已创建');
});

test('OBL-F-8-9-B C-P46-BALANCE-EXPORT pw-p46-balance-export', async ({ page }) => {
  await mockEmptyDashboard(page);
  await page.route('**/api/v1/console/trial-prepaid/balance-audits?**', (route) => route.fulfill({ json: apiResponse([]) }));
  await page.route('**/api/v1/console/trial-prepaid/balance-audits/export-request?**', (route) => route.fulfill({
    json: apiResponse({ id: 49, requestId: 'BAL-49', exportType: 'BALANCE_AUDIT', format: 'CSV', status: 'COMPLETED', recordCount: 0 }),
  }));
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/balance-audit');
  await page.getByTestId('admin-secure-async-balance-audit-export').click();
  await expect(page.getByTestId('admin-trial-prepaid-message')).toContainText('余额审计导出任务已创建');
});
