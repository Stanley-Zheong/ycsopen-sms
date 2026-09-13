import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactElement } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as dashboardApi from '@/api/dashboard';
import ComplaintRatioPanel from '@/pages/admin/dashboard/ComplaintRatioPanel';

vi.mock('@/api/dashboard', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/dashboard')>();
  return {
    ...actual,
    fetchComplaintRatio: vi.fn(),
    fetchComplaintRatioCases: vi.fn(),
    pauseComplaintRatioTarget: vi.fn(),
  };
});

function renderWithProviders(ui: ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

describe('Phase 45 complaint ratio intervention UI', () => {
  beforeEach(() => {
    vi.mocked(dashboardApi.fetchComplaintRatio).mockResolvedValue([
      {
        statMonth: '2026-08',
        dimensionType: 'CHANNEL',
        dimensionId: 11,
        dimensionName: '移动主通道',
        sendCount: 1000,
        complaintCount: 3,
        ratio: 0.003,
        thresholdValue: 0.003,
        thresholdConfigVersion: 'default-v1',
        overThreshold: true,
        dataQuality: 'COMPLETE',
        thresholdResult: 'BREACHED',
        sourceRegistry: 'complaint_ratio_stats:message_tasks:complaints',
        freshnessPolicy: 'T_PLUS_1_DAILY',
        calculatedAt: '2026-08-02T01:00:00',
        rank: 1,
        interventionAvailable: true,
        alertSourceKey: 'complaint-ratio:CHANNEL:11:2026-08:default-v1',
      },
      {
        statMonth: '2026-08',
        dimensionType: 'CHANNEL',
        dimensionId: 12,
        dimensionName: '未知归因通道',
        sendCount: 0,
        complaintCount: 1,
        ratio: 0,
        thresholdValue: 0.003,
        thresholdConfigVersion: 'default-v1',
        overThreshold: false,
        dataQuality: 'UNKNOWN',
        thresholdResult: 'UNKNOWN',
        sourceRegistry: 'complaint_ratio_stats:message_tasks:complaints',
        freshnessPolicy: 'T_PLUS_1_DAILY',
        calculatedAt: '2026-08-02T01:00:00',
        rank: 2,
        interventionAvailable: false,
        alertSourceKey: 'complaint-ratio:CHANNEL:12:2026-08:default-v1',
      },
    ]);
    vi.mocked(dashboardApi.fetchComplaintRatioCases).mockResolvedValue([
      {
        id: 91,
        source: 'REGULATOR',
        tenantId: 7,
        channelId: 11,
        messageId: 'MSG-91',
        summary: '监管投诉',
        status: 'PENDING',
        attributionQuality: 'COMPLETE',
        createdAt: '2026-08-02T10:00:00',
      },
    ]);
    vi.mocked(dashboardApi.pauseComplaintRatioTarget).mockResolvedValue({
      dimensionType: 'CHANNEL',
      dimensionId: 11,
      status: 'PAUSED',
      evidenceId: 5,
      alertRecordId: null,
      sourceKey: 'complaint-ratio:CHANNEL:11:2026-08:default-v1',
      action: 'PAUSE',
    });
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('renders threshold period drilldown and disables intervention for unknown data', async () => {
    renderWithProviders(<ComplaintRatioPanel dimension="channel" title="每通道当月投诉占比" />);

    expect(await screen.findByTestId('admin-complaint-ratio-dashboard-complaint-ratio-channel'))
      .toHaveTextContent('移动主通道');
    expect(screen.getByTestId('admin-complaint-ratio-dashboard-complaint-ratio-threshold')).toHaveTextContent('3.00‰');
    expect(screen.getByTestId('admin-complaint-ratio-dashboard-complaint-ratio-threshold')).toHaveTextContent('当前显示超阈值：1');
    expect(screen.getByTestId('admin-complaint-ratio-dashboard-complaint-ratio-period')).toHaveTextContent('显示全部');
    const unknownRow = screen.getByRole('row', { name: /未知归因通道/ });
    expect(unknownRow).toHaveTextContent('待确认');
    expect(within(unknownRow).getByRole('button', { name: '暂停通道' })).toBeDisabled();

    const breachedRow = screen.getByRole('row', { name: /移动主通道/ });
    fireEvent.click(within(breachedRow).getByRole('button', { name: '钻取' }));
    await waitFor(() => expect(screen.getByTestId('admin-complaint-ratio-dashboard-complaint-ratio-drilldown')).toHaveTextContent('MSG-91'));
    fireEvent.click(within(breachedRow).getByRole('button', { name: '暂停通道' }));
    const confirm = await screen.findByTestId('admin-complaint-ratio-dashboard-complaint-ratio-intervention-confirm');
    expect(confirm).toHaveTextContent('确认暂停通道');
    const reason = screen.getByTestId('admin-complaint-ratio-dashboard-complaint-ratio-intervention-reason');
    fireEvent.change(reason, { target: { value: '' } });
    expect(within(confirm).getByRole('button', { name: '确认暂停通道' })).toBeDisabled();
    fireEvent.change(reason, { target: { value: ' 投诉率超阈值人工确认 ' } });
    fireEvent.click(within(confirm).getByRole('button', { name: '确认暂停通道' }));

    await waitFor(() => expect(dashboardApi.pauseComplaintRatioTarget).toHaveBeenCalledWith(
      'channel',
      11,
      expect.stringMatching(/^\d{4}-\d{2}$/),
      '投诉率超阈值人工确认',
    ));
    expect(await screen.findByRole('status')).toHaveTextContent('complaint-ratio:CHANNEL:11:2026-08:default-v1');
  });
});
