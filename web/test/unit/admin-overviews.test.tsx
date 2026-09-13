import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import AdminToolsOverviewPage from '@/pages/admin/tools/AdminToolsOverviewPage';
import AdminStatisticsOverviewPage from '@/pages/admin/dashboard/AdminStatisticsOverviewPage';
import ResourceReviewHistoryPage from '@/pages/admin/review/ResourceReviewHistoryPage';
import * as dashboardApi from '@/api/operationalDashboardApi';
import * as reviewApi from '@/api/resourceReviewHistoryApi';
import { useAuthStore } from '@/store/authStore';

vi.mock('@/api/operationalDashboardApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/operationalDashboardApi')>()),
  getResourceStatistics: vi.fn(),
}));
vi.mock('@/api/resourceReviewHistoryApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/resourceReviewHistoryApi')>()),
  listResourceReviewHistory: vi.fn(),
}));

function renderPage(ui: React.ReactElement) {
  return render(<MemoryRouter><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider></MemoryRouter>);
}

describe('admin overview and list surfaces', () => {
  it('renders tools overview actions instead of a placeholder', () => {
    renderPage(<AdminToolsOverviewPage />);
    expect(screen.getByTestId('admin-tools-overview-page')).toBeVisible();
    expect(screen.getByTestId('admin-tools-overview-table')).toContainElement(screen.getByText('号码归属与携号转网'));
  });

  it('renders statistics overview with a real data table', async () => {
    vi.mocked(dashboardApi.getResourceStatistics).mockResolvedValue({
      resources: [{ tenantId: 7, signatureId: 1, templateId: 2, submitCount: 10, successCount: 9, rejectedCount: 1, freshnessAt: null }],
      channelComparisons: [], accessibleColumns: ['tenant_id'], empty: false, errorState: 'NONE',
      source: { registry: 'statistics_aggregates', formula: 'success/send', freshnessAt: null, permissionScope: 'PLATFORM', formulaVersion: 'v1' },
    });
    renderPage(<AdminStatisticsOverviewPage />);
    expect(await screen.findByText('10')).toBeVisible();
  });

  it('keeps the review history table header visible when there are no rows', async () => {
    useAuthStore.setState({ userType: 'ADMIN', accessToken: 'test', tenantId: null, expiresAt: Date.now() + 60000, principalKey: 'test' });
    vi.mocked(reviewApi.listResourceReviewHistory).mockResolvedValue([]);
    renderPage(<ResourceReviewHistoryPage />);
    expect(await screen.findByTestId('admin-resource-review-history-review-table')).toContainElement(screen.getByText('决定'));
  });
});
