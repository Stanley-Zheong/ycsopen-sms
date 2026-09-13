import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '@/api/tenantRiskAutoPauseApi';
import AdminTenantRiskPage from '@/pages/admin/risk/AdminTenantRiskPage';

vi.mock('@/api/tenantRiskAutoPauseApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/tenantRiskAutoPauseApi')>();
  return {
    ...actual,
    listTenantRiskRules: vi.fn(),
    saveTenantRiskRule: vi.fn(),
    evaluateTenantRisk: vi.fn(),
    listTenantRiskEpisodes: vi.fn(),
    recoverTenantRiskEpisode: vi.fn(),
  };
});

function renderWithQuery(ui: React.ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

const episode: api.TenantRiskEpisode = {
  id: 2,
  tenantId: 7,
  ruleId: 1,
  alertRecordId: 3,
  metric: 'FAILURE_RATE',
  sourceKey: 'failure-window-7',
  sourceRegistry: 'statistics_aggregates',
  numerator: 25,
  denominator: 100,
  rate: 0.25,
  thresholdValue: 0.2,
  windowMinutes: 15,
  dataQuality: 'COMPLETE',
  action: 'AUTO_SUSPEND',
  status: 'PAUSED',
  beforeLifecycleStatus: 'SIGNED',
  sourceSnapshot: 'numerator=25,denominator=100,rate=0.250000',
  recoveryReviewId: null,
  recoveredBy: null,
};

describe('Phase 42 tenant risk warning and auto pause UI', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.listTenantRiskRules).mockResolvedValue([{
      id: 1,
      ruleName: '失败率封停',
      tenantId: 7,
      metric: 'FAILURE_RATE',
      thresholdValue: 0.2,
      durationMinutes: 15,
      action: 'AUTO_SUSPEND',
      notifyTargets: '["tenant:7","ops"]',
      status: 'ACTIVE',
      updatedAt: '2026-09-10T12:00:00',
    }]);
    vi.mocked(api.listTenantRiskEpisodes).mockResolvedValue([episode]);
    vi.mocked(api.saveTenantRiskRule).mockResolvedValue({ id: 1 } as api.TenantRiskRule);
    vi.mocked(api.evaluateTenantRisk).mockResolvedValue({ episodeId: 2, dataQuality: 'COMPLETE', rate: 0.25, paused: true });
    vi.mocked(api.recoverTenantRiskEpisode).mockResolvedValue({ ...episode, status: 'RESOLVED', recoveryReviewId: 'review-42' });
  });

  it('configures rules evaluates source snapshot and recovers paused episode', async () => {
    renderWithQuery(<AdminTenantRiskPage />);

    expect(await screen.findByTestId('admin-tenant-risk-rules-page')).toBeVisible();
    expect(screen.getByTestId('admin-tenant-risk-notify-targets')).toHaveValue('["tenant:7","ops"]');
    await waitFor(() => expect(screen.getByTestId('admin-tenant-risk-tenant-risk-pause-detail')).toHaveTextContent('PAUSED'));
    expect(screen.getByTestId('admin-tenant-risk-source-snapshot')).toHaveTextContent('numerator=25');

    fireEvent.click(screen.getByTestId('admin-tenant-risk-rule-save'));
    await waitFor(() => expect(api.saveTenantRiskRule).toHaveBeenCalledWith(expect.objectContaining({
      metric: 'FAILURE_RATE',
      durationMinutes: 15,
      action: 'AUTO_SUSPEND',
    })));
    fireEvent.click(screen.getByTestId('admin-tenant-risk-evaluate'));
    await waitFor(() => expect(api.evaluateTenantRisk).toHaveBeenCalledWith(expect.objectContaining({
      tenantId: 7,
      denominator: 100,
      sourceRegistry: 'statistics_aggregates',
    })));
    fireEvent.click(screen.getByTestId('admin-tenant-risk-recover'));
    await waitFor(() => expect(api.recoverTenantRiskEpisode).toHaveBeenCalledWith(2, {
      reviewId: 'review-42',
      note: '来源复核通过，恢复提交',
    }));
  });

  it('shows unknown instead of safe rate when denominator is zero', async () => {
    vi.mocked(api.listTenantRiskEpisodes).mockResolvedValue([{ ...episode, denominator: 0, rate: null, dataQuality: 'UNKNOWN', status: 'UNKNOWN' }]);
    renderWithQuery(<AdminTenantRiskPage />);

    await waitFor(() => expect(screen.getByTestId('admin-tenant-risk-data-quality')).toHaveTextContent('UNKNOWN'));
    expect(screen.getByTestId('admin-tenant-risk-rate')).toHaveTextContent('未知');
  });
});
