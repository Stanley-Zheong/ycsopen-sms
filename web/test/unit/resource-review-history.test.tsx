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
  let historyRows = [signatureRow, templateRow];
  let overviewGate: Promise<void>;
  let releaseOverview: (() => void) | undefined;

  beforeEach(() => {
    urls.length = 0;
    historyRows = [signatureRow, templateRow];
    overviewGate = Promise.resolve();
    releaseOverview = undefined;
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
        await overviewGate;
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
      if (url.startsWith('/console/review-history')) return axiosResponse(request, apiResponse(historyRows));
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
    await screen.findByText('SIGNATURE:1');
    fireEvent.click(screen.getByTestId('query-panel-toggle'));
    fireEvent.change(screen.getByLabelText('资源类型'), { target: { value: 'SIGNATURE' } });
    fireEvent.change(screen.getByLabelText('机构'), { target: { value: '42' } });
    fireEvent.change(screen.getByLabelText('审核人'), { target: { value: 'operator' } });
    fireEvent.change(screen.getByLabelText('开始时间'), { target: { value: '2026-09-09T00:00' } });
    fireEvent.change(screen.getByLabelText('结束时间'), { target: { value: '2026-09-09T23:59' } });
    expect(urls.some((url) => url.includes('resourceType=SIGNATURE'))).toBe(false);
    fireEvent.click(screen.getByTestId('query-submit'));

    const table = await screen.findByTestId('admin-resource-review-history-review-table');
    await within(table).findByText('SIGNATURE:1');
    expect(table).toHaveTextContent('SIGNATURE:1');
    expect(table).toHaveTextContent('TEMPLATE:1');
    await waitFor(() => expect(urls.some((url) => url.includes('resourceType=SIGNATURE') && url.includes('tenantId=42')
      && url.includes('createdFrom=2026-09-09T00%3A00') && url.includes('createdTo=2026-09-09T23%3A59')
      && url.includes('page=0') && url.includes('pageSize=50'))).toBe(true));
    expect(screen.getByTestId('admin-resource-review-history-review-page-prev')).toBeDisabled();
    expect(screen.getByTestId('admin-resource-review-history-review-page-next')).toBeDisabled();

    const requestCountBeforeReset = urls.length;
    fireEvent.click(screen.getByTestId('query-reset'));
    expect(screen.getByLabelText('资源类型')).toHaveValue('');
    expect(screen.getByLabelText('机构')).toHaveValue('');
    expect(screen.getByLabelText('审核人')).toHaveValue('');
    await waitFor(() => expect(urls.slice(requestCountBeforeReset).some((url) => url === '/console/review-history?page=0&pageSize=50')).toBe(true));

    fireEvent.click(within(table).getAllByTestId('admin-resource-review-history-review-detail-open')[1]);
    const drawer = await screen.findByTestId('admin-resource-review-history-review-detail-drawer');
    expect(drawer).toHaveTextContent('TEMPLATE:1');
    expect(drawer).toHaveTextContent('验证码 ${code}');
    expect(drawer).toHaveTextContent('/admin/templates/review?keyword=TPL-001');
  });

  it('keeps all table columns visible with a structured empty row', async () => {
    historyRows = [];
    renderWithProviders(<ResourceReviewHistoryPage />);

    const panel = await screen.findByTestId('query-panel');
    expect(panel).toBeVisible();
    fireEvent.click(within(panel).getByTestId('query-panel-toggle'));
    expect(within(panel).getByTestId('query-submit')).toBeEnabled();
    expect(within(panel).getByTestId('query-reset')).toBeEnabled();

    const table = await screen.findByTestId('data-table');
    expect(within(table).getAllByRole('columnheader').map((header) => header.textContent)).toEqual([
      '决定', '资源', '机构', '状态', '审核人', '原因', '时间', '操作',
    ]);
    const empty = await within(table).findByTestId('table-empty');
    expect(empty).toHaveTextContent('暂无审核记录');
    expect(within(empty).getByRole('cell')).toHaveAttribute('colspan', '8');
  });

  it('does not show the successful empty state before operator access and rows resolve', async () => {
    overviewGate = new Promise<void>((resolve) => { releaseOverview = resolve; });
    historyRows = [];
    renderWithProviders(<ResourceReviewHistoryPage />);

    const table = await screen.findByTestId('data-table');
    expect(within(table).getByRole('status')).toHaveTextContent('正在加载统一审核历史');
    expect(within(table).queryByTestId('table-empty')).not.toBeInTheDocument();

    await act(async () => { releaseOverview?.(); });
    expect(await within(table).findByTestId('table-empty')).toHaveTextContent('暂无审核记录');
    expect(within(table).queryByRole('status')).not.toBeInTheDocument();
  });
});
