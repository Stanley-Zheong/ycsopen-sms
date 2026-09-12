import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { apiClient } from '@/api/client';
import ResourceReviewHistoryPage from '@/pages/admin/review/ResourceReviewHistoryPage';
import { useAuthStore } from '@/store/authStore';

function apiResponse<T>(data: T) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-09T00:00:00Z', traceId: 'trace-p15' };
}

function axiosResponse(config: InternalAxiosRequestConfig, data: unknown): AxiosResponse {
  return { config, data, headers: {}, status: 200, statusText: 'OK' };
}

const signatureRow = {
  decisionId: 'SIGNATURE:1',
  resourceType: 'SIGNATURE',
  resourceId: 1,
  resourceCode: 'YCSIG',
  resourceVersion: 'v1',
  tenantId: 42,
  decisionState: 'APPROVED',
  actor: 'operator-7',
  reason: '材料完整',
  riskLevel: 'HIGH',
  evidenceRef: 'pobj-proof-1',
  submittedSnapshot: '优创硕安',
  lifecycleLink: '/admin/signatures/review?keyword=YCSIG',
  createdAt: '2026-09-09T01:00:00',
};

const templateRow = {
  ...signatureRow,
  decisionId: 'TEMPLATE:1',
  resourceType: 'TEMPLATE',
  resourceId: 2,
  resourceCode: 'TPL-001',
  resourceVersion: 'v2',
  decisionState: 'REJECTED',
  actor: 'operator-8',
  reason: '变量说明不足',
  riskLevel: null,
  evidenceRef: 'signature:1',
  submittedSnapshot: '验证码 ${code}',
  lifecycleLink: '/admin/templates/review?keyword=TPL-001',
};

function renderWithProviders(ui: ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
    </MemoryRouter>,
  );
}

describe('Phase 15 unified resource review history UI', () => {
  const urls: string[] = [];

  beforeEach(() => {
    urls.length = 0;
    useAuthStore.setState({
      accessToken: 'test-token',
      userType: 'OPERATOR',
      tenantId: null,
      expiresAt: Date.now() + 60_000,
      principalKey: '15:test-token',
    });
    apiClient.defaults.adapter = async (config: AxiosRequestConfig) => {
      const request = config as InternalAxiosRequestConfig;
      const url = request.url ?? '';
      urls.push(url);
      if (url === '/console/account-overview') {
        return axiosResponse(request, apiResponse({
          id: 7,
          username: 'operator-7',
          userType: 'OPERATOR',
          roleNames: ['审核员'],
          permissions: [
            { code: 'review-history:menu', resourceType: 'MENU' },
            { code: 'review-history:read', resourceType: 'API' },
          ],
          lastLoginAt: null,
          lastLoginIp: null,
        }));
      }
      if (url.startsWith('/console/review-history/detail')) return axiosResponse(request, apiResponse(templateRow));
      if (url.startsWith('/console/review-history')) return axiosResponse(request, apiResponse([signatureRow, templateRow]));
      return axiosResponse(request, apiResponse(null));
    };
  });

  afterEach(() => {
    delete apiClient.defaults.adapter;
    act(() => useAuthStore.getState().logout());
  });

  it('filters unified history and opens immutable detail', async () => {
    renderWithProviders(<ResourceReviewHistoryPage />);

    await screen.findByTestId('admin-resource-review-history-review-page');
    const table = await screen.findByTestId('admin-resource-review-history-review-table');
    expect(table).toHaveTextContent('SIGNATURE:1');
    expect(table).toHaveTextContent('TEMPLATE:1');

    const panel = screen.getByTestId('query-panel');
    expect(within(panel).getByTestId('query-panel-fields')).not.toBeVisible();
    expect(within(panel).getByTestId('query-result-table')).toContainElement(table);
    fireEvent.click(within(panel).getByTestId('query-panel-toggle'));
    const filters = screen.getByTestId('admin-resource-review-history-review-filters');
    const initialRequestCount = urls.filter((url) => url.startsWith('/console/review-history?')).length;
    fireEvent.change(within(filters).getByLabelText('资源类型'), { target: { value: 'SIGNATURE' } });
    fireEvent.change(within(filters).getByLabelText('机构'), { target: { value: '42' } });
    fireEvent.change(within(filters).getByLabelText('审核人'), { target: { value: 'operator' } });
    fireEvent.change(within(filters).getByLabelText('开始时间'), { target: { value: '2026-09-09T00:00' } });
    fireEvent.change(within(filters).getByLabelText('结束时间'), { target: { value: '2026-09-09T23:59' } });
    expect(urls.filter((url) => url.startsWith('/console/review-history?'))).toHaveLength(initialRequestCount);
    fireEvent.click(within(panel).getByTestId('query-submit'));

    await waitFor(() => expect(urls.some((url) => url.includes('resourceType=SIGNATURE') && url.includes('tenantId=42')
      && url.includes('createdFrom=2026-09-09T00%3A00') && url.includes('createdTo=2026-09-09T23%3A59')
      && url.includes('page=0') && url.includes('pageSize=50'))).toBe(true));
    expect(screen.getByTestId('admin-resource-review-history-review-page-prev')).toBeDisabled();
    expect(screen.getByTestId('admin-resource-review-history-review-page-next')).toBeDisabled();

    const requestCountBeforeReset = urls.filter((url) => url.startsWith('/console/review-history?')).length;
    fireEvent.click(within(panel).getByTestId('query-reset'));
    expect(within(filters).getByLabelText('资源类型')).toHaveValue('');
    expect(within(filters).getByLabelText('机构')).toHaveValue('');
    expect(within(filters).getByLabelText('审核人')).toHaveValue('');
    await waitFor(() => expect(urls.filter((url) => url.startsWith('/console/review-history?')).length)
      .toBeGreaterThan(requestCountBeforeReset));
    expect([...urls].reverse().find((url) => url.startsWith('/console/review-history?')))
      .toContain('page=0&pageSize=50');

    const resetTable = screen.getByTestId('admin-resource-review-history-review-table');
    fireEvent.click(within(resetTable).getAllByTestId('admin-resource-review-history-review-detail-open')[1]);
    const drawer = await screen.findByTestId('admin-resource-review-history-review-detail-drawer');
    expect(drawer).toHaveTextContent('TEMPLATE:1');
    expect(drawer).toHaveTextContent('验证码 ${code}');
    expect(drawer).toHaveTextContent('/admin/templates/review?keyword=TPL-001');
  });
});
