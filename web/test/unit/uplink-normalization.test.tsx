import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '@/api/uplinkNormalizationApi';
import AdminUplinksPage from '@/pages/admin/uplinks/AdminUplinksPage';
import TenantUplinksPage from '@/pages/tenant/uplinks/TenantUplinksPage';

vi.mock('@/api/uplinkNormalizationApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/uplinkNormalizationApi')>();
  return {
    ...actual,
    listAdminUplinks: vi.fn(),
    getAdminUplink: vi.fn(),
    replayAdminUplink: vi.fn(),
    listUplinkPushMonitor: vi.fn(),
    replayUplinkPushEvent: vi.fn(),
    pauseUplinkPushEvent: vi.fn(),
    resumeUplinkPushEvent: vi.fn(),
    listTenantUplinks: vi.fn(),
    getTenantUplinkAutoReply: vi.fn(),
    saveTenantUplinkAutoReply: vi.fn(),
  };
});

function renderWithQuery(ui: React.ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

const uplink = {
  id: 101,
  tenantId: 7,
  sourceProtocol: 'HTTP',
  sourceConnector: 'HTTP-API',
  sourceEventId: 'HTTP-UP-1',
  messageId: 'MSG-1',
  phoneMasked: '138****8000',
  content: '回复帮助',
  contentKeyword: '帮助',
  state: 'NORMALIZED',
  carrier: 'CMCC',
  province: '北京',
  city: '北京',
  destination: 'https://callback.example.com/uplink',
  channelId: 3,
  signatureId: 4,
  productCode: 'STANDARD',
  pushState: 'PUSH_FAILED',
  pushEventId: 501,
  receiveTime: '2026-09-10T10:00:00',
  createdAt: '2026-09-10T10:00:00',
  updatedAt: '2026-09-10T10:01:00',
};

describe('Phase 32 uplink normalization UI', () => {
  beforeEach(() => {
    vi.mocked(api.listAdminUplinks).mockResolvedValue([uplink]);
    vi.mocked(api.getAdminUplink).mockResolvedValue(uplink);
    vi.mocked(api.replayAdminUplink).mockResolvedValue({ eventId: 501, tenantId: 7, state: 'DELIVERED', resultCode: 'HTTP_200', resultMessage: 'ok' });
    vi.mocked(api.listUplinkPushMonitor).mockResolvedValue([
      { eventId: 501, tenantId: 7, sourceId: 'UPLINK:101', logicalId: 'UPLINK:101', destinationUrl: 'https://callback.example.com/uplink', state: 'PUSH_FAILED', attemptCount: 5, maxAttempts: 5, attemptRows: 5, nextAttemptAt: null, updatedAt: '2026-09-10T10:01:00', latencyMs: 60000 },
    ]);
    vi.mocked(api.replayUplinkPushEvent).mockResolvedValue({ eventId: 501, tenantId: 7, state: 'DELIVERED', resultCode: 'HTTP_200', resultMessage: 'ok' });
    vi.mocked(api.pauseUplinkPushEvent).mockResolvedValue({ eventId: 501, tenantId: 7, state: 'PAUSED', resultCode: 'PAUSED', resultMessage: 'operator' });
    vi.mocked(api.resumeUplinkPushEvent).mockResolvedValue({ eventId: 501, tenantId: 7, state: 'RETRY', resultCode: 'RESUMED', resultMessage: 'operator' });
    vi.mocked(api.listTenantUplinks).mockResolvedValue([uplink]);
    vi.mocked(api.getTenantUplinkAutoReply).mockResolvedValue({ tenantId: 7, enabled: true, keyword: '帮助', templateId: 'TPL-1', responseContent: '收到', loopGuardMinutes: 30, auditReason: '租户配置上行自动回复', updatedBy: '7', updatedAt: '2026-09-10T10:00:00' });
    vi.mocked(api.saveTenantUplinkAutoReply).mockResolvedValue({ tenantId: 7, enabled: true, keyword: '帮助', templateId: 'TPL-1', responseContent: '收到', loopGuardMinutes: 30, auditReason: '更新', updatedBy: '7', updatedAt: '2026-09-10T10:02:00' });
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('shows admin uplinks with detail replay and push monitor actions', async () => {
    renderWithQuery(<AdminUplinksPage />);

    expect(await screen.findByTestId('admin-uplink-normalization-uplinks-page')).toBeVisible();
    expect(await screen.findByTestId('admin-uplink-normalization-uplinks-row')).toHaveTextContent('138****8000');
    const panels = screen.getAllByTestId('query-panel');
    expect(panels).toHaveLength(2);
    const uplinkPanel = panels[0];
    expect(within(uplinkPanel).getByTestId('query-panel-fields')).not.toBeVisible();
    fireEvent.click(within(uplinkPanel).getByTestId('query-panel-toggle'));
    vi.mocked(api.listAdminUplinks).mockClear();
    fireEvent.change(screen.getByTestId('admin-uplink-normalization-uplinks-filter-keyword'), { target: { value: '帮助' } });
    expect(api.listAdminUplinks).not.toHaveBeenCalled();
    fireEvent.click(within(uplinkPanel).getByTestId('query-submit'));
    await waitFor(() => expect(api.listAdminUplinks).toHaveBeenCalledWith(expect.objectContaining({ keyword: '帮助' })));
    fireEvent.click(within(uplinkPanel).getByTestId('query-reset'));
    expect(screen.getByTestId('admin-uplink-normalization-uplinks-filter-keyword')).toHaveValue('');
    expect(within(uplinkPanel).getByTestId('query-result-table')).toHaveTextContent('帮助');
    fireEvent.click(screen.getByTestId('admin-uplink-normalization-uplink-detail'));
    await waitFor(() => expect(api.getAdminUplink).toHaveBeenCalledWith(101));
    expect(await screen.findByTestId('admin-uplink-normalization-detail-drawer')).toHaveTextContent('回复帮助');

    fireEvent.click(screen.getByTestId('admin-uplink-normalization-uplink-replay'));
    await waitFor(() => expect(api.replayAdminUplink).toHaveBeenCalledWith(101, '运营复核后处理'));

    expect(await screen.findByTestId('admin-uplink-normalization-push-monitor-page')).toBeVisible();
    fireEvent.click(screen.getByTestId('admin-uplink-normalization-push-pause'));
    await waitFor(() => expect(api.pauseUplinkPushEvent).toHaveBeenCalledWith(501, '运营复核后处理'));
    fireEvent.click(screen.getByTestId('admin-uplink-normalization-push-resume'));
    await waitFor(() => expect(api.resumeUplinkPushEvent).toHaveBeenCalledWith(501, '运营复核后处理'));
  });

  it('shows tenant scoped uplinks and saves audited auto reply config', async () => {
    renderWithQuery(<TenantUplinksPage />);

    expect(await screen.findByTestId('tenant-uplinks')).toBeVisible();
    expect(await screen.findByTestId('tenant-uplink-normalization-uplinks-row')).toHaveTextContent('帮助');
    const panel = screen.getByTestId('query-panel');
    expect(within(panel).getByTestId('query-panel-fields')).not.toBeVisible();
    fireEvent.click(within(panel).getByTestId('query-panel-toggle'));
    vi.mocked(api.listTenantUplinks).mockClear();
    fireEvent.change(screen.getByTestId('tenant-uplink-normalization-uplinks-filter-number'), { target: { value: '13800138000' } });
    expect(api.listTenantUplinks).not.toHaveBeenCalled();
    fireEvent.click(within(panel).getByTestId('query-submit'));
    await waitFor(() => expect(api.listTenantUplinks).toHaveBeenCalledWith(expect.objectContaining({ phoneNumber: '13800138000' })));
    fireEvent.click(within(panel).getByTestId('query-reset'));
    expect(screen.getByTestId('tenant-uplink-normalization-uplinks-filter-number')).toHaveValue('');
    await waitFor(() => expect(api.listTenantUplinks).toHaveBeenCalledWith({ phoneNumber: '', keyword: '', carrier: '', pushState: '' }));
    expect(within(panel).getByTestId('query-result-table')).toHaveTextContent('帮助');
    expect(await screen.findByTestId('tenant-uplink-normalization-uplinks-auto-reply-config')).toBeVisible();
    fireEvent.change(screen.getByTestId('tenant-uplink-normalization-auto-reply-audit-reason'), { target: { value: '更新' } });
    fireEvent.click(screen.getByTestId('tenant-uplink-normalization-auto-reply-save'));
    await waitFor(() => expect(api.saveTenantUplinkAutoReply).toHaveBeenCalledWith(expect.objectContaining({
      enabled: true,
      keyword: '帮助',
      loopGuardMinutes: 30,
      auditReason: '更新',
    })));
  });
});
