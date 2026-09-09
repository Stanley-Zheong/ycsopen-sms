import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { apiClient } from '@/api/client';
import type { ChannelConfiguration, ChannelDependencyView } from '@/api/channelConfigurationApi';
import ChannelConfigurationPage from '@/pages/admin/channels/ChannelConfigurationPage';
import { useAuthStore } from '@/store/authStore';

type SeenRequest = { method: string; url: string; body: unknown };

function apiResponse<T>(data: T) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-08T00:00:00Z', traceId: 'trace-p10' };
}

function axiosResponse(config: InternalAxiosRequestConfig, data: unknown): AxiosResponse {
  return { config, data, headers: {}, status: 200, statusText: 'OK' };
}

function channel(overrides: Partial<ChannelConfiguration> = {}): ChannelConfiguration {
  return {
    id: 1001,
    name: 'p10-cmpp-main',
    protocol: 'CMPP',
    operator: 'MOBILE',
    host: '127.0.0.1',
    port: 7890,
    account: '******',
    password: '******',
    spId: 'SPID10',
    serviceId: 'svc10',
    srcId: '10690010',
    maxConnections: 4,
    windowSize: 8,
    tpsLimit: 100,
    price: '0.0300',
    priority: 50,
    activeWindow: '00:00-23:59',
    extraConfig: {},
    availability: 'AVAILABLE',
    status: 'NORMAL',
    effectiveVersion: 1,
    configurationVersion: 1,
    updatedAt: '2026-09-08T00:00:00Z',
    ...overrides,
  };
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        <ChannelConfigurationPage />
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe('Phase 10 channel configuration UI', () => {
  const seen: SeenRequest[] = [];
  let rows: ChannelConfiguration[];
  let dependencies: ChannelDependencyView;
  let offlineBlocked: boolean;

  beforeEach(() => {
    seen.length = 0;
    rows = [channel(), channel({ id: 1002, name: 'p10-backup' })];
    dependencies = {
      channelId: 1001,
      unresolvedCount: 1,
      dependencies: [{ source: 'ROUTE_RULE', referenceId: '88', state: 'ACTIVE', unresolved: true, destinationChannelId: null }],
    };
    offlineBlocked = false;
    useAuthStore.setState({
      accessToken: 'test-token',
      userType: 'ADMIN',
      tenantId: null,
      expiresAt: Date.now() + 60_000,
      principalKey: '10:test-token',
    });
    apiClient.defaults.adapter = async (config: AxiosRequestConfig) => {
      const request = config as InternalAxiosRequestConfig;
      const method = (request.method ?? 'get').toUpperCase();
      const url = request.url ?? '';
      const body = typeof request.data === 'string' ? JSON.parse(request.data) : request.data;
      seen.push({ method, url, body });
      if (url === '/console/channels/configuration' && method === 'GET') {
        return axiosResponse(request, apiResponse(rows));
      }
      if (url === '/console/channels/configuration' && method === 'POST') {
        const next = channel({ id: 1003, name: body.name, protocol: body.protocol, price: '0.1200', effectiveVersion: null });
        rows = [...rows, next];
        return axiosResponse(request, apiResponse(next));
      }
      if (url === '/console/channels/configuration/1001/connectivity-test') {
        return axiosResponse(request, apiResponse({ passed: true, reasonCode: 'CONFORMANCE_PASSED', retryable: false }));
      }
      if (url === '/console/channels/configuration/1001/activate') {
        return axiosResponse(request, apiResponse({
          channelId: 1001,
          requestedVersion: 2,
          effectiveVersion: 2,
          resultCode: 'EFFECTIVE',
          retryable: false,
          safeReason: 'PASSED',
        }));
      }
      if (url === '/console/channels/configuration/1001/dependencies' && method === 'GET') {
        return axiosResponse(request, apiResponse(dependencies.dependencies));
      }
      if (url === '/console/channels/configuration/1001/dependencies/migrate') {
        dependencies = { ...dependencies, unresolvedCount: 0, dependencies: [] };
        return axiosResponse(request, apiResponse(dependencies.dependencies));
      }
      if (url === '/console/channels/configuration/1001/offline') {
        if (offlineBlocked) {
          return axiosResponse(request, apiResponse({
            channelId: 1001,
            status: 'NORMAL',
            changed: false,
            unresolvedDependencies: dependencies.dependencies,
          }));
        }
        rows = rows.map((row) => (row.id === 1001 ? { ...row, status: 'OFFLINE' } : row));
        return axiosResponse(request, apiResponse({ channelId: 1001, status: 'OFFLINE', changed: true, unresolvedDependencies: [] }));
      }
      return axiosResponse(request, apiResponse(null));
    };
  });

  afterEach(() => {
    delete apiClient.defaults.adapter;
    act(() => useAuthStore.getState().logout());
  });

  it('creates a complete channel and never renders saved credential plaintext', async () => {
    renderPage();
    await screen.findByTestId('admin-channel-configuration-channel-configuration-page');
    fireEvent.click(screen.getByRole('button', { name: '新建通道' }));
    const dialog = screen.getByRole('dialog');
    fireEvent.change(within(dialog).getByTestId('admin-channel-configuration-channel-form-name'), { target: { value: 'p10-http-main' } });
    fireEvent.change(within(dialog).getByTestId('admin-channel-configuration-channel-form-protocol'), { target: { value: 'HTTP' } });
    within(within(dialog).getByTestId('admin-channel-configuration-channel-form-endpoint')).getByRole('textbox').focus();
    fireEvent.change(within(within(dialog).getByTestId('admin-channel-configuration-channel-form-credential')).getByRole('textbox'), { target: { value: 'p10-account' } });
    fireEvent.change(within(dialog).getByLabelText('密码'), { target: { value: 'P10-Secret!123' } });
    fireEvent.change(within(dialog).getByTestId('admin-channel-configuration-channel-form-price'), { target: { value: '0.1200' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '保存' }));
    await screen.findByText('p10-http-main');
    expect(JSON.stringify(seen)).toContain('"password":"P10-Secret!123"');
    expect(document.body).not.toHaveTextContent('P10-Secret!123');
  });

  it('shows connectivity, activation, dependency migration, and offline states', async () => {
    renderPage();
    await screen.findByTestId('admin-channel-configuration-channel-configuration-page');
    const row = (await screen.findAllByTestId('admin-channel-configuration-channel-row'))[0];
    fireEvent.click(within(row).getByTestId('admin-channel-configuration-channel-configuration-connectivity-test'));
    await screen.findByText('连接性校验通过。');
    fireEvent.click(within(row).getByRole('button', { name: '激活' }));
    await screen.findByTestId('admin-channel-configuration-channel-configuration-activation-result');
    expect(screen.getByTestId('admin-channel-configuration-channel-configuration-activation-result')).toHaveTextContent('EFFECTIVE');

    fireEvent.click(within(row).getByTestId('admin-channel-configuration-channel-dependency-preview'));
    await screen.findByTestId('admin-channel-configuration-channel-migration-wizard');
    expect(screen.getByTestId('admin-channel-configuration-channel-migration-wizard')).toHaveTextContent('88');
    await waitFor(() => expect(screen.getByText('未解决依赖：1')).toBeVisible());
    fireEvent.change(screen.getByLabelText('迁移到'), { target: { value: '1002' } });
    fireEvent.click(screen.getByRole('button', { name: '迁移依赖' }));
    await screen.findByText('依赖已迁移，可以下线通道。');
    fireEvent.click(screen.getByRole('button', { name: '关闭' }));
    fireEvent.click(within(row).getByTestId('admin-channel-configuration-channel-offline'));
    fireEvent.click(screen.getByRole('button', { name: '确认下线' }));
    await screen.findByText('通道已下线。');
  });

  it('shows blocked offline result when server keeps dependencies unresolved', async () => {
    offlineBlocked = true;
    renderPage();
    await screen.findByTestId('admin-channel-configuration-channel-configuration-page');
    const row = (await screen.findAllByTestId('admin-channel-configuration-channel-row'))[0];

    fireEvent.click(within(row).getByTestId('admin-channel-configuration-channel-offline'));
    fireEvent.click(screen.getByRole('button', { name: '确认下线' }));

    await screen.findByText('通道仍有 1 个关联依赖，不能下线。');
    expect(screen.getByRole('dialog')).toBeVisible();
    expect(row).toHaveTextContent('NORMAL');
  });

  it('disables mutable lifecycle actions for an offline channel row', async () => {
    rows = [channel({ status: 'OFFLINE' })];
    renderPage();
    const row = await screen.findByTestId('admin-channel-configuration-channel-row');

    expect(within(row).getByTestId('admin-channel-configuration-channel-edit')).toBeDisabled();
    expect(within(row).getByTestId('admin-channel-configuration-channel-configuration-connectivity-test')).toBeDisabled();
    expect(within(row).getByTestId('admin-channel-configuration-channel-activate')).toBeDisabled();
    expect(within(row).getByTestId('admin-channel-configuration-channel-offline')).toBeDisabled();
    expect(within(row).getByTestId('admin-channel-configuration-channel-dependency-preview')).toBeEnabled();
  });

  it('does not fetch configuration data for a tenant user', async () => {
    useAuthStore.setState({ userType: 'TENANT_ADMIN' });
    renderPage();
    expect(await screen.findByTestId('admin-channel-configuration-channel-configuration-access-denied')).toBeVisible();
    expect(seen).toHaveLength(0);
  });
});
