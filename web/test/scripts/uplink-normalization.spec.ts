import { expect, test, type Page, type Route } from '@playwright/test';
import { apiResponse, loginAs, mockEmptyDashboard } from './helpers';

test.use({ viewport: { width: 1440, height: 900 } });

const uplink = {
  id: 101,
  tenantId: 7,
  sourceProtocol: 'HTTP',
  sourceConnector: 'HTTP-API',
  sourceEventId: 'HTTP-UP-1',
  messageId: 'MSG-1',
  phoneMasked: '138****8000',
  content: '回复帮助',
  contentKeyword: '帮助',
  state: 'NORMALIZED',
  carrier: 'CMCC',
  province: '北京',
  city: '北京',
  destination: 'https://callback.example.com/uplink',
  channelId: 3,
  signatureId: 4,
  productCode: 'STANDARD',
  pushState: 'PUSH_FAILED',
  pushEventId: 501,
  receiveTime: '2026-09-10T10:00:00',
  createdAt: '2026-09-10T10:00:00',
  updatedAt: '2026-09-10T10:01:00',
};

async function mockUplinkApis(page: Page) {
  await mockEmptyDashboard(page);
  await page.route(/\/api\/v1\/console\/uplinks(?:\?.*)?$/, async (route: Route) => {
    await route.fulfill({ json: apiResponse([uplink]) });
  });
  await page.route('**/api/v1/console/uplinks/101', async (route: Route) => {
    await route.fulfill({ json: apiResponse(uplink) });
  });
  await page.route('**/api/v1/console/uplinks/101/replay', async (route: Route) => {
    expect(route.request().postDataJSON()).toEqual(expect.objectContaining({ reason: '运营复核后处理' }));
    await route.fulfill({ json: apiResponse({ eventId: 501, tenantId: 7, state: 'DELIVERED', resultCode: 'HTTP_200', resultMessage: 'ok' }) });
  });
  await page.route(/\/api\/v1\/console\/uplinks\/push-monitor(?:\?.*)?$/, async (route: Route) => {
    await route.fulfill({ json: apiResponse([
      { eventId: 501, tenantId: 7, sourceId: 'UPLINK:101', logicalId: 'UPLINK:101', destinationUrl: 'https://callback.example.com/uplink', state: 'PUSH_FAILED', attemptCount: 5, maxAttempts: 5, attemptRows: 5, nextAttemptAt: null, updatedAt: '2026-09-10T10:01:00', latencyMs: 60000 },
    ]) });
  });
  await page.route('**/api/v1/console/uplinks/push-monitor/501/replay', async (route: Route) => {
    await route.fulfill({ json: apiResponse({ eventId: 501, tenantId: 7, state: 'DELIVERED', resultCode: 'HTTP_200', resultMessage: 'ok' }) });
  });
  await page.route('**/api/v1/console/uplinks/push-monitor/501/pause', async (route: Route) => {
    await route.fulfill({ json: apiResponse({ eventId: 501, tenantId: 7, state: 'PAUSED', resultCode: 'PAUSED', resultMessage: 'operator' }) });
  });
  await page.route('**/api/v1/console/uplinks/push-monitor/501/resume', async (route: Route) => {
    await route.fulfill({ json: apiResponse({ eventId: 501, tenantId: 7, state: 'RETRY', resultCode: 'RESUMED', resultMessage: 'operator' }) });
  });
  await page.route(/\/api\/v1\/console\/tenant\/uplinks(?:\?.*)?$/, async (route: Route) => {
    await route.fulfill({ json: apiResponse([uplink]) });
  });
  await page.route('**/api/v1/console/tenant/uplinks/auto-reply', async (route: Route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({ json: apiResponse({ tenantId: 7, enabled: true, keyword: '帮助', templateId: 'TPL-1', responseContent: '收到', loopGuardMinutes: 30, auditReason: '租户配置上行自动回复', updatedBy: '7', updatedAt: '2026-09-10T10:00:00' }) });
      return;
    }
    expect(route.request().postDataJSON()).toEqual(expect.objectContaining({ enabled: true, keyword: '帮助', auditReason: '更新自动回复' }));
    await route.fulfill({ json: apiResponse({ tenantId: 7, enabled: true, keyword: '帮助', templateId: 'TPL-1', responseContent: '收到', loopGuardMinutes: 30, auditReason: '更新自动回复', updatedBy: '7', updatedAt: '2026-09-10T10:02:00' }) });
  });
}

test.describe('Phase 32 uplink normalization operations', () => {
  test('pw-p32-admin-uplink-details C-P32-ADMIN-UPLINK-DETAILS OBL-F-7-5-A pw-p32-admin-uplinks C-P32-ADMIN-UPLINKS OBL-F-10-1-A', async ({ page }) => {
    await mockUplinkApis(page);
    await loginAs(page, 'OPERATOR');
    await page.goto('/admin/uplink');
    await expect(page.getByTestId('admin-uplink-normalization-uplinks-page')).toBeVisible();
    await page.getByTestId('query-panel').first().getByTestId('query-panel-toggle').click();
    await page.getByTestId('admin-uplink-normalization-uplinks-filter-keyword').fill('帮助');
    await page.getByTestId('query-panel').first().getByTestId('query-submit').click();
    await expect(page.getByTestId('admin-uplink-normalization-uplinks-row')).toContainText('138****8000');
    await page.getByTestId('admin-uplink-normalization-uplink-detail').click();
    await expect(page.getByTestId('admin-uplink-normalization-detail-drawer')).toContainText('回复帮助');
  });

  test('pw-p32-uplink-replay C-P32-UPLINK-REPLAY OBL-F-7-5-B', async ({ page }) => {
    await mockUplinkApis(page);
    await loginAs(page, 'OPERATOR');
    await page.goto('/admin/uplink');
    await page.getByTestId('admin-uplink-normalization-uplink-replay').click();
    await expect(page.getByTestId('admin-uplink-normalization-operation-message')).toContainText('上行重放完成');
  });

  test('pw-p32-tenant-auto-reply C-P32-TENANT-AUTO-REPLY OBL-F-7-5-C pw-issue-58-tenant-uplinks C-ISSUE-58-TENANT-UPLINKS OBL-ISSUE-58-TENANT-UPLINKS', async ({ page }) => {
    await mockUplinkApis(page);
    await loginAs(page, 'TENANT_ADMIN');
    await page.goto('/tenant/uplink');
    await expect(page.getByTestId('tenant-uplinks')).toBeVisible();
    const panel = page.getByTestId('query-panel');
    await panel.getByTestId('query-panel-toggle').click();
    await panel.getByTestId('query-input-keyword').locator('input').fill('帮助');
    const filteredRequest = page.waitForRequest((request) => {
      const url = new URL(request.url());
      return url.pathname.endsWith('/tenant/uplinks') && url.searchParams.get('keyword') === '帮助';
    });
    await panel.getByTestId('query-submit').click();
    await filteredRequest;
    await panel.getByTestId('query-reset').click();
    await expect(panel.getByTestId('query-input-keyword').locator('input')).toHaveValue('');
    await expect(page.getByTestId('tenant-uplink-normalization-uplinks-auto-reply-config')).toBeVisible();
    await page.getByTestId('tenant-uplink-normalization-auto-reply-audit-reason').fill('更新自动回复');
    await page.getByTestId('tenant-uplink-normalization-auto-reply-save').click();
    await expect(page.getByTestId('tenant-uplink-normalization-operation-message')).toContainText('已保存');
  });

  test('pw-p32-push-monitor C-P32-PUSH-MONITOR OBL-F-10-4-A pw-p32-push-destination-action C-P32-PUSH-DESTINATION-ACTION OBL-F-10-4-B', async ({ page }) => {
    await mockUplinkApis(page);
    await loginAs(page, 'OPERATOR');
    await page.goto('/admin/uplink');
    await expect(page.getByTestId('admin-uplink-normalization-push-monitor-page')).toBeVisible();
    await expect(page.getByTestId('admin-uplink-normalization-push-monitor-row')).toContainText('PUSH_FAILED');
    await expect(page.getByTestId('admin-uplink-normalization-uplink-push-destination-action')).toBeVisible();
    await page.getByTestId('admin-uplink-normalization-push-pause').click();
    await expect(page.getByTestId('admin-uplink-normalization-operation-message')).toContainText('目的地暂停完成');
  });
});
