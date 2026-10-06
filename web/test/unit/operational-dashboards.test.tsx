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

vi.mock('@/api/dashboard', () => ({
  fetchComplaintRatio: vi.fn().mockResolvedValue([]),
  fetchComplaintRatioCases: vi.fn().mockResolvedValue([]),
  pauseComplaintRatioTarget: vi.fn(),
}));

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
      todayAggregation: {
        state: 'FRESH',
        businessDate: '2026-09-10',
        businessTimeZone: 'Asia/Shanghai',
        sourceRegistry: 'statistics_aggregates',
        refreshedAt: '2026-09-10T09:05:00',
        sourceChangedAt: '2026-09-10T09:04:30',
        sourceRecordCount: 150,
        aggregateRowCount: 2,
      },
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
        todayAggregation: {
          state: 'FRESH',
          businessDate: '2026-09-10',
          businessTimeZone: 'Asia/Shanghai',
          sourceRegistry: 'statistics_aggregates',
          refreshedAt: '2026-09-10T09:05:00',
          sourceChangedAt: '2026-09-10T09:04:30',
          sourceRecordCount: 150,
          aggregateRowCount: 2,
        },
      });
    renderWithProviders(<DashboardPage />);

    expect(screen.getByTestId('admin-operational-dashboards-dashboard-data-status')).toHaveAttribute('data-state', 'LOADING');
    expect(await screen.findByText('运营仪表盘加载失败，可重试。')).toBeInTheDocument();
    expect(screen.getByTestId('admin-operational-dashboards-dashboard-data-status')).toHaveAttribute('data-state', 'ERROR');
    fireEvent.click(screen.getByTestId('admin-operational-dashboards-dashboard-realtime-refresh'));

    await waitFor(() => expect(screen.getByTestId('admin-operational-dashboards-dashboard-realtime-page')).toHaveTextContent('150'));
  });

  it.each([
    ['NOT_REFRESHED', '统计尚未刷新', '尚无成功刷新'],
    ['EMPTY', '今日无消息数据', '2026-09-10T09:05:00'],
    ['STALE', '数据已过期', '2026-09-10T09:05:00'],
  ] as const)('renders %s without turning absent aggregate values into zero', async (state, guidance, freshness) => {
    vi.mocked(operationalApi.getPlatformDashboard).mockResolvedValue({
      realtime: { totalUsers: 3, todayMessages: null, successRate: null, activeTenants: 1, comparisonMessages: null },
      kpi: { todaySend: null, activeTenants: 1, successRate: null, todayRevenue: null, formula: 'success_count/send_count' },
      hourlyTrend: [],
      tenantRank: [],
      channelHealth: { normal: 1, maintenance: 1, abnormal: 1 },
      financeWarning: { warningCount: 1, freshnessAt: '2026-09-10T09:06:00' },
      source: { registry: 'statistics_aggregates', formula: 'success_count/send_count', freshnessAt: null, permissionScope: 'PLATFORM', formulaVersion: 'v1' },
      todayAggregation: {
        state,
        businessDate: '2026-09-10',
        businessTimeZone: 'Asia/Shanghai',
        sourceRegistry: 'statistics_aggregates',
        refreshedAt: state === 'NOT_REFRESHED' ? null : '2026-09-10T09:05:00',
        sourceChangedAt: state === 'STALE' ? '2026-09-10T09:05:30' : null,
        sourceRecordCount: 0,
        aggregateRowCount: 0,
      },
    });

    renderWithProviders(<DashboardPage />);

    const status = screen.getByTestId('admin-operational-dashboards-dashboard-data-status');
    await waitFor(() => expect(status).toHaveAttribute('data-state', state));
    expect(status).toHaveTextContent(guidance);
    expect(screen.getByTestId('admin-operational-dashboards-dashboard-aggregation-business-date')).toHaveTextContent('2026-09-10');
    expect(screen.getByTestId('admin-operational-dashboards-dashboard-aggregation-business-date')).toHaveTextContent('Asia/Shanghai');
    expect(screen.getByTestId('admin-operational-dashboards-dashboard-aggregation-freshness')).toHaveTextContent(freshness);
    for (const testId of [
      'admin-operational-dashboards-dashboard-realtime-today-messages-value',
      'admin-operational-dashboards-dashboard-realtime-success-rate-value',
      'admin-operational-dashboards-dashboard-realtime-comparison-value',
      'admin-operational-dashboards-dashboard-kpi-today-send-value',
      'admin-operational-dashboards-dashboard-kpi-success-rate-value',
      'admin-operational-dashboards-dashboard-kpi-revenue-value',
    ]) {
      expect(screen.getByTestId(testId)).toHaveTextContent('—');
      expect(screen.getByTestId(testId)).not.toHaveTextContent('0');
    }
    expect(screen.getByTestId('admin-operational-dashboards-dashboard-realtime-send-trend')).toHaveTextContent('今日趋势暂不可用');
    expect(screen.getByTestId('admin-operational-dashboards-dashboard-kpi-tenant-rank')).toHaveTextContent('机构排行暂不可用');
    if (state === 'STALE') expect(status).toHaveTextContent('当前不展示消息统计值');
  });

  it('keeps stale last-known aggregate values visible with both freshness timestamps', async () => {
    vi.mocked(operationalApi.getPlatformDashboard).mockResolvedValue({
      realtime: { totalUsers: 3, todayMessages: 216, successRate: 0.875, activeTenants: 1, comparisonMessages: 200 },
      kpi: { todaySend: 216, activeTenants: 1, successRate: 0.875, todayRevenue: 9.5, formula: 'success_count/send_count' },
      hourlyTrend: [{ bucketStart: '2026-09-10T09:00:00', sendCount: 216, successCount: 189, successRate: 0.875 }],
      tenantRank: [{ tenantId: 7, sendCount: 216, successCount: 189, successRate: 0.875 }],
      channelHealth: { normal: 1, maintenance: 1, abnormal: 1 },
      financeWarning: { warningCount: 1, freshnessAt: '2026-09-10T09:06:00' },
      source: { registry: 'statistics_aggregates', formula: 'success_count/send_count', freshnessAt: '2026-09-10T09:05:00', permissionScope: 'PLATFORM', formulaVersion: 'v1' },
      todayAggregation: {
        state: 'STALE',
        businessDate: '2026-09-10',
        businessTimeZone: 'Asia/Shanghai',
        sourceRegistry: 'statistics_aggregates',
        refreshedAt: '2026-09-10T09:05:00',
        sourceChangedAt: '2026-09-10T09:09:00',
        sourceRecordCount: 216,
        aggregateRowCount: 2,
      },
    });

    renderWithProviders(<DashboardPage />);

    const status = screen.getByTestId('admin-operational-dashboards-dashboard-data-status');
    await waitFor(() => expect(status).toHaveAttribute('data-state', 'STALE'));
    expect(status).toHaveTextContent('数据已过期');
    expect(screen.getByTestId('admin-operational-dashboards-dashboard-realtime-today-messages-value')).toHaveTextContent('216');
    expect(screen.getByTestId('admin-operational-dashboards-dashboard-realtime-success-rate-value')).toHaveTextContent('87.50%');
    expect(screen.getByTestId('admin-operational-dashboards-dashboard-realtime-comparison-value')).toHaveTextContent('200');
    expect(screen.getByTestId('admin-operational-dashboards-dashboard-kpi-today-send-value')).toHaveTextContent('216');
    expect(screen.getByTestId('admin-operational-dashboards-dashboard-kpi-success-rate-value')).toHaveTextContent('88%');
    expect(screen.getByTestId('admin-operational-dashboards-dashboard-kpi-revenue-value')).toHaveTextContent('9.5');
    expect(screen.getByTestId('admin-operational-dashboards-dashboard-aggregation-freshness')).toHaveTextContent('2026-09-10T09:05:00');
    expect(screen.getByTestId('admin-operational-dashboards-dashboard-aggregation-freshness')).toHaveTextContent('2026-09-10T09:09:00');
    expect(screen.getByTestId('admin-operational-dashboards-dashboard-realtime-send-trend')).toHaveTextContent('09:00');
    expect(screen.getByTestId('admin-operational-dashboards-dashboard-kpi-tenant-rank')).toHaveTextContent('租户 7');
  });

  it('renders legitimate zero aggregate values when the server marks them fresh', async () => {
    vi.mocked(operationalApi.getPlatformDashboard).mockResolvedValue({
      realtime: { totalUsers: 3, todayMessages: 0, successRate: 0, activeTenants: 1, comparisonMessages: 0 },
      kpi: { todaySend: 0, activeTenants: 1, successRate: 0, todayRevenue: 0, formula: 'success_count/send_count' },
      hourlyTrend: [],
      tenantRank: [],
      channelHealth: { normal: 1, maintenance: 1, abnormal: 1 },
      financeWarning: { warningCount: 1, freshnessAt: '2026-09-10T09:06:00' },
      source: { registry: 'statistics_aggregates', formula: 'success_count/send_count', freshnessAt: '2026-09-10T09:05:00', permissionScope: 'PLATFORM', formulaVersion: 'v1' },
      todayAggregation: {
        state: 'FRESH',
        businessDate: '2026-09-10',
        businessTimeZone: 'Asia/Shanghai',
        sourceRegistry: 'statistics_aggregates',
        refreshedAt: '2026-09-10T09:05:00',
        sourceChangedAt: '2026-09-10T09:04:30',
        sourceRecordCount: 1,
        aggregateRowCount: 1,
      },
    });

    renderWithProviders(<DashboardPage />);

    await waitFor(() => expect(screen.getByTestId('admin-operational-dashboards-dashboard-data-status')).toHaveAttribute('data-state', 'FRESH'));
    expect(screen.getByTestId('admin-operational-dashboards-dashboard-realtime-today-messages-value')).toHaveTextContent('0');
    expect(screen.getByTestId('admin-operational-dashboards-dashboard-realtime-success-rate-value')).toHaveTextContent('0.00%');
    expect(screen.getByTestId('admin-operational-dashboards-dashboard-realtime-comparison-value')).toHaveTextContent('0');
    expect(screen.getByTestId('admin-operational-dashboards-dashboard-kpi-today-send-value')).toHaveTextContent('0');
    expect(screen.getByTestId('admin-operational-dashboards-dashboard-kpi-success-rate-value')).toHaveTextContent('0%');
    expect(screen.getByTestId('admin-operational-dashboards-dashboard-kpi-revenue-value')).toHaveTextContent('0');
  });

  it('keeps stale data after a refresh failure and replaces it after an explicit retry', async () => {
    type PlatformResponse = Awaited<ReturnType<typeof operationalApi.getPlatformDashboard>>;
    let rejectRefresh: ((reason?: unknown) => void) | undefined;
    const failedRefresh = new Promise<PlatformResponse>((_resolve, reject) => {
      rejectRefresh = reject;
    });
    let resolveRetry: ((value: PlatformResponse) => void) | undefined;
    const retried = new Promise<PlatformResponse>((resolve) => {
      resolveRetry = resolve;
    });
    vi.mocked(operationalApi.getPlatformDashboard)
      .mockResolvedValueOnce({
        realtime: { totalUsers: 3, todayMessages: 150, successRate: 0.9, activeTenants: 1, comparisonMessages: 140 },
        kpi: { todaySend: 150, activeTenants: 1, successRate: 0.9, todayRevenue: 10, formula: 'success_count/send_count' },
        hourlyTrend: [{ bucketStart: '2026-09-10T09:00:00', sendCount: 150, successCount: 135, successRate: 0.9 }],
        tenantRank: [{ tenantId: 7, sendCount: 150, successCount: 135, successRate: 0.9 }],
        channelHealth: { normal: 1, maintenance: 1, abnormal: 1 },
        financeWarning: { warningCount: 1, freshnessAt: '2026-09-10T09:05:00' },
        source: { registry: 'statistics_aggregates', formula: 'success_count/send_count', freshnessAt: '2026-09-10T09:05:00', permissionScope: 'PLATFORM', formulaVersion: 'v1' },
        todayAggregation: { state: 'STALE', businessDate: '2026-09-10', businessTimeZone: 'Asia/Shanghai', sourceRegistry: 'statistics_aggregates', refreshedAt: '2026-09-10T09:05:00', sourceChangedAt: '2026-09-10T09:09:00', sourceRecordCount: 150, aggregateRowCount: 2 },
      })
      .mockReturnValueOnce(failedRefresh)
      .mockReturnValueOnce(retried);
    renderWithProviders(<DashboardPage />);

    const status = screen.getByTestId('admin-operational-dashboards-dashboard-data-status');
    await waitFor(() => expect(status).toHaveAttribute('data-state', 'STALE'));
    const refresh = screen.getByTestId('admin-operational-dashboards-dashboard-realtime-refresh');
    fireEvent.click(refresh);
    await waitFor(() => expect(refresh).toBeDisabled());
    rejectRefresh?.(new Error('temporary unavailable'));

    expect(await screen.findByText('刷新失败，当前仍显示上一次加载的数据。')).toBeInTheDocument();
    expect(status).toHaveAttribute('data-state', 'STALE');
    expect(screen.getByTestId('admin-operational-dashboards-dashboard-realtime-today-messages-value')).toHaveTextContent('150');
    expect(refresh).toBeEnabled();

    fireEvent.click(refresh);
    await waitFor(() => expect(refresh).toBeDisabled());

    resolveRetry?.({
      realtime: { totalUsers: 3, todayMessages: 216, successRate: 1, activeTenants: 1, comparisonMessages: 200 },
      kpi: { todaySend: 216, activeTenants: 1, successRate: 1, todayRevenue: 12, formula: 'success_count/send_count' },
      hourlyTrend: [{ bucketStart: '2026-09-10T10:00:00', sendCount: 216, successCount: 216, successRate: 1 }],
      tenantRank: [{ tenantId: 7, sendCount: 216, successCount: 216, successRate: 1 }],
      channelHealth: { normal: 1, maintenance: 1, abnormal: 1 }, financeWarning: { warningCount: 1, freshnessAt: null },
      source: { registry: 'statistics_aggregates', formula: 'success_count/send_count', freshnessAt: '2026-09-10T10:05:00', permissionScope: 'PLATFORM', formulaVersion: 'v1' },
      todayAggregation: { state: 'FRESH', businessDate: '2026-09-10', businessTimeZone: 'Asia/Shanghai', sourceRegistry: 'statistics_aggregates', refreshedAt: '2026-09-10T10:05:00', sourceChangedAt: '2026-09-10T10:04:30', sourceRecordCount: 216, aggregateRowCount: 2 },
    });

    await waitFor(() => expect(status).toHaveAttribute('data-state', 'FRESH'));
    expect(screen.getByTestId('admin-operational-dashboards-dashboard-realtime-today-messages-value')).toHaveTextContent('216');
    expect(screen.getByTestId('admin-operational-dashboards-dashboard-aggregation-freshness')).toHaveTextContent('2026-09-10T10:05:00');
    expect(refresh).toBeEnabled();
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
    expect(screen.getByTestId('query-panel')).toBeVisible();
    vi.mocked(operationalApi.getDashboardConfiguration).mockClear();
    fireEvent.change(screen.getByTestId('admin-operational-dashboards-dashboard-configuration-role'), { target: { value: 'TENANT_ADMIN' } });
    expect(operationalApi.getDashboardConfiguration).not.toHaveBeenCalled();
    fireEvent.click(screen.getByTestId('query-submit'));
    await waitFor(() => expect(operationalApi.getDashboardConfiguration).toHaveBeenCalledWith('TENANT_ADMIN'));
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
