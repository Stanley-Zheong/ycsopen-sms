import { expect, test } from '@playwright/test';

function token(subject: string) {
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({ sub: subject, jti: `p44-${subject}`, exp: 4102444800 })}.signature`;
}

function response(data: unknown) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-10T00:00:00Z', traceId: 'trace-p44' };
}

const permissions = [
  { code: 'operational-dashboard:menu', resourceType: 'MENU' },
  { code: 'operational-dashboard:read', resourceType: 'API' },
  { code: 'operational-dashboard:write', resourceType: 'API' },
];

const source = {
  registry: 'statistics_aggregates',
  formula: 'success_count/send_count',
  freshnessAt: '2026-09-10T09:05:00',
  permissionScope: 'PLATFORM',
  formulaVersion: 'v1',
};

const platformDashboard = {
  realtime: { totalUsers: 3, todayMessages: 150, successRate: 0.9, activeTenants: 1, comparisonMessages: 150 },
  kpi: { todaySend: 150, activeTenants: 1, successRate: 0.9, todayRevenue: 1.23, formula: 'success_count/send_count' },
  hourlyTrend: [{ bucketStart: '2026-09-10T09:00:00', sendCount: 100, successCount: 90, successRate: 0.9 }],
  tenantRank: [{ tenantId: 7, sendCount: 100, successCount: 90, successRate: 0.9 }],
  channelHealth: { normal: 1, maintenance: 1, abnormal: 1 },
  financeWarning: { warningCount: 1, freshnessAt: '2026-09-10T09:06:00' },
  source,
};

const resourceStatistics = {
  resources: [{ tenantId: 7, signatureId: 55, templateId: 66, submitCount: 100, successCount: 90, rejectedCount: 5, freshnessAt: '2026-09-10T09:04:00' }],
  channelComparisons: [{ tenantId: 7, channelId: 11, sendCount: 100, successCount: 90, failureCount: 10, successRate: 0.9, freshnessAt: '2026-09-10T09:05:00' }],
  accessibleColumns: ['tenant_id', 'signature_id', 'template_id', 'submit_count', 'success_count', 'rejected_count', 'freshness_at'],
  source: { ...source, formula: 'RESOURCE_USAGE success/reject', permissionScope: 'TENANT' },
  empty: false,
  errorState: 'NONE',
};

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/console/account-overview', async (route) => {
    await route.fulfill({ json: response({
      id: 44,
      username: 'operator-44',
      userType: 'ADMIN',
      roleNames: ['运营'],
      permissions,
      lastLoginAt: null,
      lastLoginIp: null,
    }) });
  });
  await page.route('**/api/v1/console/dashboard/complaint-ratio/*', async (route) => route.fulfill({ json: response([]) }));
  await page.route('**/api/v1/console/operational-dashboards/platform', async (route) => route.fulfill({ json: response(platformDashboard) }));
  await page.route('**/api/v1/console/operational-dashboards/resource-statistics*', async (route) => route.fulfill({ json: response(resourceStatistics) }));
  await page.route('**/api/v1/console/operational-dashboards/api-status', async (route) => route.fulfill({ json: response({
    rows: [{ component: 'DATABASE', status: 'NORMAL', source: 'statistics_aggregates', freshnessAt: '2026-09-10T09:05:00', impact: 'dashboard query source', drilldownKey: 'health|database|statistics_aggregates' }],
    source: { ...source, registry: 'operational_source_tables', formula: 'live table counts' },
  }) }));
  await page.route('**/api/v1/console/operational-dashboards/configuration*', async (route) => {
    if (route.request().method() === 'POST') {
      await route.fulfill({ json: response({ role: 'TENANT_ADMIN', globalCards: false, tenantCards: true, refreshMode: 'POLLING', pollingSeconds: 300, complaintThreshold: '0.0030', updatedBy: '44', updatedAt: '2026-09-10T10:00:00' }) });
      return;
    }
    await route.fulfill({ json: response({ role: 'ADMIN', globalCards: true, tenantCards: true, refreshMode: 'MANUAL', pollingSeconds: 300, complaintThreshold: '0.0030', updatedBy: 'system', updatedAt: null }) });
  });
  await page.route('**/api/v1/console/operational-dashboards/tenant-overview*', async (route) => route.fulfill({ json: response({
    tenantId: 7,
    balanceMil: 120000,
    trialStatus: 'TRIAL',
    contractStatus: 'ACTIVE',
    todayMessages: 100,
    successRate: 0.9,
    serviceStatus: 'NORMAL',
    source: { ...source, permissionScope: 'TENANT' },
  }) }));
  await page.route('**/api/v1/console/trial-prepaid/tenants/*/overview', async (route) => route.fulfill({ json: response({
    tenantId: 7,
    trialStatus: 'TRIAL',
    quotaTotal: 500,
    quotaRemaining: 98,
    validFrom: '2026-09-09T00:00:00',
    validUntil: '2026-09-23T00:00:00',
    version: 1,
  }) }));
  await page.route('**/api/v1/console/contracts/tenants/*/overview', async (route) => route.fulfill({ json: response({
    tenantId: 7,
    tenantState: 'CONTRACTED',
    billingMode: 'POSTPAID',
    priceBookVersion: 'SMS_STANDARD_V1',
    contractNo: 'HT-44',
    signedAt: '2026-09-10',
    attachmentRef: 'oss://contracts/HT-44.pdf',
    creditLimitMil: 1000000,
    billingPeriod: 'MONTHLY',
    contractStatus: 'ACTIVE',
    approvedBy: 'operator',
  }) }));
});

test('pw-p44-realtime-dashboard C-P44-REALTIME-DASHBOARD OBL-F-11-5-A pw-p44-realtime-trend C-P44-REALTIME-TREND OBL-F-11-5-B pw-p44-realtime-refresh C-P44-REALTIME-REFRESH OBL-F-11-5-C pw-p44-kpi-dashboard C-P44-KPI-DASHBOARD OBL-F-11-6-A pw-p44-kpi-hourly-trend C-P44-KPI-HOURLY-TREND OBL-F-11-6-B pw-p44-kpi-tenant-rank C-P44-KPI-TENANT-RANK OBL-F-11-6-C pw-p44-kpi-channel-health C-P44-KPI-CHANNEL-HEALTH OBL-F-11-6-D pw-p44-finance-warning-card C-P44-FINANCE-WARNING-CARD OBL-F-8-10-D pw-p44-shared-metric-source C-P44-SHARED-METRIC-SOURCE OBL-DISPLAY-DASH-SOURCE', async ({ page }) => {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('44'), userType: 'ADMIN', tenantId: null });
  await page.goto('/admin/dashboard');
  await expect(page.getByTestId('admin-operational-dashboards-dashboard-realtime-page')).toContainText('150');
  await expect(page.getByTestId('admin-operational-dashboards-dashboard-realtime-send-trend')).toContainText('09:00');
  await expect(page.getByTestId('admin-operational-dashboards-dashboard-kpi-page')).toContainText('1.23');
  await expect(page.getByTestId('admin-operational-dashboards-dashboard-kpi-hourly-trend')).toContainText('0.9000');
  await expect(page.getByTestId('admin-operational-dashboards-dashboard-kpi-tenant-rank')).toContainText('7');
  await expect(page.getByTestId('admin-operational-dashboards-dashboard-kpi-channel-health')).toContainText('ABNORMAL 1');
  await expect(page.getByTestId('admin-operational-dashboards-dashboard-finance-warning-card')).toContainText('1');
  await page.getByTestId('admin-operational-dashboards-dashboard-realtime-refresh').click();
  await expect(page.getByTestId('shared-operational-dashboards-metric-source')).toContainText('statistics_aggregates');
});

test('pw-p44-resource-statistics C-P44-RESOURCE-STATISTICS OBL-F-11-3-A pw-p44-channel-statistics-comparison C-P44-CHANNEL-COMPARISON OBL-F-4-5-B pw-p44-channel-period-compare C-P44-CHANNEL-PERIOD-COMPARE OBL-F-11-1-B pw-p44-api-status-monitor C-P44-API-STATUS-MONITOR OBL-NFR-OBS-HEALTH pw-p44-dashboard-configuration C-P44-DASHBOARD-CONFIGURATION OBL-F-11-10-A pw-p44-configuration-role C-P44-CONFIGURATION-ROLE OBL-F-11-10-B', async ({ page }) => {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('44'), userType: 'ADMIN', tenantId: null });
  await page.goto('/admin/statistics/resources');
  await expect(page.getByTestId('admin-operational-dashboards-statistics-resources-page')).toContainText('66');
  await expect(page.getByTestId('admin-operational-dashboards-channel-statistics-comparison')).toContainText('11');
  await expect(page.getByTestId('admin-operational-dashboards-statistics-channel-period-compare')).toContainText('0.9000');
  await page.goto('/admin/api/status');
  await expect(page.getByTestId('admin-operational-dashboards-api-status-monitor-page')).toContainText('health|database|statistics_aggregates');
  await page.goto('/admin/dashboard/configuration');
  await expect(page.getByTestId('admin-operational-dashboards-dashboard-configuration-page')).toContainText('ADMIN');
  await page.getByTestId('admin-operational-dashboards-dashboard-configuration-role').selectOption('TENANT_ADMIN');
  await page.getByTestId('admin-operational-dashboards-dashboard-configuration-save').click();
  await expect(page.getByTestId('admin-operational-dashboards-dashboard-configuration-message')).toContainText('TENANT_ADMIN');
});

test('pw-p44-tenant-overview-balance C-P44-TENANT-OVERVIEW-BALANCE OBL-F-1-5-B pw-p44-tenant-overview-scope C-P44-TENANT-OVERVIEW-SCOPE OBL-F-11-2-B pw-p44-tenant-template-stats C-P44-TENANT-TEMPLATE-STATS OBL-F-3-8-B', async ({ page }) => {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('tenant-7'), userType: 'TENANT_ADMIN', tenantId: 7 });
  await page.goto('/tenant/overview');
  await expect(page.getByTestId('tenant-operational-dashboards-tenant-overview-page')).toContainText('120000');
  await expect(page.getByTestId('tenant-operational-dashboards-tenant-overview-page')).toContainText('NORMAL');
  await expect(page.getByTestId('tenant-operational-dashboards-tenant-overview-scope')).toContainText('当前机构');
  await page.goto('/tenant/templates/statistics');
  await expect(page.getByTestId('tenant-operational-dashboards-templates-statistics-page')).toContainText('55');
});
