import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { apiClient } from '@/api/client';
import type { ChannelHealthMonitorRow, ChannelPool } from '@/api/channelHealthApi';
import ChannelHealthPage from '@/pages/admin/channels/ChannelHealthPage';
import ChannelPoolsPage from '@/pages/admin/channels/ChannelPoolsPage';
import { useAuthStore } from '@/store/authStore';

type SeenRequest = { method: string; url: string; body: unknown };

function apiResponse<T>(data: T) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-08T00:00:00Z', traceId: 'trace-p11' };
}

function axiosResponse(config: InternalAxiosRequestConfig, data: unknown): AxiosResponse {
  return { config, data, headers: {}, status: 200, statusText: 'OK' };
}

function monitorRow(overrides: Partial<ChannelHealthMonitorRow> = {}): ChannelHealthMonitorRow {
  return {
    channelId: 1101,
    channelName: 'p11-cmpp-main',
    protocol: 'CMPP',
    operator: 'MOBILE',
    status: 'NORMAL',
    healthState: 'HEALTHY',
    timeoutRate: '0.0100',
    failureRate: '0.0200',
    averageLatencyMs: 88,
    reasonCode: null,
    candidateEligible: true,
    candidateReasonCode: 'ELIGIBLE',
    eventCount: 0,
    pauseReason: null,
    pausedBy: null,
    pausedAt: null,
    ...overrides,
  };
}

function pool(overrides: Partial<ChannelPool> = {}): ChannelPool {
  return {
    id: 501,
    name: 'p11-weighted',
    mode: 'WEIGHTED',
    version: 3,
    status: 'ACTIVE',
    members: [
      { channelId: 1101, weight: 70, primaryMember: true, enabled: true },
      { channelId: 1102, weight: 30, primaryMember: false, enabled: true },
    ],
    ...overrides,
  };
}

function renderWithProviders(ui: ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        {ui}
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe('Phase 11 channel health and pool UI', () => {
  const seen: SeenRequest[] = [];
  let monitorRows: ChannelHealthMonitorRow[];
  let pools: ChannelPool[];

  beforeEach(() => {
    seen.length = 0;
    monitorRows = [
      monitorRow(),
      monitorRow({
        channelId: 1102,
        channelName: 'p11-cmpp-backup',
        status: 'MAINTENANCE',
        healthState: 'HEALTHY',
        reasonCode: 'SEED_VALIDATION',
        candidateEligible: false,
        candidateReasonCode: 'STATUS_MAINTENANCE',
      }),
    ];
    pools = [pool()];
    useAuthStore.setState({
      accessToken: 'test-token',
      userType: 'ADMIN',
      tenantId: null,
      expiresAt: Date.now() + 60_000,
      principalKey: '11:test-token',
    });
    apiClient.defaults.adapter = async (config: AxiosRequestConfig) => {
      const request = config as InternalAxiosRequestConfig;
      const method = (request.method ?? 'get').toUpperCase();
      const url = request.url ?? '';
      const body = typeof request.data === 'string' ? JSON.parse(request.data) : request.data;
      seen.push({ method, url, body });
      if (url === '/console/channel-health/monitor' && method === 'GET') {
        return axiosResponse(request, apiResponse(monitorRows));
      }
      if (url === '/console/channel-health/pools' && method === 'GET') {
        return axiosResponse(request, apiResponse(pools));
      }
      if (url === '/console/channel-health/channels/1101/pause' && method === 'POST') {
        monitorRows = monitorRows.map((row) => row.channelId === 1101
          ? { ...row, status: 'PAUSED', healthState: 'PAUSED', candidateEligible: false, candidateReasonCode: 'STATUS_PAUSED', pauseReason: body.reason, pausedBy: '11' }
          : row);
        return axiosResponse(request, apiResponse(monitorRows[0]));
      }
      if (url === '/console/channel-health/channels/1101/maintenance/start' && method === 'POST') {
        monitorRows = monitorRows.map((row) => row.channelId === 1101
          ? { ...row, status: 'MAINTENANCE', healthState: 'MAINTENANCE', candidateEligible: false, candidateReasonCode: 'STATUS_MAINTENANCE' }
          : row);
        return axiosResponse(request, apiResponse(monitorRows[0]));
      }
      if (url === '/console/channel-health/channels/1102/maintenance/end' && method === 'POST') {
        monitorRows = monitorRows.map((row) => row.channelId === 1102
          ? { ...row, status: 'NORMAL', healthState: 'HEALTHY', candidateEligible: true, candidateReasonCode: 'ELIGIBLE' }
          : row);
        return axiosResponse(request, apiResponse(monitorRows[1]));
      }
      if (url === '/console/channel-health/channels/1101/observations' && method === 'POST') {
        monitorRows = monitorRows.map((row) => row.channelId === 1101 ? { ...row, reasonCode: body.reasonCode } : row);
        return axiosResponse(request, apiResponse(monitorRows[0]));
      }
      if (url === '/console/channel-health/pools' && method === 'POST') {
        const next = pool({ id: 502, name: body.name, mode: body.mode, version: 1, members: body.members });
        pools = [...pools, next];
        return axiosResponse(request, apiResponse(next));
      }
      return axiosResponse(request, apiResponse(null));
    };
  });

  afterEach(() => {
    delete apiClient.defaults.adapter;
    act(() => useAuthStore.getState().logout());
  });

  it('renders monitor state, records validation, and pauses a channel with reason evidence', async () => {
    renderWithProviders(<ChannelHealthPage />);
    await screen.findByTestId('admin-channel-health-channel-monitor-page');
    expect((await screen.findAllByTestId('admin-channel-health-channel-monitor-health-state'))[0]).toHaveTextContent('HEALTHY');

    const row = (await screen.findAllByTestId('admin-channel-health-channel-monitor-row'))[0];
    fireEvent.click(within(row).getByTestId('admin-channel-health-channel-monitor-sample'));
    await screen.findByText('健康校验已记录。');
    fireEvent.click(within(row).getByTestId('admin-channel-health-channel-monitor-pause'));
    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByTestId('admin-channel-health-channel-monitor-action-actor')).toHaveTextContent('当前登录账号');
    fireEvent.change(within(dialog).getByTestId('admin-channel-health-channel-monitor-action-reason'), { target: { value: 'sustained failure' } });
    fireEvent.click(within(dialog).getByTestId('admin-channel-health-channel-monitor-action-submit'));

    await screen.findByText('通道状态已更新。');
    await waitFor(() => expect(screen.getByText('STATUS_PAUSED')).toBeVisible());
    expect(seen).toContainEqual(expect.objectContaining({ method: 'POST', url: '/console/channel-health/channels/1101/pause' }));
  });

  it('supports planned maintenance start and validates maintenance end control', async () => {
    renderWithProviders(<ChannelHealthPage />);
    await screen.findByTestId('admin-channel-health-channel-monitor-page');
    const rows = await screen.findAllByTestId('admin-channel-health-channel-monitor-row');
    fireEvent.click(within(rows[0]).getByTestId('admin-channel-health-channel-monitor-maintenance'));
    fireEvent.change(screen.getByTestId('admin-channel-health-channel-monitor-action-reason'), { target: { value: 'planned window' } });
    fireEvent.click(screen.getByTestId('admin-channel-health-channel-monitor-action-submit'));
    await screen.findByText('通道状态已更新。');

    fireEvent.click(within(rows[1]).getByTestId('admin-channel-health-channel-monitor-maintenance-end'));
    fireEvent.change(screen.getByTestId('admin-channel-health-channel-monitor-action-reason'), { target: { value: 'validation passed' } });
    fireEvent.click(screen.getByTestId('admin-channel-health-channel-monitor-action-submit'));
    await waitFor(() => expect(seen).toContainEqual(expect.objectContaining({ method: 'POST', url: '/console/channel-health/channels/1102/maintenance/end' })));
  });

  it('renders pools and validates weighted editor before save', async () => {
    renderWithProviders(<ChannelPoolsPage />);
    await screen.findByTestId('admin-channel-health-channel-pools-page');
    expect(await screen.findByTestId('admin-channel-health-channel-pools-row')).toHaveTextContent('p11-weighted');

    fireEvent.click(screen.getByTestId('admin-channel-health-channel-pools-create-open'));
    const editor = screen.getByTestId('admin-channel-health-channel-pools-weight-editor');
    fireEvent.change(within(editor).getByTestId('admin-channel-health-channel-pools-form-name'), { target: { value: 'p11-new' } });
    fireEvent.click(within(editor).getByTestId('admin-channel-health-channel-pools-member-add'));
    fireEvent.change(within(editor).getByTestId('admin-channel-health-channel-pools-member-weight'), { target: { value: '90' } });
    expect(screen.getByRole('alert')).toHaveTextContent('权重总和必须等于 100');
    fireEvent.change(within(editor).getByTestId('admin-channel-health-channel-pools-member-weight'), { target: { value: '100' } });
    fireEvent.click(screen.getByTestId('admin-channel-health-channel-pools-form-save'));

    await screen.findByText('通道池已保存。');
    expect(seen).toContainEqual(expect.objectContaining({ method: 'POST', url: '/console/channel-health/pools' }));
  });

  it('does not load channel health pages for tenant users', async () => {
    useAuthStore.setState({ userType: 'TENANT_ADMIN' });
    renderWithProviders(<ChannelHealthPage />);
    expect(await screen.findByTestId('admin-channel-health-channel-monitor-access-denied')).toBeVisible();
    expect(seen).toHaveLength(0);
  });
});
