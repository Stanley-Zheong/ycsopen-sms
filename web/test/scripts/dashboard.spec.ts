import { expect, test } from '@playwright/test';
import { apiResponse, loginAs } from './helpers';

test('WEB-DASH-001 OBL-F-11-9-A C-P45-CHANNEL-RATIO pw-p45-channel-ratio OBL-F-11-9-B C-P45-TENANT-RATIO pw-p45-tenant-ratio OBL-F-11-9-C C-P45-THRESHOLD pw-p45-threshold OBL-F-11-9-D C-P45-PERIOD pw-p45-period complaint ratio panels render values and threshold status', async ({ page }) => {
  await mockOperationalDashboard(page);
  await page.route('**/api/v1/console/dashboard/complaint-ratio/channel**', (route) => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify(apiResponse([
      ratioRow('CHANNEL', 11, '移动主通道', 1000, 3, 0.003, 'BREACHED', true),
      ratioRow('CHANNEL', 12, '联通备用通道', 2000, 2, 0.001, 'NORMAL', false),
    ])),
  }));
  await page.route('**/api/v1/console/dashboard/complaint-ratio/tenant**', (route) => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify(apiResponse([
      ratioRow('TENANT', 7, '示例机构', 500, 1, 0.002, 'NORMAL', false),
    ])),
  }));
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/dashboard');
  await expect(page.getByTestId('admin-complaint-ratio-dashboard-complaint-ratio-threshold')).toContainText('3.00‰');
  await expect(page.getByTestId('admin-complaint-ratio-dashboard-complaint-ratio-threshold-tenant')).toContainText('3.00‰');
  await expect(page.getByTestId('admin-complaint-ratio-dashboard-complaint-ratio-period')).toContainText('显示全部');
  await expect(page.getByTestId('admin-complaint-ratio-dashboard-complaint-ratio-period-tenant')).toContainText('显示全部');
  await expect(page.getByTestId('admin-complaint-ratio-dashboard-complaint-ratio-channel')).toContainText('移动主通道');
  await expect(page.getByTestId('admin-complaint-ratio-dashboard-complaint-ratio-tenant')).toContainText('示例机构');
  const channelRow = page.getByRole('row').filter({ hasText: '移动主通道' });
  await expect(channelRow.getByRole('cell').nth(2)).toHaveText('1,000');
  await expect(channelRow.getByRole('cell').nth(3)).toHaveText('3');
  await expect(channelRow.getByRole('cell').nth(4)).toHaveText('3.00‰');
  await expect(channelRow.getByRole('cell').nth(7)).toHaveText('超阈值');
  await expect(channelRow).toHaveCSS('background-color', 'rgb(255, 240, 242)');
  const normalChannelRow = page.getByRole('row').filter({ hasText: '联通备用通道' });
  await expect(normalChannelRow.getByRole('cell').nth(2)).toHaveText('2,000');
  await expect(normalChannelRow.getByRole('cell').nth(3)).toHaveText('2');
  await expect(normalChannelRow.getByRole('cell').nth(4)).toHaveText('1.00‰');
  await expect(normalChannelRow.getByRole('cell').nth(7)).toHaveText('正常');
  await expect(normalChannelRow).not.toContainText('超阈值');
  const tenantRow = page.getByRole('row').filter({ hasText: '示例机构' });
  await expect(tenantRow.getByRole('cell').nth(2)).toHaveText('500');
  await expect(tenantRow.getByRole('cell').nth(3)).toHaveText('1');
  await expect(tenantRow.getByRole('cell').nth(4)).toHaveText('2.00‰');
  await expect(tenantRow.getByRole('cell').nth(7)).toHaveText('正常');
  await expect(tenantRow).not.toContainText('超阈值');
});

test('WEB-DASH-002 complaint ratio failures render stable error states', async ({ page }) => {
  await mockOperationalDashboard(page);
  await page.route('**/api/v1/console/dashboard/complaint-ratio/*', (route) =>
    route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ message: 'failure' }) }),
  );
  await loginAs(page, 'ADMIN');
  await expect(page.getByText(/加载失败，请稍后重试/)).toHaveCount(2);
  await expect(page.getByRole('row').filter({ hasText: '超阈值' })).toHaveCount(0);
});

test('WEB-DASH-003 OBL-F-11-9-E C-P45-DRILLDOWN pw-p45-drilldown OBL-FLOW-12-2-COMPLAINT-CHANNEL C-P45-CHANNEL-FLOW pw-p45-channel-flow complaint ratio drilldown and pause use exact dimension target', async ({ page }) => {
  await mockOperationalDashboard(page);
  await page.route('**/api/v1/console/dashboard/complaint-ratio/channel**', (route) => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify(apiResponse([ratioRow('CHANNEL', 11, '移动主通道', 1000, 3, 0.003, 'BREACHED', true)])),
  }));
  await page.route('**/api/v1/console/dashboard/complaint-ratio/tenant**', (route) => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify(apiResponse([])),
  }));
  await page.route('**/api/v1/console/dashboard/complaint-ratio/channel/*/complaints**', (route) => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify(apiResponse([
      { id: 91, source: 'REGULATOR', tenantId: 7, channelId: 11, messageId: 'MSG-91', summary: '监管投诉', status: 'PENDING', attributionQuality: 'COMPLETE', createdAt: '2026-08-02T10:00:00' },
    ])),
  }));
  await page.route('**/api/v1/console/dashboard/complaint-ratio/channel/*/pause**', (route) => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify(apiResponse({
      dimensionType: 'CHANNEL',
      dimensionId: 11,
      status: 'PAUSED',
      evidenceId: 5,
      alertRecordId: null,
      sourceKey: 'complaint-ratio:CHANNEL:11:2026-08:default-v1',
      action: 'PAUSE',
    })),
  }));

  await loginAs(page, 'ADMIN');
  await page.goto('/admin/dashboard');
  await expect(page.getByTestId('admin-complaint-ratio-dashboard-complaint-ratio-channel')).toContainText('暂停通道');
  await page.getByRole('row').filter({ hasText: '移动主通道' }).getByRole('button', { name: '钻取' }).click();
  await expect(page.getByTestId('admin-complaint-ratio-dashboard-complaint-ratio-drilldown')).toContainText('MSG-91');
  await page.getByRole('row').filter({ hasText: '移动主通道' }).getByRole('button', { name: '暂停通道' }).click();
  await expect(page.getByTestId('admin-complaint-ratio-dashboard-complaint-ratio-intervention-confirm')).toContainText('确认暂停通道');
  await page.getByTestId('admin-complaint-ratio-dashboard-complaint-ratio-intervention-reason').fill('投诉率超阈值人工确认');
  await page.getByRole('button', { name: '确认暂停通道' }).click();
  await expect(page.getByRole('status')).toContainText('complaint-ratio:CHANNEL:11:2026-08:default-v1');
});

function ratioRow(
  dimensionType: 'CHANNEL' | 'TENANT',
  dimensionId: number,
  dimensionName: string,
  sendCount: number,
  complaintCount: number,
  ratio: number,
  thresholdResult: 'BREACHED' | 'NORMAL',
  interventionAvailable: boolean,
) {
  return {
    statMonth: '2026-08',
    dimensionType,
    dimensionId,
    dimensionName,
    sendCount,
    complaintCount,
    ratio,
    thresholdValue: 0.003,
    thresholdConfigVersion: 'default-v1',
    overThreshold: thresholdResult === 'BREACHED',
    dataQuality: 'COMPLETE',
    thresholdResult,
    sourceRegistry: 'complaint_ratio_stats:message_tasks:complaints',
    freshnessPolicy: 'T_PLUS_1_DAILY',
    calculatedAt: '2026-08-02T01:00:00',
    rank: dimensionId === 11 ? 1 : 2,
    interventionAvailable,
    alertSourceKey: `complaint-ratio:${dimensionType}:${dimensionId}:2026-08:default-v1`,
  };
}

async function mockOperationalDashboard(page: Parameters<typeof loginAs>[0]) {
  await page.route('**/api/v1/console/operational-dashboards/platform', (route) => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify(apiResponse({
      realtime: { totalUsers: 1, todayMessages: 1, successRate: 1, activeTenants: 1 },
      kpi: { todaySend: 1, activeTenants: 1, successRate: 1, todayRevenue: 0 },
      hourlyTrend: [],
      tenantRank: [],
      channelHealth: { normal: 1, maintenance: 0, abnormal: 0 },
      financeWarning: { warningCount: 0, freshnessAt: '2026-08-02T01:00:00' },
      source: { registry: 'statistics_aggregates', formula: 'fixture', formulaVersion: 'v1', permissionScope: 'platform', freshnessAt: '2026-08-02T01:00:00' },
    })),
  }));
}
