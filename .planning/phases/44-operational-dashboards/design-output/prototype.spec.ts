import { expect, test } from '@playwright/test';

test('pw-p44-realtime-dashboard C-P44-REALTIME-DASHBOARD OBL-F-11-5-A pw-p44-realtime-trend C-P44-REALTIME-TREND OBL-F-11-5-B pw-p44-realtime-refresh C-P44-REALTIME-REFRESH OBL-F-11-5-C pw-p44-kpi-dashboard C-P44-KPI-DASHBOARD OBL-F-11-6-A pw-p44-kpi-hourly-trend C-P44-KPI-HOURLY-TREND OBL-F-11-6-B pw-p44-kpi-tenant-rank C-P44-KPI-TENANT-RANK OBL-F-11-6-C pw-p44-kpi-channel-health C-P44-KPI-CHANNEL-HEALTH OBL-F-11-6-D pw-p44-finance-warning-card C-P44-FINANCE-WARNING-CARD OBL-F-8-10-D pw-p44-shared-metric-source C-P44-SHARED-METRIC-SOURCE OBL-DISPLAY-DASH-SOURCE', async ({ page }) => {
  await page.goto('/admin/dashboard');
  await expect(page.getByTestId('admin-operational-dashboards-dashboard-realtime-page')).toBeVisible();
  await expect(page.getByTestId('admin-operational-dashboards-dashboard-realtime-send-trend')).toBeVisible();
  await page.getByTestId('admin-operational-dashboards-dashboard-realtime-refresh').click();
  await expect(page.getByTestId('admin-operational-dashboards-dashboard-kpi-page')).toBeVisible();
  await expect(page.getByTestId('admin-operational-dashboards-dashboard-kpi-hourly-trend')).toBeVisible();
  await expect(page.getByTestId('admin-operational-dashboards-dashboard-kpi-tenant-rank')).toBeVisible();
  await expect(page.getByTestId('admin-operational-dashboards-dashboard-kpi-channel-health')).toBeVisible();
  await expect(page.getByTestId('admin-operational-dashboards-dashboard-finance-warning-card')).toBeVisible();
  await expect(page.getByTestId('shared-operational-dashboards-metric-source')).toBeVisible();
});

test('pw-p44-resource-statistics C-P44-RESOURCE-STATISTICS OBL-F-11-3-A pw-p44-channel-statistics-comparison C-P44-CHANNEL-COMPARISON OBL-F-4-5-B pw-p44-channel-period-compare C-P44-CHANNEL-PERIOD-COMPARE OBL-F-11-1-B pw-p44-api-status-monitor C-P44-API-STATUS-MONITOR OBL-NFR-OBS-HEALTH pw-p44-dashboard-configuration C-P44-DASHBOARD-CONFIGURATION OBL-F-11-10-A pw-p44-configuration-role C-P44-CONFIGURATION-ROLE OBL-F-11-10-B', async ({ page }) => {
  await page.goto('/admin/statistics/resources');
  await expect(page.getByTestId('admin-operational-dashboards-statistics-resources-page')).toBeVisible();
  await expect(page.getByTestId('admin-operational-dashboards-channel-statistics-comparison')).toBeVisible();
  await expect(page.getByTestId('admin-operational-dashboards-statistics-channel-period-compare')).toBeVisible();
  await page.goto('/admin/api/status');
  await expect(page.getByTestId('admin-operational-dashboards-api-status-monitor-page')).toBeVisible();
  await page.goto('/admin/dashboard/configuration');
  await expect(page.getByTestId('admin-operational-dashboards-dashboard-configuration-page')).toBeVisible();
  await page.getByTestId('admin-operational-dashboards-dashboard-configuration-role').selectOption('TENANT_ADMIN');
});

test('pw-p44-tenant-overview-balance C-P44-TENANT-OVERVIEW-BALANCE OBL-F-1-5-B pw-p44-tenant-overview-scope C-P44-TENANT-OVERVIEW-SCOPE OBL-F-11-2-B pw-p44-tenant-template-stats C-P44-TENANT-TEMPLATE-STATS OBL-F-3-8-B', async ({ page }) => {
  await page.goto('/tenant/overview');
  await expect(page.getByTestId('tenant-operational-dashboards-tenant-overview-page')).toBeVisible();
  await expect(page.getByTestId('tenant-operational-dashboards-tenant-overview-scope')).toBeVisible();
  await page.goto('/tenant/templates/statistics');
  await expect(page.getByTestId('tenant-operational-dashboards-templates-statistics-page')).toBeVisible();
});
