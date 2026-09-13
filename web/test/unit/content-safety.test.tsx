import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { apiClient } from '@/api/client';
import ContentSafetyPage from '@/pages/admin/risk/ContentSafetyPage';
import { useAuthStore } from '@/store/authStore';

function apiResponse<T>(data: T) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-09T00:00:00Z', traceId: 'trace-p17' };
}

function axiosResponse(config: InternalAxiosRequestConfig, data: unknown): AxiosResponse {
  return { config, data, headers: {}, status: 200, statusText: 'OK' };
}

const policy = {
  id: 1,
  word: '营销',
  category: 'MARKETING',
  level: 'HIGH',
  replacement: '通知',
  action: 'REPLACE',
  scope: 'GLOBAL',
  scopeRefId: null,
  status: 'ACTIVE',
  hitCount: 7,
  createdAt: '2026-09-09T00:00:00',
  updatedAt: '2026-09-09T00:00:00',
};

function renderWithProviders(ui: ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
    </MemoryRouter>,
  );
}

describe('Phase 17 runtime content safety UI', () => {
  const calls: Array<{ method?: string; url: string; data?: Record<string, unknown> }> = [];

  beforeEach(() => {
    calls.length = 0;
    useAuthStore.setState({
      accessToken: 'test-token',
      userType: 'OPERATOR',
      tenantId: null,
      expiresAt: Date.now() + 60_000,
      principalKey: '17:test-token',
    });
    apiClient.defaults.adapter = async (config: AxiosRequestConfig) => {
      const request = config as InternalAxiosRequestConfig;
      const url = request.url ?? '';
      const data = request.data ? JSON.parse(String(request.data)) as Record<string, unknown> : undefined;
      calls.push({ method: request.method, url, data });
      if (url === '/console/account-overview') {
        return axiosResponse(request, apiResponse({
          id: 7,
          username: 'operator-7',
          userType: 'OPERATOR',
          roleNames: ['内容审核员'],
          permissions: [
            { code: 'content-safety:menu', resourceType: 'MENU' },
            { code: 'content-safety:read', resourceType: 'API' },
            { code: 'content-safety:write', resourceType: 'API' },
            { code: 'content-safety:import', resourceType: 'BUTTON' },
            { code: 'content-safety:export', resourceType: 'BUTTON' },
            { code: 'content-safety:scan', resourceType: 'API' },
          ],
          lastLoginAt: null,
          lastLoginIp: null,
        }));
      }
      if (url === '/console/content-safety/policies' && request.method === 'get') return axiosResponse(request, apiResponse([policy]));
      if (url === '/console/content-safety/policies' && request.method === 'post') return axiosResponse(request, apiResponse(policy));
      if (url === '/console/content-safety/policies/import') return axiosResponse(request, apiResponse({ success: 2, failed: 0, errors: [] }));
      if (url === '/console/content-safety/policies/export-request') return axiosResponse(request, apiResponse({ requestId: 'CONTENT_EXPORT_1', matchedRows: 1, status: 'REQUESTED' }));
      if (url === '/console/content-safety/policies/export-request?**') return axiosResponse(request, apiResponse({ requestId: 'CONTENT_EXPORT_1', matchedRows: 1, status: 'REQUESTED' }));
      if (url === '/console/content-safety/policies/1/delete') return axiosResponse(request, apiResponse({ ...policy, status: 'DISABLED' }));
      if (url === '/console/content-safety/analytics') return axiosResponse(request, apiResponse({
        total: 3, active: 2, hits: 10, intercepts: 4, replacements: 5, alerts: 1, interceptRate: 0.4, coverageRate: 0.67,
      }));
      if (url === '/console/content-safety/scan') return axiosResponse(request, apiResponse({
        blocked: false, reason: null, finalContent: '【签名】变量填入abc，高危通知',
      }));
      return axiosResponse(request, apiResponse(null));
    };
  });

  afterEach(() => {
    delete apiClient.defaults.adapter;
    act(() => useAuthStore.getState().logout());
  });

  it('manages policies, import/export, hot update, and final-content scan', async () => {
    renderWithProviders(<ContentSafetyPage />);

    await screen.findByTestId('admin-runtime-content-content-safety-page');
    expect(await screen.findByTestId('admin-runtime-content-content-safety-row')).toHaveTextContent('营销');
    expect(await screen.findByText('今日拦截 4')).toBeInTheDocument();
    expect(screen.getByTestId('admin-runtime-content-content-safety-filter-status')).toHaveValue('ACTIVE');
    expect(screen.getByTestId('admin-runtime-content-content-safety-word')).toHaveValue('营销');
    expect(screen.getByTestId('admin-runtime-content-content-safety-action')).toHaveValue('REPLACE');
    expect(screen.getByTestId('admin-runtime-content-content-safety-scan-content')).toHaveValue('【签名】变量填入ＡＢＣ，高危营销');

    fireEvent.click(screen.getByTestId('admin-runtime-content-content-safety-save'));
    fireEvent.click(screen.getByTestId('admin-runtime-content-content-safety-import'));
    fireEvent.click(screen.getByTestId('admin-runtime-content-content-safety-export'));
    fireEvent.click(screen.getByTestId('admin-runtime-content-content-safety-delete'));
    fireEvent.click(screen.getByTestId('admin-runtime-content-content-safety-scan'));

    await waitFor(() => expect(screen.getByTestId('admin-runtime-content-content-safety-scan-result')).toHaveTextContent('高危通知'));
    await waitFor(() => expect(calls.some((call) => call.url === '/console/content-safety/policies' && call.method === 'post')).toBe(true));
    await waitFor(() => expect(calls.some((call) => call.url === '/console/content-safety/policies/import')).toBe(true));
    await waitFor(() => expect(calls.some((call) => call.url === '/console/content-safety/policies/export-request')).toBe(true));
    await waitFor(() => expect(calls.some((call) => call.url === '/console/content-safety/policies/1/delete')).toBe(true));
    await waitFor(() => expect(calls.some((call) => call.url === '/console/content-safety/scan'
      && call.data?.tenantId === 17 && call.data?.templateId === 8)).toBe(true));
  });
});
