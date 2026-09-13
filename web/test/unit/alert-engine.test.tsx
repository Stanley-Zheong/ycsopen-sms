import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '@/api/alertEngineApi';
import AdminAlertsPage from '@/pages/admin/alerts/AdminAlertsPage';

vi.mock('@/api/alertEngineApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/alertEngineApi')>();
  return {
    ...actual,
    getAlertDashboard: vi.fn(),
    listAlertRules: vi.fn(),
    saveAlertRule: vi.fn(),
    listAlertHistory: vi.fn(),
    listAlertDeliveries: vi.fn(),
    evaluateAlertSource: vi.fn(),
    acknowledgeAlert: vi.fn(),
    resolveAlert: vi.fn(),
    muteAlert: vi.fn(),
  };
});

function renderWithQuery(ui: React.ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

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

describe('Phase 35 alert engine console UI', () => {
  beforeEach(() => {
    vi.mocked(api.getAlertDashboard).mockResolvedValue({ totalCount: 4, activeCount: 2, severeCount: 1, resolvedCount: 1 });
    vi.mocked(api.listAlertRules).mockResolvedValue([
      { id: 9, ruleName: '通道失败率告警', ruleType: 'FAILURE_RATE', metricName: 'FAILURE_RATE', metricSource: 'statistics_aggregates', thresholdValue: 0.1, comparisonOp: '>=', durationMinutes: 5, severity: 'CRITICAL', notifyChannels: '["SMS","EMAIL","DINGTALK","WECOM"]', notificationTargets: '["operations","finance","tenant:7"]', sourceScope: 'PLATFORM', status: 'ACTIVE', updatedAt: null },
    ]);
    vi.mocked(api.listAlertHistory).mockResolvedValue([alertRow]);
    vi.mocked(api.listAlertDeliveries).mockResolvedValue([
      { id: 1, alertRecordId: 501, channel: 'EMAIL', targetSnapshot: '["operations"]', providerResult: 'ACCEPTED', retryCount: 0, status: 'DELIVERED', failureReason: null, attemptedAt: '2026-09-10T10:01:00' },
    ]);
    vi.mocked(api.saveAlertRule).mockResolvedValue({ id: 10, ruleName: '通道失败率告警', ruleType: 'FAILURE_RATE', metricName: 'FAILURE_RATE', metricSource: 'statistics_aggregates', thresholdValue: 0.2, comparisonOp: '>=', durationMinutes: 5, severity: 'CRITICAL', notifyChannels: '["SMS","EMAIL"]', notificationTargets: '["operations"]', sourceScope: 'PLATFORM', status: 'ACTIVE', updatedAt: null });
    vi.mocked(api.evaluateAlertSource).mockResolvedValue({ alerts: [alertRow] });
    vi.mocked(api.acknowledgeAlert).mockResolvedValue({ ...alertRow, status: 'ACKNOWLEDGED', acknowledgedBy: 'operator' });
    vi.mocked(api.resolveAlert).mockResolvedValue({ ...alertRow, status: 'RESOLVED', resolvedBy: 'operator', resolutionNote: '确认来源已恢复' });
    vi.mocked(api.muteAlert).mockResolvedValue({ id: 1, scope: 'GLOBAL', reason: '运营临时静音', mutedBy: 'operator', mutedUntil: '2026-09-10T10:30:00' });
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('renders dashboard rules targets history actions and delivery evidence', async () => {
    renderWithQuery(<AdminAlertsPage />);

    expect(await screen.findByTestId('admin-alert-engine-dashboard-cards')).toHaveTextContent('严重告警');
    expect(await screen.findByTestId('admin-alert-engine-rules-page')).toBeVisible();
    expect(await screen.findByTestId('admin-alert-engine-notification-targets')).toHaveTextContent('SMS');
    expect(await screen.findByTestId('admin-alert-engine-alert-history')).toBeVisible();
    expect(await screen.findByTestId('admin-alert-engine-dashboard-alert-tabs')).toHaveTextContent('活跃');
    expect(await screen.findByTestId('admin-alert-engine-alert-row')).toHaveTextContent('通道失败率过高');
    expect(await screen.findByTestId('admin-alert-engine-alert-delivery-attempts')).toHaveTextContent('EMAIL');

    fireEvent.change(screen.getByTestId('admin-alert-engine-rule-threshold'), { target: { value: '0.2' } });
    fireEvent.click(screen.getByTestId('admin-alert-engine-rule-save'));
    await waitFor(() => expect(api.saveAlertRule).toHaveBeenCalledWith(expect.objectContaining({ thresholdValue: 0.2 })));
    fireEvent.click(screen.getByTestId('admin-alert-engine-source-evaluate'));
    await waitFor(() => expect(api.evaluateAlertSource).toHaveBeenCalledWith(expect.objectContaining({ metricName: 'FAILURE_RATE' })));
    fireEvent.click(screen.getByTestId('admin-alert-engine-alert-acknowledge'));
    await waitFor(() => expect(api.acknowledgeAlert).toHaveBeenCalledWith(501));
    expect(screen.queryByTestId('admin-alert-engine-resolve-reason')).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId('admin-alert-engine-alert-resolve'));
    expect(screen.getByTestId('admin-alert-engine-action-target')).toHaveTextContent('通道失败率过高');
    expect(screen.getByTestId('admin-alert-engine-resolve-reason')).toHaveAttribute('maxlength', '255');
    fireEvent.change(screen.getByTestId('admin-alert-engine-resolve-reason'), { target: { value: '确认来源已恢复' } });
    fireEvent.click(screen.getByTestId('admin-alert-engine-action-confirm'));
    await waitFor(() => expect(api.resolveAlert).toHaveBeenCalledWith(501, '确认来源已恢复'));
    fireEvent.click(screen.getByTestId('admin-alert-engine-alert-mute'));
    expect(screen.getByTestId('admin-alert-engine-action-target')).toHaveTextContent('全局告警通知');
    expect(screen.getByTestId('admin-alert-engine-action-target')).toHaveTextContent('由告警 #501');
    expect(screen.getByTestId('admin-alert-engine-action-consequence')).toHaveTextContent('30 分钟内所有新告警通知都会被全局抑制');
    fireEvent.change(screen.getByTestId('admin-alert-engine-mute-reason'), { target: { value: '运营临时静音' } });
    fireEvent.click(screen.getByTestId('admin-alert-engine-action-confirm'));
    await waitFor(() => expect(api.muteAlert).toHaveBeenCalledWith(501, 30, '运营临时静音'));
  });

  it('keeps a reason dialog intact when an unrelated alert mutation finishes', async () => {
    let finishEvaluation: ((value: { alerts: typeof alertRow[] }) => void) | undefined;
    vi.mocked(api.evaluateAlertSource).mockImplementation(() => new Promise((resolve) => {
      finishEvaluation = resolve;
    }));
    renderWithQuery(<AdminAlertsPage />);

    expect(await screen.findByTestId('admin-alert-engine-alert-row')).toBeVisible();
    fireEvent.click(screen.getByTestId('admin-alert-engine-source-evaluate'));
    await waitFor(() => expect(api.evaluateAlertSource).toHaveBeenCalled());
    fireEvent.click(screen.getByTestId('admin-alert-engine-alert-resolve'));
    fireEvent.change(screen.getByTestId('admin-alert-engine-resolve-reason'), { target: { value: '保留这份复核依据' } });

    finishEvaluation?.({ alerts: [alertRow] });
    expect(await screen.findByTestId('admin-alert-engine-message')).toHaveTextContent('告警评估完成');
    expect(screen.getByTestId('admin-alert-engine-action-dialog')).toBeVisible();
    expect(screen.getByTestId('admin-alert-engine-resolve-reason')).toHaveValue('保留这份复核依据');
  });
});
