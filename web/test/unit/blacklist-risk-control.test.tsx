import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { apiClient } from '@/api/client';
import BlacklistRiskControlPage from '@/pages/admin/risk/BlacklistRiskControlPage';
import { useAuthStore } from '@/store/authStore';

function apiResponse<T>(data: T) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-09T00:00:00Z', traceId: 'trace-p16' };
}

function axiosResponse(config: InternalAxiosRequestConfig, data: unknown): AxiosResponse {
  return { config, data, headers: {}, status: 200, statusText: 'OK' };
}

const entry = {
  id: 1,
  tenantId: 42,
  maskedMobile: '139****0001',
  mobileRef: 'ref-13900000001',
  listType: 'BLACK',
  reason: '投诉风险',
  source: 'MANUAL',
  status: 'ACTIVE',
  createdBy: '7',
  createdAt: '2026-09-09T00:00:00',
  expiresAt: null,
};

const provider = {
  id: 1,
  providerName: 'local-risk',
  providerUrl: 'https://risk.example.test',
  credentialRef: 'secret-ref',
  checkLevel: 'ADVANCED',
  thresholdScore: 80,
  timeoutMs: 500,
  fallbackPolicy: 'CACHE',
  status: 'ACTIVE',
  cacheTtlSeconds: 300,
  createdBy: '7',
  createdAt: '2026-09-09T00:00:00',
};

const decision = {
  id: 9,
  requestId: 'risk-1',
  tenantId: 42,
  mobileRef: 'opaque-risk-1',
  sourceCategory: 'THIRD_PARTY_RISK',
  riskResult: 'BLOCK',
  traceReason: '第三方风险名单命中，发送任务未创建且未计费',
  taskCreated: false,
  charged: false,
  createdAt: '2026-09-09T00:00:00',
};

const degradedDecision = {
  ...decision,
  id: 10,
  sourceCategory: 'THIRD_PARTY_DEGRADED',
  riskResult: 'DEGRADED_CACHE',
  traceReason: '第三方风险服务失败，按新鲜缓存策略降级',
};

function renderWithProviders(ui: ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
    </MemoryRouter>,
  );
}

describe('Phase 16 blacklist risk control UI', () => {
  const calls: Array<{ method?: string; url: string; data?: Record<string, unknown>; params?: Record<string, unknown> }> = [];

  beforeEach(() => {
    calls.length = 0;
    useAuthStore.setState({
      accessToken: 'test-token',
      userType: 'OPERATOR',
      tenantId: null,
      expiresAt: Date.now() + 60_000,
      principalKey: '16:test-token',
    });
    apiClient.defaults.adapter = async (config: AxiosRequestConfig) => {
      const request = config as InternalAxiosRequestConfig;
      const url = request.url ?? '';
      const data = request.data ? JSON.parse(String(request.data)) as Record<string, unknown> : undefined;
      calls.push({ method: request.method, url, data, params: request.params as Record<string, unknown> | undefined });
      if (url === '/console/account-overview') {
        return axiosResponse(request, apiResponse({
          id: 7,
          username: 'operator-7',
          userType: 'OPERATOR',
          roleNames: ['风控员'],
          permissions: [
            { code: 'blacklist:menu', resourceType: 'MENU' },
            { code: 'blacklist:read', resourceType: 'API' },
            { code: 'blacklist:write', resourceType: 'API' },
            { code: 'blacklist:import', resourceType: 'BUTTON' },
            { code: 'blacklist:export', resourceType: 'BUTTON' },
            { code: 'risk-provider:read', resourceType: 'API' },
            { code: 'risk-provider:write', resourceType: 'API' },
            { code: 'risk-analysis:read', resourceType: 'API' },
            { code: 'risk-analysis:check', resourceType: 'API' },
            { code: 'risk-analysis:appeal', resourceType: 'BUTTON' },
          ],
          lastLoginAt: null,
          lastLoginIp: null,
        }));
      }
      if (url === '/console/risk/blacklist' && request.method === 'get') return axiosResponse(request, apiResponse([entry]));
      if (url === '/console/risk/blacklist' && request.method === 'post') return axiosResponse(request, apiResponse(entry));
      if (url === '/console/risk/blacklist/import') return axiosResponse(request, apiResponse({ success: 1, failed: 1, errors: ['bad-mobile'] }));
      if (url === '/console/risk/blacklist/export-request') return axiosResponse(request, apiResponse({ requestId: 'export-1', matchedRows: 1, status: 'REQUESTED' }));
      if (url === '/console/risk/provider' && request.method === 'get') return axiosResponse(request, apiResponse([provider]));
      if (url === '/console/risk/provider' && request.method === 'post') return axiosResponse(request, apiResponse(provider));
      if (url === '/console/risk/blacklist/1/disable') return axiosResponse(request, apiResponse({ ...entry, status: 'DISABLED' }));
      if (url === '/console/risk/check') {
        return axiosResponse(request, apiResponse(data?.forceProviderFailure ? [degradedDecision] : [decision]));
      }
      if (url === '/console/risk/analytics') return axiosResponse(request, apiResponse({
        total: 1, blocked: 1, allowed: 0, systemHits: 0, tenantHits: 0, providerHits: 1, degraded: 0, appeals: 0,
      }));
      if (url === '/console/risk/appeals') return axiosResponse(request, apiResponse({
        id: 1, decisionId: 9, originalResult: 'BLOCK', appealResult: 'FALSE_POSITIVE',
      }));
      return axiosResponse(request, apiResponse(null));
    };
  });

  afterEach(() => {
    delete apiClient.defaults.adapter;
    act(() => useAuthStore.getState().logout());
  });

  it('manages lists, provider, decisions, degraded evidence, and appeal actions', async () => {
    renderWithProviders(<BlacklistRiskControlPage />);

    await screen.findByTestId('admin-blacklist-risk-page');
    expect(await screen.findByText('139****0001')).toBeInTheDocument();
    expect(await screen.findByText('拦截 1')).toBeInTheDocument();
    expect(await screen.findByText(/local-risk/)).toBeInTheDocument();

    expect(screen.getByTestId('query-panel-fields')).not.toBeVisible();
    expect(screen.getByTestId('admin-blacklist-risk-black-white-lists-filter-status')).toHaveValue('');
    fireEvent.click(screen.getByTestId('query-panel-toggle'));
    const initialQueryCount = calls.filter((call) => call.url === '/console/risk/blacklist' && call.method === 'get').length;
    await act(async () => {
      fireEvent.change(screen.getByTestId('admin-blacklist-risk-black-white-lists-filter-tenant'), { target: { value: '77' } });
    });
    expect(calls.filter((call) => call.url === '/console/risk/blacklist' && call.method === 'get')).toHaveLength(initialQueryCount);
    fireEvent.click(screen.getByTestId('query-submit'));
    await waitFor(() => expect(calls.some((call) => call.url === '/console/risk/blacklist' && call.method === 'get'
      && call.params?.tenantId === '77')).toBe(true));
    fireEvent.change(screen.getByTestId('admin-blacklist-risk-black-white-lists-filter-tenant'), { target: { value: '88' } });
    fireEvent.click(screen.getByTestId('admin-blacklist-risk-black-white-lists-export'));
    await waitFor(() => expect(calls.some((call) => call.url === '/console/risk/blacklist/export-request'
      && call.params?.tenantId === '77')).toBe(true));
    fireEvent.click(screen.getByTestId('query-reset'));
    await waitFor(() => expect(calls.some((call) => call.url === '/console/risk/blacklist' && call.method === 'get'
      && call.params?.tenantId === '' && call.params?.listType === '' && call.params?.status === '')).toBe(true));
    expect(screen.getByTestId('admin-blacklist-risk-black-white-lists-filter-tenant')).toHaveValue('');

    expect(screen.queryByTestId('admin-blacklist-risk-black-white-lists-create-dialog')).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId('admin-blacklist-risk-black-white-lists-create-open'));
    fireEvent.click(screen.getByTestId('admin-blacklist-risk-black-white-lists-save'));
    await waitFor(() => expect(screen.queryByTestId('admin-blacklist-risk-black-white-lists-create-dialog')).not.toBeInTheDocument());
    fireEvent.click(screen.getByTestId('admin-blacklist-risk-black-white-lists-import'));
    expect(screen.getByTestId('admin-blacklist-risk-black-white-lists-import-dialog')).toBeVisible();
    fireEvent.click(screen.getByTestId('admin-blacklist-risk-black-white-lists-import-submit'));
    fireEvent.click(screen.getByTestId('admin-blacklist-risk-black-white-lists-export'));
    fireEvent.click(screen.getByTestId('admin-blacklist-risk-black-white-lists-disable'));
    fireEvent.click(screen.getByTestId('admin-blacklist-risk-risk-provider-create-open'));
    fireEvent.click(screen.getByTestId('admin-blacklist-risk-risk-provider-save'));
    await waitFor(() => expect(screen.queryByTestId('admin-blacklist-risk-risk-provider-create-dialog')).not.toBeInTheDocument());
    expect(screen.getByTestId('admin-blacklist-risk-intercept-check-tenant')).toHaveValue(42);
    fireEvent.click(screen.getByTestId('admin-blacklist-risk-intercept-check-run'));
    const table = await screen.findByTestId('admin-blacklist-risk-intercept-decisions-table');
    expect(table).toHaveTextContent('THIRD_PARTY_RISK');
    expect(table).toHaveTextContent('未创建');
    expect(table).toHaveTextContent('未计费');
    fireEvent.click(within(table).getByTestId('admin-blacklist-risk-intercept-appeal'));
    fireEvent.click(screen.getByTestId('admin-blacklist-risk-risk-provider-degraded'));
    await waitFor(() => expect(table).toHaveTextContent('THIRD_PARTY_DEGRADED'));

    await waitFor(() => expect(calls.some((call) => call.url === '/console/risk/blacklist' && call.method === 'post')).toBe(true));
    await waitFor(() => expect(calls.some((call) => call.url === '/console/risk/blacklist/import')).toBe(true));
    await waitFor(() => expect(calls.some((call) => call.url === '/console/risk/blacklist/export-request')).toBe(true));
    await waitFor(() => expect(calls.some((call) => call.url === '/console/risk/blacklist/1/disable')).toBe(true));
    await waitFor(() => expect(calls.some((call) => call.url === '/console/risk/provider' && call.method === 'post')).toBe(true));
    await waitFor(() => expect(calls.some((call) => call.url === '/console/risk/check' && call.data?.tenantId === 42 && call.data?.forceProviderFailure === true)).toBe(true));
    await waitFor(() => expect(calls.some((call) => call.url === '/console/risk/appeals')).toBe(true));
  });
});
