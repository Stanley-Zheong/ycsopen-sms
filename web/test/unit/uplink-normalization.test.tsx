import { fireEvent, render, screen, waitFor } from '@testing-library/react';
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
    listUplinkTenantOptions: vi.fn(),
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
  tenantNo: 'TENANT-0007',
  tenantShortName: '北斗短信',
  tenantFullName: '北斗短信服务有限公司',
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
    vi.mocked(api.listUplinkTenantOptions).mockResolvedValue([
      { tenantId: 7, tenantNo: 'TENANT-0007', tenantShortName: '北斗短信', tenantFullName: '北斗短信服务有限公司' },
    ]);
    vi.mocked(api.listAdminUplinks).mockResolvedValue([uplink]);
    vi.mocked(api.getAdminUplink).mockResolvedValue(uplink);
    vi.mocked(api.replayAdminUplink).mockResolvedValue({ eventId: 501, tenantId: 7, state: 'DELIVERED', resultCode: 'HTTP_200', resultMessage: 'ok' });
    vi.mocked(api.listUplinkPushMonitor).mockResolvedValue([
      { eventId: 501, tenantId: 7, tenantNo: 'TENANT-0007', tenantShortName: '北斗短信', tenantFullName: '北斗短信服务有限公司', sourceId: 'UPLINK:101', logicalId: 'UPLINK:101', destinationUrl: 'https://callback.example.com/uplink', state: 'PUSH_FAILED', attemptCount: 5, maxAttempts: 5, attemptRows: 5, nextAttemptAt: null, updatedAt: '2026-09-10T10:01:00', latencyMs: 60000 },
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
    expect(screen.getByTestId('admin-uplink-normalization-uplinks-tenant-cell')).toHaveTextContent('北斗短信');
    expect(screen.getByTestId('admin-uplink-normalization-uplinks-tenant-cell')).toHaveTextContent('TENANT-0007');
    fireEvent.click(screen.getByTestId('admin-uplink-normalization-uplink-detail'));
    await waitFor(() => expect(api.getAdminUplink).toHaveBeenCalledWith(101));
    expect(await screen.findByTestId('admin-uplink-normalization-detail-drawer')).toHaveTextContent('回复帮助');
    expect(screen.getByTestId('admin-uplink-normalization-detail-tenant-identity')).toHaveTextContent('北斗短信服务有限公司');
    expect(screen.getByTestId('admin-uplink-normalization-detail-tenant-identity')).toHaveTextContent('内部 ID7');

    expect(screen.queryByTestId('admin-uplink-normalization-uplink-replay-reason')).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId('admin-uplink-normalization-uplink-replay'));
    expect(screen.getByTestId('admin-uplink-normalization-action-target')).toHaveTextContent('上行记录 #101');
    fireEvent.change(screen.getByTestId('admin-uplink-normalization-uplink-replay-reason'), { target: { value: '上行内容复核通过' } });
    fireEvent.click(screen.getByTestId('admin-uplink-normalization-action-confirm'));
    await waitFor(() => expect(api.replayAdminUplink).toHaveBeenCalledWith(101, '上行内容复核通过'));

    expect(await screen.findByTestId('admin-uplink-normalization-push-monitor-page')).toBeVisible();
    expect(screen.getByTestId('admin-uplink-normalization-push-monitor-tenant-cell')).toHaveTextContent('北斗短信');
    fireEvent.click(screen.getByTestId('admin-uplink-normalization-push-pause'));
    expect(screen.getByTestId('admin-uplink-normalization-action-target')).toHaveTextContent('UPLINK:101');
    fireEvent.change(screen.getByTestId('admin-uplink-normalization-push-action-reason'), { target: { value: '目的地维护暂停' } });
    fireEvent.click(screen.getByTestId('admin-uplink-normalization-action-confirm'));
    await waitFor(() => expect(api.pauseUplinkPushEvent).toHaveBeenCalledWith(501, '目的地维护暂停'));
    fireEvent.click(screen.getByTestId('admin-uplink-normalization-push-resume'));
    fireEvent.change(screen.getByTestId('admin-uplink-normalization-push-action-reason'), { target: { value: '维护完成恢复' } });
    fireEvent.click(screen.getByTestId('admin-uplink-normalization-action-confirm'));
    await waitFor(() => expect(api.resumeUplinkPushEvent).toHaveBeenCalledWith(501, '维护完成恢复'));
  });

  it('resolves institution names and numbers to stable tenant IDs for both queries', async () => {
    vi.mocked(api.listUplinkTenantOptions).mockImplementation(async (query) => {
      const candidates = [
        { tenantId: 7, tenantNo: 'TENANT-0007', tenantShortName: '北斗短信', tenantFullName: '北斗短信服务有限公司' },
        { tenantId: 9, tenantNo: 'TENANT-0009', tenantShortName: '南方短信', tenantFullName: '南方短信服务有限公司' },
      ];
      const normalized = query.toLocaleLowerCase('zh-CN');
      return candidates.filter((candidate) => !normalized || [
        candidate.tenantNo,
        candidate.tenantShortName,
        candidate.tenantFullName,
      ].some((value) => value.toLocaleLowerCase('zh-CN').includes(normalized)));
    });
    renderWithQuery(<AdminUplinksPage />);

    await waitFor(() => expect(api.listUplinkTenantOptions).toHaveBeenCalled());
    fireEvent.change(screen.getByTestId('admin-uplink-normalization-uplinks-filter-tenant'), { target: { value: '北斗短信' } });
    await waitFor(() => expect(api.listUplinkTenantOptions).toHaveBeenCalledWith('北斗短信'));
    fireEvent.change(screen.getByTestId('admin-uplink-normalization-push-filter-tenant'), { target: { value: '南方短信' } });
    await waitFor(() => expect(api.listUplinkTenantOptions).toHaveBeenCalledWith('南方短信'));
    await waitFor(() => expect(screen.getByTestId('admin-uplink-normalization-tenant-filter-feedback')).toHaveTextContent('可按机构简称'));
    fireEvent.click(screen.getAllByTestId('query-submit')[0]);
    await waitFor(() => expect(api.listAdminUplinks).toHaveBeenLastCalledWith(expect.objectContaining({ tenantId: '7' })));

    fireEvent.click(screen.getAllByTestId('query-submit')[1]);
    await waitFor(() => expect(api.listUplinkPushMonitor).toHaveBeenLastCalledWith(expect.objectContaining({ tenantId: '9' })));
  });

  it('keeps the internal ID visible when tenant metadata is unavailable', async () => {
    const missing = { ...uplink, tenantId: 8, tenantNo: null, tenantShortName: null, tenantFullName: null };
    vi.mocked(api.listAdminUplinks).mockResolvedValue([missing]);
    vi.mocked(api.getAdminUplink).mockResolvedValue(missing);
    vi.mocked(api.listUplinkPushMonitor).mockResolvedValue([{
      eventId: 502, tenantId: 8, tenantNo: null, tenantShortName: null, tenantFullName: null,
      sourceId: 'UPLINK:102', logicalId: 'UPLINK:102', destinationUrl: 'https://callback.example.com/uplink',
      state: 'PUSH_FAILED', attemptCount: 1, maxAttempts: 5, attemptRows: 1, nextAttemptAt: null,
      updatedAt: '2026-09-10T10:01:00', latencyMs: 60000,
    }]);

    renderWithQuery(<AdminUplinksPage />);

    expect(await screen.findByTestId('admin-uplink-normalization-uplinks-tenant-cell')).toHaveTextContent('机构 ID 8');
    expect(screen.getByTestId('admin-uplink-normalization-push-monitor-tenant-cell')).toHaveTextContent('机构 ID 8');
    fireEvent.click(screen.getByTestId('admin-uplink-normalization-uplink-detail'));
    expect(await screen.findByTestId('admin-uplink-normalization-detail-tenant-identity')).toHaveTextContent('名称不可用');
    expect(screen.getByTestId('admin-uplink-normalization-detail-tenant-identity')).toHaveTextContent('内部 ID8');
  });

  it('blocks ambiguous display text and preserves numeric-ID filtering if option lookup fails', async () => {
    vi.mocked(api.listUplinkTenantOptions).mockResolvedValue([
      { tenantId: 7, tenantNo: 'TENANT-0007', tenantShortName: '同名机构', tenantFullName: '同名机构一公司' },
      { tenantId: 9, tenantNo: 'TENANT-0009', tenantShortName: '同名机构', tenantFullName: '同名机构二公司' },
    ]);
    renderWithQuery(<AdminUplinksPage />);
    await waitFor(() => expect(api.listUplinkTenantOptions).toHaveBeenCalled());

    fireEvent.change(screen.getByTestId('admin-uplink-normalization-uplinks-filter-tenant'), { target: { value: '同名机构' } });
    await waitFor(() => expect(api.listUplinkTenantOptions).toHaveBeenCalledWith('同名机构'));
    fireEvent.click(screen.getAllByTestId('query-submit')[0]);
    expect(await screen.findByTestId('admin-uplink-normalization-tenant-filter-feedback')).toHaveTextContent('匹配到多个机构');
    fireEvent.click(screen.getAllByTestId('query-submit')[0]);
    expect(api.listAdminUplinks).toHaveBeenCalledTimes(1);

    vi.mocked(api.listUplinkTenantOptions).mockRejectedValue(new Error('lookup unavailable'));
    fireEvent.change(screen.getByTestId('admin-uplink-normalization-uplinks-filter-tenant'), { target: { value: '8' } });
    await waitFor(() => expect(api.listUplinkTenantOptions).toHaveBeenCalledWith('8'));
    fireEvent.click(screen.getAllByTestId('query-submit')[0]);
    await waitFor(() => expect(api.listAdminUplinks).toHaveBeenLastCalledWith(expect.objectContaining({ tenantId: '8' })));
  });

  it('applies a tenant resolved after a blocked loading submission', async () => {
    let resolveLookup: ((options: api.UplinkTenantOption[]) => void) | undefined;
    vi.mocked(api.listUplinkTenantOptions).mockImplementation(async (query) => {
      if (query !== '延迟机构') {
        return [{ tenantId: 7, tenantNo: 'TENANT-0007', tenantShortName: '北斗短信', tenantFullName: '北斗短信服务有限公司' }];
      }
      return new Promise((resolve) => { resolveLookup = resolve; });
    });
    renderWithQuery(<AdminUplinksPage />);
    await waitFor(() => expect(api.listUplinkTenantOptions).toHaveBeenCalled());

    fireEvent.change(screen.getByTestId('admin-uplink-normalization-uplinks-filter-tenant'), { target: { value: '延迟机构' } });
    await waitFor(() => expect(api.listUplinkTenantOptions).toHaveBeenCalledWith('延迟机构'));
    fireEvent.click(screen.getAllByTestId('query-submit')[0]);
    expect(await screen.findByTestId('admin-uplink-normalization-tenant-filter-feedback')).toHaveTextContent('正在查找机构');
    expect(api.listAdminUplinks).toHaveBeenCalledTimes(1);

    resolveLookup?.([{ tenantId: 7, tenantNo: 'TENANT-0007', tenantShortName: '延迟机构', tenantFullName: '延迟机构有限公司' }]);
    await waitFor(() => expect(screen.getByTestId('admin-uplink-normalization-tenant-filter-feedback')).toHaveTextContent('可按机构简称'));
    fireEvent.click(screen.getAllByTestId('query-submit')[0]);
    await waitFor(() => expect(api.listAdminUplinks).toHaveBeenLastCalledWith(expect.objectContaining({ tenantId: '7' })));
  });

  it('shows tenant scoped uplinks and saves audited auto reply config', async () => {
    renderWithQuery(<TenantUplinksPage />);

    expect(await screen.findByTestId('tenant-uplinks')).toBeVisible();
    expect(await screen.findByTestId('tenant-uplink-normalization-uplinks-row')).toHaveTextContent('帮助');
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

  it('refreshes only the tenant uplink query and exposes its result state', async () => {
    renderWithQuery(<TenantUplinksPage />);

    await waitFor(() => expect(screen.getByTestId('tenant-uplink-normalization-uplinks-query-status')).toHaveAttribute('data-state', 'success'));
    await waitFor(() => expect(api.getTenantUplinkAutoReply).toHaveBeenCalledTimes(1));
    fireEvent.click(screen.getByTestId('query-refresh'));

    await waitFor(() => expect(api.listTenantUplinks).toHaveBeenCalledTimes(2));
    expect(api.getTenantUplinkAutoReply).toHaveBeenCalledTimes(1);
  });
});
