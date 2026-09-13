import { expect, test, type Page, type Request, type Route } from '@playwright/test';
import { apiResponse, loginAs } from './helpers';

test.use({ viewport: { width: 1440, height: 900 } });

const recharge = {
  id: 3, tenantId: 42, amountMil: 300000, rechargeMethod: 'WECHAT', transactionRefMask: 'WX-R****0003', evidenceText: '微信凭证', status: 'PENDING', submitterActor: 'tenant-user', reviewerActor: null, reviewReason: null, reviewedAt: null, createdAt: '2026-09-10T02:00:00',
};
const uplink = {
  id: 101, tenantId: 7, sourceProtocol: 'HTTP', sourceConnector: 'HTTP-API', sourceEventId: 'HTTP-UP-1', messageId: 'MSG_FAILED', phoneMasked: '138****8000', content: '回复帮助', contentKeyword: '帮助', state: 'NORMALIZED', carrier: 'CMCC', province: '北京', city: '北京', destination: 'https://callback.example.com/uplink', channelId: 3, signatureId: 4, productCode: 'STANDARD', pushState: 'PUSH_FAILED', pushEventId: 501, receiveTime: '2026-09-10T10:00:00', createdAt: '2026-09-10T10:00:00', updatedAt: '2026-09-10T10:01:00',
};
const pushEvent = {
  eventId: 501, tenantId: 7, sourceId: 'UPLINK:101', logicalId: 'UPLINK:101', destinationUrl: 'https://callback.example.com/uplink', state: 'PUSH_FAILED', attemptCount: 5, maxAttempts: 5, attemptRows: 5, nextAttemptAt: null, updatedAt: '2026-09-10T10:01:00', latencyMs: 60000,
};
const send = {
  taskId: 201, messageId: 'MSG_FAILED', tenantId: 42, submissionId: 101, maskedMobile: '已保护', contentSummary: '【签名】验证码...', sendStatus: 'FAILED', channelId: 7, providerMessageId: 'UP-1', carrier: 'MOBILE', province: '广东', city: '深圳', errorCode: 'E42', errorMessage: '供应商拒绝', cost: 0.05, retryCount: 0, outboxState: 'FAILED', sentAt: null, deliveredAt: null, createdAt: '2026-09-09T00:00:00', version: 3,
};
const receipt = {
  receiptId: 501, messageId: 'MSG_FAILED', tenantId: 42, maskedMobile: '已保护', channelId: 7, providerMessageId: 'UP-1', receiptStatus: 'FAILED', sendStatus: 'FAILED', errorCode: 'E42', rawPayloadSummary: 'raw payload protected', receiptDigest: 'R-1', carrier: 'MOBILE', province: '广东', city: '深圳', reportTime: '2026-09-09T00:00:00',
};
const alert = {
  id: 601, ruleId: 9, title: '通道失败率过高', content: '通道 11 失败率超过阈值', metricValue: 0.18, status: 'ACTIVE', severity: 'CRITICAL', sourceModule: 'CHANNEL', sourceKey: 'channel:11', impactScope: '影响 1250 项', triggeredAt: '2026-09-10T10:00:00', acknowledgedAt: null, acknowledgedBy: null, resolvedAt: null, resolvedBy: null, resolutionNote: null, deliveryState: 'DELIVERED', mutedUntil: null,
};
const webhookFailure = {
  eventId: 701, tenantId: 7, eventType: 'STATUS', sourceId: 'STATUS:MSG_1:FAILED', logicalId: 'STATUS:MSG_1:FAILED', destinationUrl: 'https://callback.example.com/status', state: 'PUSH_FAILED', attemptCount: 5, maxAttempts: 5, nextAttemptAt: null, updatedAt: '2026-09-09T00:00:00',
};
const bulkTask = {
  bulkId: 801, tenantId: 42, batchKey: 'BULK-801', taskName: '批量发送任务', messageType: 'NOTIFY', priority: 'HIGH', totalCount: 3, validCount: 3, invalidCount: 0, runningCount: 1, completedCount: 1, failCount: 0, cancelledCount: 0, totalCost: 0.05, state: 'RUNNING', scheduleAt: null, createdAt: '2026-09-09T00:00:00', failureReason: null, controlReason: null,
};

function actionResult(action: string, target: string) {
  return { actionId: `${action}-1`, action, target, status: 'COMPLETED', resultCode: 'OK', resultMessage: '完成' };
}

async function mockBusinessApis(page: Page) {
  await page.route('**/api/v1/console/**', async (route: Route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname.replace('/api/v1', '');
    const method = request.method();
    let data: unknown = {};

    if (method === 'GET') {
      if (path === '/console/recharges/reviews') data = [recharge];
      else if (path === '/console/uplinks') data = [uplink];
      else if (path === '/console/uplinks/push-monitor') data = [pushEvent];
      else if (path === '/console/message-operations/submissions') data = [{ submissionId: 101, tenantId: 42, submitId: 'SUBMIT-1', messageId: 'MSG_FAILED', sourceProtocol: 'HTTP', productType: 'NOTIFY', submissionStatus: 'ACCEPTED', sendStatus: 'FAILED', templateId: 11, signatureId: 12, errorCode: 'E42', errorMessage: '供应商拒绝', createdAt: '2026-09-09T00:00:00' }];
      else if (path === '/console/message-operations/sends') data = [send];
      else if (path === '/console/message-operations/receipts') data = [receipt];
      else if (path === '/console/message-operations/errors') data = [{ normalizedCode: 'E42', platformCategory: 'FAILURE', severity: 'ERROR', retryable: true, totalCount: 1, tenantCount: 1, channelCount: 1, firstSeenAt: '2026-09-09T00:00:00', lastSeenAt: '2026-09-09T00:00:00' }];
      else if (path === '/console/alerts/dashboard') data = { totalCount: 1, activeCount: 1, severeCount: 1, resolvedCount: 0 };
      else if (path === '/console/alerts/rules') data = [];
      else if (path === '/console/alerts/history') data = [alert];
      else if (path === '/console/alerts/deliveries') data = [];
      else if (path === '/console/webhook-deliveries/failures') data = [webhookFailure];
      else if (path === '/console/bulk/tasks') data = [bulkTask];
      else if (path.startsWith('/console/dashboard/complaint-ratio/')) data = [];
    } else if (path === '/console/recharges/3/review') data = { ...recharge, status: 'APPROVED', reviewerActor: 'admin', reviewReason: request.postDataJSON().reason };
    else if (path === '/console/uplinks/101/replay' || path.startsWith('/console/uplinks/push-monitor/501/')) data = { eventId: 501, tenantId: 7, state: 'DELIVERED', resultCode: 'OK', resultMessage: '完成' };
    else if (path === '/console/message-operations/exports') data = actionResult('EXPORT_REQUEST', 'snapshot');
    else if (path === '/console/message-operations/sends/MSG_FAILED/resend') data = actionResult('RESEND', 'MSG_FAILED');
    else if (path === '/console/message-operations/receipts/501/correct') data = actionResult('RECEIPT_CORRECT', 'MSG_FAILED');
    else if (path === '/console/message-operations/errors/actions') data = { actionId: 'BULK-1', action: 'BULK_RETRY', total: 1, completed: 1, failed: 0, results: [] };
    else if (path === '/console/alerts/601/resolve') data = { ...alert, status: 'RESOLVED', resolutionNote: request.postDataJSON().reason };
    else if (path === '/console/alerts/601/mute') data = { id: 1, scope: 'GLOBAL', reason: request.postDataJSON().reason, mutedBy: 'admin', mutedUntil: '2026-09-10T10:30:00' };
    else if (path.startsWith('/console/webhook-deliveries/701/')) data = { eventId: 701, tenantId: 7, state: 'DELIVERED', resultCode: 'OK', resultMessage: '完成' };
    else if (path.startsWith('/console/bulk/tasks/801/')) data = { ...bulkTask, state: 'PAUSED', controlReason: request.postDataJSON().reason };

    if (method === 'POST' && path === '/console/recharges/3/review') {
      await new Promise((resolve) => setTimeout(resolve, 800));
    }
    await route.fulfill({ json: apiResponse(data) });
  });
}

async function verifyAction(page: Page, input: {
  triggerId: string;
  idPrefix: string;
  reasonTestId: string;
  targetText: string;
  reason: string;
  requestPath: string;
  cancelFirst?: boolean;
  additionalTargetText?: string;
  maxLength?: number;
  pendingBackgroundTriggerId?: string;
}) {
  const matchingRequests: Request[] = [];
  const captureMatchingRequest = (request: Request) => {
    if (new URL(request.url()).pathname === `/api/v1${input.requestPath}`) matchingRequests.push(request);
  };
  page.on('request', captureMatchingRequest);

  await expect(page.getByTestId(input.reasonTestId)).toHaveCount(0);
  await expect(page.getByTestId(input.triggerId)).toBeVisible();
  await page.getByTestId(input.triggerId).click();
  await expect(page.getByTestId(`${input.idPrefix}-dialog`)).toBeVisible();
  await expect(page.getByTestId(`${input.idPrefix}-target`)).toContainText(input.targetText);
  if (input.additionalTargetText) await expect(page.getByTestId(`${input.idPrefix}-target`)).toContainText(input.additionalTargetText);
  await expect(page.getByTestId(`${input.idPrefix}-consequence`)).not.toHaveText('');
  await expect(page.getByTestId(`${input.idPrefix}-confirm`)).toBeDisabled();
  if (input.maxLength) await expect(page.getByTestId(input.reasonTestId)).toHaveAttribute('maxlength', String(input.maxLength));

  if (input.cancelFirst) {
    await page.getByTestId(`${input.idPrefix}-cancel`).click();
    await expect(page.getByTestId(`${input.idPrefix}-dialog`)).toHaveCount(0);
    expect(matchingRequests).toHaveLength(0);
    await page.getByTestId(input.triggerId).click();
  }

  await page.getByTestId(input.reasonTestId).fill(`  ${input.reason}  `);
  expect(matchingRequests).toHaveLength(0);
  const requestPromise = page.waitForRequest((request: Request) => new URL(request.url()).pathname === `/api/v1${input.requestPath}`);
  await page.getByTestId(`${input.idPrefix}-confirm`).click();
  const request = await requestPromise;
  expect(request.postDataJSON().reason).toBe(input.reason);
  expect(matchingRequests).toHaveLength(1);
  if (input.pendingBackgroundTriggerId) {
    await expect(page.getByTestId(`${input.idPrefix}-confirm`)).toBeDisabled();
    await page.keyboard.press('Escape');
    await expect(page.getByTestId(`${input.idPrefix}-dialog`)).toBeVisible();
    await expect(page.getByTestId(input.pendingBackgroundTriggerId).click({ timeout: 250 })).rejects.toThrow();
    await expect(page.getByTestId(`${input.idPrefix}-target`)).toContainText(input.targetText);
  }
  await expect(page.getByTestId(`${input.idPrefix}-dialog`)).toHaveCount(0);
  page.off('request', captureMatchingRequest);
}

test('pw-issue-91-action-reason-context C-ISSUE-91-ACTION-REASON-CONTEXT OBL-ISSUE-91-ACTION-REASON', async ({ page }) => {
  await mockBusinessApis(page);
  await loginAs(page, 'ADMIN');

  await page.goto('/admin/tenant-recharge-review');
  await verifyAction(page, { triggerId: 'admin-tenant-recharge-operations-review-approve', idPrefix: 'admin-tenant-recharge-operations-review-action', reasonTestId: 'admin-tenant-recharge-operations-review-reason', targetText: '机构 42', reason: '到账凭证复核一致', requestPath: '/console/recharges/3/review', cancelFirst: true, maxLength: 255, pendingBackgroundTriggerId: 'admin-tenant-recharge-operations-review-reject' });

  await page.goto('/admin/uplink');
  await verifyAction(page, { triggerId: 'admin-uplink-normalization-uplink-replay', idPrefix: 'admin-uplink-normalization-action', reasonTestId: 'admin-uplink-normalization-uplink-replay-reason', targetText: '上行记录 #101', reason: '上行内容复核通过', requestPath: '/console/uplinks/101/replay' });
  await verifyAction(page, { triggerId: 'admin-uplink-normalization-push-pause', idPrefix: 'admin-uplink-normalization-action', reasonTestId: 'admin-uplink-normalization-push-action-reason', targetText: 'UPLINK:101', reason: '目的地维护暂停', requestPath: '/console/uplinks/push-monitor/501/pause' });

  await page.goto('/admin/submission/details');
  await verifyAction(page, { triggerId: 'admin-message-receipt-export-request', idPrefix: 'admin-message-receipt-action', reasonTestId: 'admin-message-receipt-action-reason', targetText: '提交详情当前筛选结果', additionalTargetText: '机构 42', reason: '导出用于问题排查', requestPath: '/console/message-operations/exports' });

  await page.goto('/admin/send/details');
  await verifyAction(page, { triggerId: 'admin-message-receipt-send-details-resend', idPrefix: 'admin-message-receipt-action', reasonTestId: 'admin-message-receipt-action-reason', targetText: 'MSG_FAILED', reason: '供应商失败重试', requestPath: '/console/message-operations/sends/MSG_FAILED/resend' });

  await page.goto('/admin/receipt/details');
  await verifyAction(page, { triggerId: 'admin-message-receipt-receipt-correct', idPrefix: 'admin-message-receipt-action', reasonTestId: 'admin-message-receipt-action-reason', targetText: '回执 #501', reason: '运营商送达凭证确认', requestPath: '/console/message-operations/receipts/501/correct' });

  await page.goto('/admin/error/details');
  await verifyAction(page, { triggerId: 'admin-message-receipt-error-details-bulk-retry', idPrefix: 'admin-message-receipt-action', reasonTestId: 'admin-message-receipt-action-reason', targetText: '错误码 E42', reason: '错误组已具备重试条件', requestPath: '/console/message-operations/errors/actions' });

  await page.goto('/admin/alerts');
  await verifyAction(page, { triggerId: 'admin-alert-engine-alert-resolve', idPrefix: 'admin-alert-engine-action', reasonTestId: 'admin-alert-engine-resolve-reason', targetText: '通道失败率过高', reason: '确认来源已恢复', requestPath: '/console/alerts/601/resolve', maxLength: 255 });
  await verifyAction(page, { triggerId: 'admin-alert-engine-alert-mute', idPrefix: 'admin-alert-engine-action', reasonTestId: 'admin-alert-engine-mute-reason', targetText: '通道失败率过高', reason: '运营临时静音', requestPath: '/console/alerts/601/mute', maxLength: 255 });

  await page.goto('/admin/push/failures');
  await verifyAction(page, { triggerId: 'admin-webhook-delivery-push-failures-replay', idPrefix: 'admin-webhook-delivery-action', reasonTestId: 'admin-webhook-delivery-action-reason', targetText: 'STATUS:MSG_1:FAILED', reason: '修复目的地后重放', requestPath: '/console/webhook-deliveries/701/replay' });

  await page.goto('/admin/send/jobs');
  await verifyAction(page, { triggerId: 'admin-bulk-scheduled-send-jobs-pause', idPrefix: 'admin-bulk-scheduled-send-jobs-action', reasonTestId: 'admin-bulk-scheduled-send-jobs-reason', targetText: 'BULK-801', reason: '等待通道恢复', requestPath: '/console/bulk/tasks/801/pause' });
});
