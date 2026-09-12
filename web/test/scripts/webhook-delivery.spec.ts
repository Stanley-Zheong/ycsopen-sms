import { expect, test, type Page, type Route } from '@playwright/test';
import { apiResponse, loginAs, mockEmptyDashboard } from './helpers';

test.use({ viewport: { width: 1440, height: 900 } });

async function mockWebhookApis(page: Page) {
  await mockEmptyDashboard(page);
  await page.route('**/api/v1/console/trial-prepaid/tenants/*/overview', (route: Route) => route.fulfill({
    json: apiResponse({ tenantId: 7, lifecycleStatus: 'TRIAL', quotaRemaining: 10 }),
  }));
  await page.route('**/api/v1/console/tenant/webhooks', async (route: Route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({ json: apiResponse({
        tenantId: 7,
        statusCallbackUrl: 'https://callback.example.com/status',
        uplinkCallbackUrl: 'https://callback.example.com/uplink',
        unsubscribeCallbackUrl: 'https://callback.example.com/unsubscribe',
        retryMaxCount: 5,
        retryBackoffSeconds: 30,
        status: 'ACTIVE',
        latestFailureReason: null,
        pausedAt: null,
        version: 1,
      }) });
      return;
    }
    expect(route.request().postDataJSON()).toEqual(expect.objectContaining({
      statusCallbackUrl: 'https://callback.example.com/status2',
      uplinkCallbackUrl: 'https://callback.example.com/uplink',
      unsubscribeCallbackUrl: 'https://callback.example.com/unsubscribe',
      retryMaxCount: 5,
      retryBackoffSeconds: 30,
    }));
    await route.fulfill({ json: apiResponse({
      tenantId: 7,
      statusCallbackUrl: 'https://callback.example.com/status2',
      uplinkCallbackUrl: 'https://callback.example.com/uplink',
      unsubscribeCallbackUrl: 'https://callback.example.com/unsubscribe',
      retryMaxCount: 5,
      retryBackoffSeconds: 30,
      status: 'ACTIVE',
      latestFailureReason: null,
      pausedAt: null,
      version: 2,
    }) });
  });
  await page.route('**/api/v1/console/tenant/webhooks/test', async (route: Route) => {
    expect(route.request().postDataJSON()).toEqual(expect.objectContaining({ type: 'STATUS', destinationUrl: 'https://callback.example.com/status2' }));
    await route.fulfill({ json: apiResponse({ eventId: 101, destinationUrl: 'https://callback.example.com/status2', state: 'DELIVERED', resultCode: 'HTTP_204', resultMessage: null }) });
  });
  await page.route(/\/api\/v1\/console\/webhook-deliveries\/failures(?:\?.*)?$/, async (route: Route) => {
    await route.fulfill({ json: apiResponse([
      { eventId: 501, tenantId: 7, eventType: 'STATUS', sourceId: 'STATUS:MSG_1:FAILED', logicalId: 'STATUS:MSG_1:FAILED', destinationUrl: 'https://callback.example.com/status', state: 'PUSH_FAILED', attemptCount: 5, maxAttempts: 5, nextAttemptAt: null, updatedAt: '2026-09-09T00:00:00' },
    ]) });
  });
  await page.route('**/api/v1/console/webhook-deliveries/501/replay', async (route: Route) => {
    expect(route.request().postDataJSON()).toEqual(expect.objectContaining({ reason: '运营复核后处理' }));
    await route.fulfill({ json: apiResponse({ eventId: 501, tenantId: 7, state: 'DELIVERED', resultCode: 'HTTP_200', resultMessage: 'ok' }) });
  });
  await page.route('**/api/v1/console/webhook-deliveries/501/pause', async (route: Route) => route.fulfill({
    json: apiResponse({ eventId: 501, tenantId: 7, state: 'PAUSED', resultCode: 'PAUSED', resultMessage: 'operator' }),
  }));
  await page.route('**/api/v1/console/webhook-deliveries/501/resume', async (route: Route) => route.fulfill({
    json: apiResponse({ eventId: 501, tenantId: 7, state: 'RETRY', resultCode: 'RESUMED', resultMessage: 'operator' }),
  }));
}

test.describe('Phase 28 webhook delivery transport', () => {
  test('pw-p28-tenant-webhooks C-P28-TENANT-WEBHOOKS OBL-F-6-6-A', async ({ page }) => {
    await mockWebhookApis(page);
    await loginAs(page, 'TENANT_ADMIN');
    await page.goto('/tenant/webhooks');
    await expect(page.getByTestId('tenant-webhook-delivery-config-form')).toBeVisible();
    await page.getByTestId('tenant-webhook-delivery-status-url').fill('https://callback.example.com/status2');
    await page.getByTestId('tenant-webhook-delivery-save').click();
    await expect(page.getByTestId('tenant-webhook-delivery-operation-message')).toContainText('已保存');
    await page.getByTestId('tenant-webhook-delivery-test').click();
    await expect(page.getByTestId('tenant-webhook-delivery-operation-message')).toContainText('测试回调完成');
  });

  test('pw-p28-admin-push-failures C-P28-ADMIN-PUSH-FAILURES OBL-F-7-7-A', async ({ page }) => {
    await mockWebhookApis(page);
    await loginAs(page, 'OPERATOR');
    await page.goto('/admin/push/failures');
    await expect(page.getByTestId('admin-webhook-delivery-push-failures-page')).toBeVisible();
    await expect(page.getByTestId('admin-webhook-delivery-push-failures-row')).toContainText('PUSH_FAILED');
  });

  test('pw-p28-push-failure-replay C-P28-PUSH-FAILURE-REPLAY OBL-F-6-6-C pw-p28-webhook-failure C-P28-WEBHOOK-FAILURE OBL-EDGE-WEBHOOK-FAILURE', async ({ page }) => {
    await mockWebhookApis(page);
    await loginAs(page, 'OPERATOR');
    await page.goto('/admin/push/failures');
    await page.getByTestId('admin-webhook-delivery-push-failures-replay').click();
    await expect(page.getByTestId('admin-webhook-delivery-operation-message')).toContainText('重放完成');
  });

  test('pw-p28-push-failure-policy C-P28-PUSH-FAILURE-POLICY OBL-F-7-7-B', async ({ page }) => {
    await mockWebhookApis(page);
    await loginAs(page, 'OPERATOR');
    await page.goto('/admin/push/failures');
    await expect(page.getByTestId('admin-webhook-delivery-push-failures-policy')).toBeVisible();
    await page.getByTestId('admin-webhook-delivery-push-failures-pause').click();
    await expect(page.getByTestId('admin-webhook-delivery-operation-message')).toContainText('暂停完成');
    await page.getByTestId('admin-webhook-delivery-push-failures-resume').click();
    await expect(page.getByTestId('admin-webhook-delivery-operation-message')).toContainText('恢复完成');
  });
});
