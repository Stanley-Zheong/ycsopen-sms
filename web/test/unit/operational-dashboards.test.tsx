import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as operationalApi from '@/api/operationalDashboardApi';
import * as contractApi from '@/api/contractPricingApi';
import * as trialPrepaidApi from '@/api/trialPrepaidApi';
import DashboardPage from '@/pages/admin/dashboard/DashboardPage';
import ApiStatusPage from '@/pages/admin/dashboard/ApiStatusPage';
import DashboardConfigurationPage from '@/pages/admin/dashboard/DashboardConfigurationPage';
import ResourceStatisticsPage from '@/pages/admin/dashboard/ResourceStatisticsPage';
import OverviewPage from '@/pages/tenant/overview/OverviewPage';
import TenantTemplateStatisticsPage from '@/pages/tenant/templates/TenantTemplateStatisticsPage';
import { useAuthStore } from '@/store/authStore';

vi.mock('@/api/operationalDashboardApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/operationalDashboardApi')>();
  return {
    ...actual,
    getPlatformDashboard: vi.fn(),
    getTenantOperationalOverview: vi.fn(),
    getResourceStatistics: vi.fn(),
    getApiStatus: vi.fn(),
    getDashboardConfiguration: vi.fn(),
    saveDashboardConfiguration: vi.fn(),
  };
});

vi.mock('@/api/trialPrepaidApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/trialPrepaidApi')>();
  return {
    ...actual,
    getTrialOverview: vi.fn(),
    consumeTrial: vi.fn(),
    requestConversion: vi.fn(),
  };
});

vi.mock('@/api/contractPricingApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/contractPricingApi')>();
  return { ...actual, getContractOverview: vi.fn() };
});

function renderWithProviders(ui: ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
    </MemoryRouter>,
  );
}

describe('Phase 44 operational dashboards UI', () => {
  beforeEach(() => {
    useAuthStore.setState({
      accessToken: 'test-token',
      userType: 'ADMIN',
      tenantId: null,
      expiresAt: Date.now() + 60_000,
      principalKey: '44:test-token',
    });
    vi.mocked(operationalApi.getPlatformDashboard).mockResolvedValue({
      realtime: { totalUsers: 3, todayMessages: 150, successRate: 0.9, activeTenants: 1, comparisonMessages: 150 },
      kpi: { todaySend: 150, activeTenants: 1, successRate: 0.9, todayRevenue: 1.23, formula: 'success_count/send_count' },
      hourlyTrend: [{ bucketStart: '2026-09-10T09:00:00', sendCount: 100, successCount: 90, successRate: 0.9 }],
      tenantRank: [{ tenantId: 7, sendCount: 100, successCount: 90, successRate: 0.9 }],
      channelHealth: { normal: 1, maintenance: 1, abnormal: 1 },
      financeWarning: { warningCount: 1, freshnessAt: '2026-09-10T09:06:00' },
      source: { registry: 'statistics_aggregates', formula: 'success_count/send_count', freshnessAt: '2026-09-10T09:05:00', permissionScope: 'PLATFORM', formulaVersion: 'v1' },
    });
    vi.mocked(operationalApi.getTenantOperationalOverview).mockResolvedValue({
      tenantId: 7,
      balanceMil: 120000,
      trialStatus: 'TRIAL',
      contractStatus: 'ACTIVE',
      todayMessages: 100,
      successRate: 0.9,
      serviceStatus: 'NORMAL',
      source: { registry: 'statistics_aggregates', formula: 'success_count/send_count', freshnessAt: '2026-09-10T09:05:00', permissionScope: 'TENANT', formulaVersion: 'v1' },
    });
    vi.mocked(operationalApi.getResourceStatistics).mockResolvedValue({
      resources: [{ tenantId: 7, signatureId: 55, templateId: 66, submitCount: 100, successCount: 90, rejectedCount: 5, freshnessAt: '2026-09-10T09:04:00' }],
      channelComparisons: [{ tenantId: 7, channelId: 11, sendCount: 100, successCount: 90, failureCount: 10, successRate: 0.9, freshnessAt: '2026-09-10T09:05:00' }],
      accessibleColumns: ['tenant_id', 'signature_id', 'template_id', 'success_count'],
      source: { registry: 'statistics_aggregates', formula: 'RESOURCE_USAGE success/reject', freshnessAt: '2026-09-10T09:04:00', permissionScope: 'TENANT', formulaVersion: 'v1' },
      empty: false,
      errorState: 'NONE',
    });
    vi.mocked(operationalApi.getApiStatus).mockResolvedValue({
      rows: [{ component: 'DATABASE', status: 'NORMAL', source: 'statistics_aggregates', freshnessAt: '2026-09-10T09:05:00', impact: 'dashboard query source', drilldownKey: 'health|database|statistics_aggregates' }],
      source: { registry: 'operational_source_tables', formula: 'live table counts', freshnessAt: '2026-09-10T09:05:00', permissionScope: 'PLATFORM', formulaVersion: 'v1' },
    });
    vi.mocked(operationalApi.getDashboardConfiguration).mockResolvedValue({
      role: 'ADMIN',
      globalCards: true,
      tenantCards: true,
      refreshMode: 'MANUAL',
      pollingSeconds: 300,
      complaintThreshold: '0.0030',
      updatedBy: 'system',
      updatedAt: null,
    });
    vi.mocked(operationalApi.saveDashboardConfiguration).mockResolvedValue({
      role: 'TENANT_ADMIN',
      globalCards: false,
      tenantCards: true,
      refreshMode: 'POLLING',
      pollingSeconds: 300,
      complaintThreshold: '0.0030',
      updatedBy: '43',
      updatedAt: '2026-09-10T10:00:00',
    });
    vi.mocked(trialPrepaidApi.getTrialOverview).mockResolvedValue({
      tenantId: 7,
      trialStatus: 'TRIAL',
      quotaTotal: 500,
      quotaRemaining: 98,
      validFrom: '2026-09-09T00:00:00',
      validUntil: '2026-09-23T00:00:00',
      version: 1,
    });
    vi.mocked(trialPrepaidApi.consumeTrial).mockResolvedValue({
      tenantId: 7,
      trialStatus: 'TRIAL',
      quotaTotal: 500,
      quotaRemaining: 97,
      validFrom: '2026-09-09T00:00:00',
      validUntil: '2026-09-23T00:00:00',
      version: 2,
    });
    vi.mocked(trialPrepaidApi.requestConversion).mockResolvedValue({
      id: 1,
      tenantId: 7,
      trialStatus: 'TRIAL',
      status: 'REQUESTED',
    });
    vi.mocked(contractApi.getContractOverview).mockResolvedValue({
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
    });
  });

  afterEach(() => {
    vi.clearAllMocks();
    act(() => useAuthStore.getState().logout());
  });

  it('renders source-backed realtime and KPI dashboards with refresh and accessible tables', async () => {
    renderWithProviders(<DashboardPage />);

    expect(await screen.findByTestId('admin-operational-dashboards-dashboard-realtime-page')).toHaveTextContent('150');
    expect(screen.getByTestId('admin-operational-dashboards-dashboard-realtime-send-trend')).toHaveTextContent('09:00');
    expect(screen.getByTestId('admin-operational-dashboards-dashboard-kpi-page')).toHaveTextContent('1.23');
    expect(screen.getByTestId('admin-operational-dashboards-dashboard-kpi-hourly-trend')).toHaveTextContent('0.9000');
    expect(screen.getByTestId('admin-operational-dashboards-dashboard-kpi-tenant-rank')).toHaveTextContent('7');
    expect(screen.getByTestId('admin-operational-dashboards-dashboard-kpi-channel-health')).toHaveTextContent('ABNORMAL 1');
    expect(screen.getByTestId('admin-operational-dashboards-dashboard-finance-warning-card')).toHaveTextContent('1');
    expect(screen.getByTestId('shared-operational-dashboards-metric-source')).toHaveTextContent('statistics_aggregates');
    fireEvent.click(screen.getByTestId('admin-operational-dashboards-dashboard-realtime-refresh'));
    await waitFor(() => expect(operationalApi.getPlatformDashboard).toHaveBeenCalledTimes(2));
  });

  it('keeps dashboard retry available when the initial load fails', async () => {
    vi.mocked(operationalApi.getPlatformDashboard)
      .mockRejectedValueOnce(new Error('backend unavailable'))
      .mockResolvedValueOnce({
        realtime: { totalUsers: 3, todayMessages: 150, successRate: 0.9, activeTenants: 1, comparisonMessages: 150 },
        kpi: { todaySend: 150, activeTenants: 1, successRate: 0.9, todayRevenue: 1.23, formula: 'success_count/send_count' },
        hourlyTrend: [{ bucketStart: '2026-09-10T09:00:00', sendCount: 100, successCount: 90, successRate: 0.9 }],
        tenantRank: [{ tenantId: 7, sendCount: 100, successCount: 90, successRate: 0.9 }],
        channelHealth: { normal: 1, maintenance: 1, abnormal: 1 },
        financeWarning: { warningCount: 1, freshnessAt: '2026-09-10T09:06:00' },
        source: { registry: 'statistics_aggregates', formula: 'success_count/send_count', freshnessAt: '2026-09-10T09:05:00', permissionScope: 'PLATFORM', formulaVersion: 'v1' },
      });
    renderWithProviders(<DashboardPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent('运营仪表盘加载失败');
    fireEvent.click(screen.getByTestId('admin-operational-dashboards-dashboard-realtime-refresh'));

    await waitFor(() => expect(screen.getByTestId('admin-operational-dashboards-dashboard-realtime-page')).toHaveTextContent('150'));
  });

  it('renders tenant overview and tenant template statistics without cross-tenant controls', async () => {
    useAuthStore.setState({ userType: 'TENANT_ADMIN', tenantId: 7 });
    renderWithProviders(<OverviewPage />);

    await waitFor(() => expect(screen.getByTestId('tenant-operational-dashboards-tenant-overview-page')).toHaveTextContent('120000'));
    expect(screen.getByTestId('tenant-operational-dashboards-tenant-overview-page')).toHaveTextContent('NORMAL');
    expect(screen.getByTestId('tenant-operational-dashboards-tenant-overview-scope')).toHaveTextContent('当前机构');
    expect(operationalApi.getTenantOperationalOverview).toHaveBeenCalledWith(7);

    cleanup();
    renderWithProviders(<TenantTemplateStatisticsPage />);
    await waitFor(() => expect(screen.getByTestId('tenant-operational-dashboards-templates-statistics-page')).toHaveTextContent('55'));
    expect(screen.getByTestId('shared-operational-dashboards-metric-source')).toHaveTextContent('RESOURCE_USAGE');
  });

  it('renders resource statistics api status and role configuration surfaces', async () => {
    renderWithProviders(<ResourceStatisticsPage />);
    await waitFor(() => expect(screen.getByTestId('admin-operational-dashboards-statistics-resources-page')).toHaveTextContent('66'));
    expect(screen.getByTestId('admin-operational-dashboards-channel-statistics-comparison')).toHaveTextContent('11');
    expect(screen.getByTestId('admin-operational-dashboards-statistics-channel-period-compare')).toHaveTextContent('0.9000');

    cleanup();
    renderWithProviders(<ApiStatusPage />);
    await waitFor(() => expect(screen.getByTestId('admin-operational-dashboards-api-status-monitor-page')).toHaveTextContent('DATABASE'));
    expect(screen.getByTestId('admin-operational-dashboards-api-status-monitor-page')).toHaveTextContent('health|database|statistics_aggregates');

    cleanup();
    renderWithProviders(<DashboardConfigurationPage />);
    await waitFor(() => expect(screen.getByTestId('admin-operational-dashboards-dashboard-configuration-page')).toHaveTextContent('ADMIN'));
    fireEvent.change(screen.getByTestId('admin-operational-dashboards-dashboard-configuration-role'), { target: { value: 'TENANT_ADMIN' } });
    fireEvent.click(screen.getByTestId('admin-operational-dashboards-dashboard-configuration-save'));
    await waitFor(() => expect(operationalApi.saveDashboardConfiguration).toHaveBeenCalled());
    expect(vi.mocked(operationalApi.saveDashboardConfiguration).mock.calls[0][0]).toMatchObject({
      role: 'TENANT_ADMIN',
      globalCards: true,
      tenantCards: true,
      refreshMode: 'MANUAL',
      pollingSeconds: 300,
      complaintThreshold: '0.0030',
    });
    expect(await screen.findByTestId('admin-operational-dashboards-dashboard-configuration-message')).toHaveTextContent('TENANT_ADMIN');
  });
});
