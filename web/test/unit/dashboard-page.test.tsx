import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchComplaintRatio } from '@/api/dashboard';
import { getPlatformDashboard } from '@/api/operationalDashboardApi';
import DashboardPage from '@/pages/admin/dashboard/DashboardPage';

vi.mock('@/api/dashboard', () => ({ fetchComplaintRatio: vi.fn() }));
vi.mock('@/api/operationalDashboardApi', () => ({ getPlatformDashboard: vi.fn() }));

const platformDashboard = {
  realtime: { totalUsers: 12, todayMessages: 320, successRate: 0.975, activeTenants: 7, comparisonMessages: 240 },
  kpi: { todaySend: 320, activeTenants: 7, successRate: 0.975, todayRevenue: 1280, formula: 'send*price' },
  hourlyTrend: [
    { bucketStart: '2026-10-04T00:00:00', sendCount: 100, successCount: 98, successRate: 0.98 },
    { bucketStart: '2026-10-04T01:00:00', sendCount: 220, successCount: 210, successRate: 0.9545 },
  ],
  tenantRank: [
    { tenantId: 1, sendCount: 220, successCount: 210, successRate: 0.9545 },
    { tenantId: 2, sendCount: 100, successCount: 98, successRate: 0.98 },
  ],
  channelHealth: { normal: 2, maintenance: 1, abnormal: 1 },
  financeWarning: { warningCount: 3, freshnessAt: '2026-10-04T03:40:00' },
  source: {
    registry: 'operational_dashboard',
    formula: 'real rows',
    freshnessAt: '2026-10-04T03:40:00',
    permissionScope: 'PLATFORM',
    formulaVersion: 'v1',
  },
};

describe('admin dashboard release boundary', () => {
  beforeEach(() => {
    vi.mocked(fetchComplaintRatio).mockResolvedValue([]);
    vi.mocked(getPlatformDashboard).mockResolvedValue(platformDashboard);
  });

  it('exposes the stable dashboard page selector used by release acceptance', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <DashboardPage />
      </QueryClientProvider>,
    );

    expect(await screen.findByTestId('admin-dashboard-page')).toBeVisible();
    expect(await screen.findByTestId('admin-operational-dashboards-dashboard-realtime-chart')).toBeVisible();
    expect(screen.getByTestId('admin-operational-dashboards-dashboard-finance-warning-chart')).toBeVisible();
    expect(screen.getByTestId('shared-operational-dashboards-metric-source')).toHaveTextContent('operational_dashboard');
  });
});
