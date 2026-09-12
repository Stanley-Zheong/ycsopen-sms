import { expect, test, type Page, type Route } from '@playwright/test';
import { apiResponse, loginAs, mockEmptyDashboard } from './helpers';

test.use({ viewport: { width: 1440, height: 900 } });

const submissions = [{ submissionId: 101, tenantId: 42, submitId: 'SUBMIT-1', messageId: 'MSG_FAILED', sourceProtocol: 'HTTP', productType: 'NOTIFY', submissionStatus: 'ACCEPTED', sendStatus: 'FAILED', templateId: 11, signatureId: 12, errorCode: 'E42', errorMessage: '供应商拒绝', createdAt: '2026-09-09T00:00:00' }];
const sends = [{ taskId: 201, messageId: 'MSG_FAILED', tenantId: 42, submissionId: 101, maskedMobile: '已保护', contentSummary: '【签名】验证码...', sendStatus: 'FAILED', channelId: 7, providerMessageId: 'UP-1', carrier: 'MOBILE', province: '广东', city: '深圳', errorCode: 'E42', errorMessage: '供应商拒绝', cost: 0.05, retryCount: 0, outboxState: 'FAILED', sentAt: null, deliveredAt: null, createdAt: '2026-09-09T00:00:00', version: 3 }];
const receipts = [{ receiptId: 501, messageId: 'MSG_FAILED', tenantId: 42, maskedMobile: '已保护', channelId: 7, providerMessageId: 'UP-1', receiptStatus: 'FAILED', sendStatus: 'FAILED', errorCode: 'E42', rawPayloadSummary: 'raw payload protected', receiptDigest: 'R-1', carrier: 'MOBILE', province: '广东', city: '深圳', reportTime: '2026-09-09T00:00:00' }];
const errors = [{ normalizedCode: 'E42', platformCategory: 'FAILURE', severity: 'ERROR', retryable: true, totalCount: 1, tenantCount: 1, channelCount: 1, firstSeenAt: '2026-09-09T00:00:00', lastSeenAt: '2026-09-09T00:00:00' }];

async function mockMessageOperationsApis(page: Page) {
  await mockEmptyDashboard(page);
  await page.route(/\/api\/v1\/console\/message-operations\/submissions(?:\?.*)?$/, (route: Route) => route.fulfill({ json: apiResponse(submissions) }));
  await page.route(/\/api\/v1\/console\/message-operations\/sends(?:\?.*)?$/, (route: Route) => route.fulfill({ json: apiResponse(sends) }));
  await page.route(/\/api\/v1\/console\/message-operations\/receipts(?:\?.*)?$/, (route: Route) => route.fulfill({ json: apiResponse(receipts) }));
  await page.route(/\/api\/v1\/console\/message-operations\/errors(?:\?.*)?$/, (route: Route) => route.fulfill({ json: apiResponse(errors) }));
  await page.route('**/api/v1/console/message-operations/sends/MSG_FAILED/resend', async (route: Route) => {
    expect(route.request().postDataJSON()).toEqual(expect.objectContaining({ reason: '运营复核确认' }));
    await route.fulfill({ json: apiResponse({ actionId: 'RESEND-1', action: 'RESEND', target: 'MSG_FAILED', status: 'COMPLETED', resultCode: 'RETRY_CREATED', resultMessage: '301' }) });
  });
  await page.route('**/api/v1/console/message-operations/sends/MSG_FAILED/appeal', async (route: Route) => {
    expect(route.request().postDataJSON()).toEqual(expect.objectContaining({ reason: '运营复核确认' }));
    await route.fulfill({ json: apiResponse({ actionId: 'APPEAL-1', action: 'APPEAL', target: 'MSG_FAILED', status: 'COMPLETED', resultCode: 'APPEAL_RECORDED', resultMessage: null }) });
  });
  await page.route('**/api/v1/console/message-operations/receipts/501/correct', async (route: Route) => {
    expect(route.request().postDataJSON()).toEqual(expect.objectContaining({ reason: '运营复核确认', status: 'DELIVERED', taxonomyVersion: 'ST20260909' }));
    await route.fulfill({ json: apiResponse({ actionId: 'CORRECT-1', action: 'RECEIPT_CORRECT', target: 'MSG_FAILED', status: 'COMPLETED', resultCode: 'RECEIPT_CORRECTED', resultMessage: null }) });
  });
  await page.route('**/api/v1/console/message-operations/receipts/501/replay', async (route: Route) => route.fulfill({
    json: apiResponse({ actionId: 'REPLAY-1', action: 'RECEIPT_REPLAY', target: 'MSG_FAILED', status: 'COMPLETED', resultCode: 'RECEIPT_REPLAYED', resultMessage: 'FAILED' }),
  }));
  await page.route('**/api/v1/console/message-operations/errors/actions', async (route: Route) => {
    expect(route.request().postDataJSON()).toEqual(expect.objectContaining({ action: 'BULK_RETRY', errorCode: 'E42', messageIds: ['MSG_FAILED'] }));
    await route.fulfill({ json: apiResponse({ actionId: 'BULK-1', action: 'BULK_RETRY', total: 1, completed: 1, failed: 0, results: [] }) });
  });
  await page.route(/\/api\/v1\/console\/message-operations\/exports(?:\?.*)?$/, async (route: Route) => route.fulfill({
    json: apiResponse({ actionId: 'EXPORT-1', action: 'EXPORT_REQUEST', target: 'snapshot', status: 'COMPLETED', resultCode: 'EXPORT_REQUESTED', resultMessage: '导出请求已登记，匹配行数:3' }),
  }));
}

test.describe('Phase 27 message receipt error operations', () => {
  test('pw-p27-submission-page C-P27-SUBMISSION-PAGE OBL-F-7-1-A pw-p27-submission-trace C-P27-SUBMISSION-TRACE OBL-F-7-1-B', async ({ page }) => {
    await mockMessageOperationsApis(page);
    await loginAs(page, 'OPERATOR');
    await page.goto('/admin/submission/details');
    await expect(page.getByTestId('admin-message-receipt-submission-details-page')).toBeVisible();
    await expect(page.getByTestId('admin-message-receipt-submission-details-trace')).toContainText('SUBMIT-1');
    await page.getByTestId('admin-message-receipt-export-request').click();
    await expect(page.getByTestId('admin-message-receipt-operation-message')).toContainText('导出请求已登记');
  });

  test('pw-p27-send-page C-P27-SEND-PAGE OBL-F-7-2-A pw-p27-send-resend C-P27-SEND-RESEND OBL-F-7-2-B', async ({ page }) => {
    await mockMessageOperationsApis(page);
    await loginAs(page, 'OPERATOR');
    await page.goto('/admin/send/details');
    await expect(page.getByTestId('admin-message-receipt-send-details-page')).toBeVisible();
    await expect(page.getByTestId('admin-message-receipt-send-details-row')).toContainText('已保护');
    await page.getByTestId('admin-message-receipt-send-details-resend').click();
    await expect(page.getByTestId('admin-message-receipt-operation-message')).toContainText('重发已处理');
    await page.getByTestId('admin-message-receipt-send-details-appeal').click();
    await expect(page.getByTestId('admin-message-receipt-operation-message')).toContainText('申诉已登记');
  });

  test('pw-p27-receipt-page C-P27-RECEIPT-PAGE OBL-F-7-4-A pw-p27-receipt-correct C-P27-RECEIPT-CORRECT OBL-F-7-4-B', async ({ page }) => {
    await mockMessageOperationsApis(page);
    await loginAs(page, 'OPERATOR');
    await page.goto('/admin/receipt/details');
    await expect(page.getByTestId('admin-message-receipt-receipt-details-page')).toBeVisible();
    await expect(page.getByTestId('admin-message-receipt-receipt-details-row')).toContainText('raw payload protected');
    await page.getByTestId('admin-message-receipt-receipt-correct').click();
    await expect(page.getByTestId('admin-message-receipt-operation-message')).toContainText('回执纠正已应用');
    await page.getByTestId('admin-message-receipt-receipt-replay').click();
    await expect(page.getByTestId('admin-message-receipt-operation-message')).toContainText('回执重放已处理');
  });

  test('pw-p27-error-page C-P27-ERROR-PAGE OBL-F-7-6-A pw-p27-error-bulk-retry C-P27-ERROR-BULK-RETRY OBL-F-7-6-B', async ({ page }) => {
    await mockMessageOperationsApis(page);
    await loginAs(page, 'OPERATOR');
    await page.goto('/admin/error/details');
    await expect(page.getByTestId('admin-message-receipt-error-details-page')).toBeVisible();
    await expect(page.getByTestId('admin-message-receipt-error-details-row')).toContainText('E42');
    await page.getByTestId('admin-message-receipt-error-details-bulk-retry').click();
    await expect(page.getByTestId('admin-message-receipt-operation-message')).toContainText('批量重试完成');
  });
});
