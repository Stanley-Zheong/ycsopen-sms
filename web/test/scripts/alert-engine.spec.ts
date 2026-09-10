import { expect, test, type Page, type Route } from '@playwright/test';
import { apiResponse, loginAs, mockEmptyDashboard } from './helpers';

test.use({ viewport: { width: 1440, height: 900 } });

const alertRow = {
  id: 501,
  ruleId: 9,
  title: '通道失败率过高',
  content: '通道 11 失败率超过阈值',
  metricValue: 0.18,
  status: 'ACTIVE',
  severity: 'CRITICAL',
  sourceModule: 'CHANNEL',
  sourceKey: 'channel:11',
  impactScope: '影响 1250 项',
  triggeredAt: '2026-09-10T10:00:00',
  acknowledgedAt: null,
  acknowledgedBy: null,
  resolvedAt: null,
  resolvedBy: null,
  resolutionNote: null,
  deliveryState: 'DELIVERED',
  mutedUntil: null,
};

async function mockAlertApis(page: Page) {
  await mockEmptyDashboard(page);
  await page.route('**/api/v1/console/alerts/dashboard', (route: Route) => route.fulfill({
    json: apiResponse({ totalCount: 4, activeCount: 2, severeCount: 1, resolvedCount: 1 }),
  }));
  await page.route('**/api/v1/console/alerts/rules', async (route: Route) => {
    if (route.request().method() === 'POST') {
      expect(route.request().postDataJSON()).toEqual(expect.objectContaining({ metricName: 'FAILURE_RATE' }));
      await route.fulfill({ json: apiResponse({ id: 10, ruleName: '通道失败率告警', ruleType: 'FAILURE_RATE', metricName: 'FAILURE_RATE', metricSource: 'statistics_aggregates', thresholdValue: 0.2, comparisonOp: '>=', durationMinutes: 5, severity: 'CRITICAL', notifyChannels: '["SMS","EMAIL"]', notificationTargets: '["operations"]', sourceScope: 'PLATFORM', status: 'ACTIVE', updatedAt: null }) });
      return;
    }
    await route.fulfill({ json: apiResponse([
      { id: 9, ruleName: '通道失败率告警', ruleType: 'FAILURE_RATE', metricName: 'FAILURE_RATE', metricSource: 'statistics_aggregates', thresholdValue: 0.1, comparisonOp: '>=', durationMinutes: 5, severity: 'CRITICAL', notifyChannels: '["SMS","EMAIL","DINGTALK","WECOM"]', notificationTargets: '["operations","finance","tenant:7"]', sourceScope: 'PLATFORM', status: 'ACTIVE', updatedAt: null },
    ]) });
  });
  await page.route('**/api/v1/console/alerts/history', (route: Route) => route.fulfill({ json: apiResponse([alertRow]) }));
  await page.route('**/api/v1/console/alerts/history?**', (route: Route) => route.fulfill({ json: apiResponse([alertRow]) }));
  await page.route('**/api/v1/console/alerts/deliveries**', (route: Route) => route.fulfill({
    json: apiResponse([{ id: 1, alertRecordId: 501, channel: 'EMAIL', targetSnapshot: '["operations"]', providerResult: 'ACCEPTED', retryCount: 0, status: 'DELIVERED', failureReason: null, attemptedAt: '2026-09-10T10:01:00' }]),
  }));
  await page.route('**/api/v1/console/alerts/evaluate', (route: Route) => route.fulfill({ json: apiResponse({ alerts: [alertRow] }) }));
  await page.route('**/api/v1/console/alerts/501/acknowledge', (route: Route) => route.fulfill({ json: apiResponse({ ...alertRow, status: 'ACKNOWLEDGED', acknowledgedBy: 'operator' }) }));
  await page.route('**/api/v1/console/alerts/501/resolve', (route: Route) => route.fulfill({ json: apiResponse({ ...alertRow, status: 'RESOLVED', resolvedBy: 'operator', resolutionNote: '确认来源已恢复' }) }));
  await page.route('**/api/v1/console/alerts/501/mute', (route: Route) => route.fulfill({ json: apiResponse({ id: 1, scope: 'GLOBAL', reason: '运营临时静音', mutedBy: 'operator', mutedUntil: '2026-09-10T10:30:00' }) }));
}

test.describe('Phase 35 alert engine console', () => {
  test('pw-p35-dashboard C-P35-DASHBOARD OBL-F-11-7-A pw-p35-tabs C-P35-TABS OBL-F-11-7-B', async ({ page }) => {
    await mockAlertApis(page);
    await loginAs(page, 'OPERATOR');
    await page.goto('/admin/alerts');
    await expect(page.getByTestId('admin-alert-engine-dashboard-cards')).toContainText('严重告警');
    await expect(page.getByTestId('admin-alert-engine-dashboard-alert-tabs')).toContainText('活跃');
  });

  test('pw-p35-rules C-P35-RULES OBL-F-12-1-A pw-p35-targets C-P35-TARGETS OBL-F-12-2-A', async ({ page }) => {
    await mockAlertApis(page);
    await loginAs(page, 'OPERATOR');
    await page.goto('/admin/alerts');
    await expect(page.getByTestId('admin-alert-engine-rules-page')).toBeVisible();
    await expect(page.getByTestId('admin-alert-engine-notification-targets')).toContainText('SMS');
    await page.getByTestId('admin-alert-engine-rule-save').click();
    await expect(page.getByTestId('admin-alert-engine-message')).toContainText('告警规则已保存');
  });

  test('pw-p35-history C-P35-HISTORY OBL-F-12-3-A pw-p35-dashboard-action C-P35-DASHBOARD-ACTION OBL-F-11-7-C pw-p35-history-ack C-P35-HISTORY-ACK OBL-F-12-3-B pw-p35-state-ack C-P35-STATE-ACK OBL-STATE-ALERT-ACK pw-p35-state-resolve C-P35-STATE-RESOLVE OBL-STATE-ALERT-RESOLVE', async ({ page }) => {
    await mockAlertApis(page);
    await loginAs(page, 'OPERATOR');
    await page.goto('/admin/alerts');
    await expect(page.getByTestId('admin-alert-engine-alert-history')).toContainText('通道失败率过高');
    await expect(page.getByTestId('admin-alert-engine-dashboard-alert-action')).toBeVisible();
    await page.getByTestId('admin-alert-engine-alert-history-acknowledge').click();
    await expect(page.getByTestId('admin-alert-engine-message')).toContainText('告警已确认');
    await page.getByTestId('admin-alert-engine-alert-acknowledge').click();
    await expect(page.getByTestId('admin-alert-engine-message')).toContainText('告警已确认');
    await page.getByTestId('admin-alert-engine-alert-resolve').click();
    await expect(page.getByTestId('admin-alert-engine-message')).toContainText('告警已解决');
  });

  test('pw-p35-deliveries C-P35-DELIVERIES OBL-F-12-2-B pw-p35-history-mute C-P35-HISTORY-MUTE OBL-F-12-3-C pw-p35-state-mute C-P35-STATE-MUTE OBL-STATE-ALERT-MUTE', async ({ page }) => {
    await mockAlertApis(page);
    await loginAs(page, 'OPERATOR');
    await page.goto('/admin/alerts');
    await expect(page.getByTestId('admin-alert-engine-alert-delivery-attempts')).toContainText('EMAIL');
    await page.getByTestId('admin-alert-engine-alert-history-mute').click();
    await expect(page.getByTestId('admin-alert-engine-message')).toContainText('告警通知已静音');
    await page.getByTestId('admin-alert-engine-alert-mute').click();
    await expect(page.getByTestId('admin-alert-engine-message')).toContainText('告警通知已静音');
  });
});
