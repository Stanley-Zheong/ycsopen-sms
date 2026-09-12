import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { apiClient } from '@/api/client';
import SignatureReviewPage from '@/pages/admin/signatures/SignatureReviewPage';
import SignatureLifecyclePage from '@/pages/tenant/signatures/SignatureLifecyclePage';
import { useAuthStore } from '@/store/authStore';

type SeenRequest = { method: string; url: string; body: unknown };

function apiResponse<T>(data: T) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-08T00:00:00Z', traceId: 'trace-p12' };
}

function axiosResponse(config: InternalAxiosRequestConfig, data: unknown): AxiosResponse {
  return { config, data, headers: {}, status: 200, statusText: 'OK' };
}

function signature(overrides: Record<string, unknown> = {}) {
  return {
    id: 1201,
    tenantId: 42,
    signCode: 'SGN42',
    signContent: '优创硕安',
    signType: 'TRADEMARK',
    usageType: 'SELF',
    riskLevel: 'HIGH',
    evidenceRef: 'pobj-proof-1',
    applicantName: '申请人A',
    auditStatus: 'PENDING',
    auditComment: '',
    auditTime: null,
    createdAt: '2026-09-08T00:00:00',
    history: [{ eventType: 'SUBMITTED', actor: 'tenant:42', opinion: '提交申请', riskLevel: 'HIGH', createdAt: '2026-09-08T00:00:00' }],
    ...overrides,
  };
}

function renderWithProviders(ui: ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
    </MemoryRouter>,
  );
}

describe('Phase 12 signature lifecycle UI', () => {
  const seen: SeenRequest[] = [];
  let tenantRows: ReturnType<typeof signature>[];
  let adminRows: ReturnType<typeof signature>[];
  let filings: Array<Record<string, unknown>>;

  beforeEach(() => {
    seen.length = 0;
    tenantRows = [signature()];
    adminRows = [signature()];
    filings = [
      { signatureId: 1201, channelId: 10, channelName: 'cmpp-main', protocol: 'CMPP', operator: 'MOBILE', status: 'NONE', providerRequestId: null, resultMessage: null, attemptCount: 0, channelEligible: true, channelEligibilityReason: 'ELIGIBLE' },
      { signatureId: 1201, channelId: 11, channelName: 'cmpp-paused', protocol: 'CMPP', operator: 'MOBILE', status: 'REGISTERED', providerRequestId: 'filing-1201-11-1', resultMessage: 'ok', attemptCount: 1, channelEligible: false, channelEligibilityReason: 'STATUS_PAUSED' },
    ];
    useAuthStore.setState({
      accessToken: 'test-token',
      userType: 'TENANT_ADMIN',
      tenantId: 42,
      expiresAt: Date.now() + 60_000,
      principalKey: '12:test-token',
    });
    apiClient.defaults.adapter = async (config: AxiosRequestConfig) => {
      const request = config as InternalAxiosRequestConfig;
      const method = (request.method ?? 'get').toUpperCase();
      const url = request.url ?? '';
      const body = typeof request.data === 'string' ? JSON.parse(request.data) : request.data;
      seen.push({ method, url, body });
      if (url === '/console/tenant/signatures' && method === 'GET') return axiosResponse(request, apiResponse(tenantRows));
      if (url === '/console/tenant/signatures' && method === 'POST') {
        const created = signature({ id: 1202, signContent: body.signContent, signType: body.signType, usageType: body.usageType, evidenceRef: body.evidenceRef });
        tenantRows = [created, ...tenantRows];
        adminRows = [created, ...adminRows];
        return axiosResponse(request, apiResponse(created));
      }
      if (url === '/console/tenant/signatures/1201/usable-channels' && method === 'GET') {
        return axiosResponse(request, apiResponse(filings.filter((row) => row.status === 'REGISTERED' && row.channelEligible)));
      }
      if (url.startsWith('/console/signatures/review') && method === 'GET') {
        return axiosResponse(request, apiResponse({
          summary: { total: adminRows.length, pending: 1, approved: 0, rejected: 0, supplementRequired: 0, highRisk: 1 },
          items: adminRows,
        }));
      }
      if (url === '/console/signatures/1201/decisions' && method === 'POST') {
        const status = body.decision === 'REJECT' ? 'REJECTED' : body.decision === 'SUPPLEMENT_REQUIRED' ? 'SUPPLEMENT_REQUIRED' : 'APPROVED';
        adminRows = adminRows.map((row) => row.id === 1201
          ? signature({ ...row, auditStatus: status, auditComment: body.opinion, history: [...row.history, { eventType: status, actor: '7', opinion: body.opinion, riskLevel: 'HIGH', createdAt: '2026-09-08T00:01:00' }] })
          : row);
        return axiosResponse(request, apiResponse(adminRows[0]));
      }
      if (url === '/console/signatures/1201/filings' && method === 'GET') return axiosResponse(request, apiResponse(filings));
      if (url === '/console/signatures/1201/filings/10/request' && method === 'POST') {
        filings = filings.map((row) => row.channelId === 10 ? { ...row, status: 'REGISTERING', providerRequestId: 'filing-1201-10-1', attemptCount: 1 } : row);
        return axiosResponse(request, apiResponse(filings[0]));
      }
      if (url === '/console/signatures/1201/filings/10/result' && method === 'POST') {
        filings = filings.map((row) => row.channelId === 10 ? { ...row, status: body.status, resultMessage: body.resultMessage } : row);
        return axiosResponse(request, apiResponse(filings[0]));
      }
      return axiosResponse(request, apiResponse(null));
    };
  });

  afterEach(() => {
    delete apiClient.defaults.adapter;
    act(() => useAuthStore.getState().logout());
  });

  it('submits a complete tenant signature application and shows feedback history', async () => {
    renderWithProviders(<SignatureLifecyclePage />);
    await screen.findByTestId('tenant-signature-lifecycle-signatures-page');
    expect(await screen.findByTestId('tenant-signature-lifecycle-signatures-history')).toHaveTextContent('SUBMITTED');

    expect(screen.queryByTestId('tenant-signature-lifecycle-signatures-application-dialog')).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId('tenant-signature-lifecycle-signatures-application-open'));
    const form = screen.getByTestId('tenant-signature-lifecycle-signatures-application-form');
    fireEvent.change(within(form).getByLabelText('签名内容'), { target: { value: '新商标' } });
    fireEvent.change(within(form).getByLabelText('签名类型'), { target: { value: 'TRADEMARK' } });
    fireEvent.change(within(form).getByLabelText('证明材料'), { target: { value: 'pobj-proof-2' } });
    fireEvent.click(screen.getByTestId('tenant-signature-lifecycle-signatures-application-submit'));

    await screen.findByText('签名申请已提交。');
    expect(screen.queryByTestId('tenant-signature-lifecycle-signatures-application-dialog')).not.toBeInTheDocument();
    expect(seen).toContainEqual(expect.objectContaining({
      method: 'POST',
      url: '/console/tenant/signatures',
      body: expect.objectContaining({ signContent: '新商标', signType: 'TRADEMARK', evidenceRef: 'pobj-proof-2' }),
    }));
  });

  it('shows usable channels only from registered and eligible filing rows', async () => {
    renderWithProviders(<SignatureLifecyclePage />);
    await screen.findByTestId('tenant-signature-lifecycle-signatures-page');
    fireEvent.click(await screen.findByTestId('tenant-signature-lifecycle-signatures-usable-channels'));

    await screen.findByText('暂无可用通道');
    expect(seen).toContainEqual(expect.objectContaining({ method: 'GET', url: '/console/tenant/signatures/1201/usable-channels' }));
  });

  it('lets operators filter, decide, and record filing result', async () => {
    useAuthStore.setState({ userType: 'OPERATOR', tenantId: null });
    renderWithProviders(<SignatureReviewPage />);
    await screen.findByTestId('admin-signature-lifecycle-signature-review-page');
    await waitFor(() => expect(screen.getByTestId('admin-signature-lifecycle-signature-review-stats')).toHaveTextContent('待审核 1'));

    fireEvent.change(screen.getByTestId('admin-signature-lifecycle-signature-review-filters'), { target: { value: '优创' } });
    await waitFor(() => expect(seen.some((req) => decodeURIComponent(req.url).includes('keyword=优创'))).toBe(true));

    const row = screen.getByTestId('admin-signature-lifecycle-signature-review-row');
    fireEvent.click(within(row).getByTestId('admin-signature-lifecycle-signature-review-decision-open'));
    fireEvent.change(screen.getByTestId('admin-signature-lifecycle-signature-review-decision-status'), { target: { value: 'SUPPLEMENT_REQUIRED' } });
    fireEvent.change(screen.getByTestId('admin-signature-lifecycle-signature-review-decision-opinion'), { target: { value: '材料完整，同意启用' } });
    fireEvent.click(screen.getByTestId('admin-signature-lifecycle-signature-review-decision'));
    await screen.findByText('审核结果已保存。');
    expect(seen).toContainEqual(expect.objectContaining({
      method: 'POST',
      url: '/console/signatures/1201/decisions',
      body: expect.objectContaining({ decision: 'SUPPLEMENT_REQUIRED' }),
    }));

    fireEvent.click(within(row).getByTestId('admin-signature-lifecycle-signature-filing-matrix-open'));
    await screen.findByTestId('admin-signature-lifecycle-signature-filing-matrix');
    fireEvent.click(screen.getByTestId('admin-signature-lifecycle-signature-filing-retry'));
    await screen.findByText('报备请求已提交。');
    fireEvent.change(screen.getByTestId('admin-signature-lifecycle-signature-filing-result-message'), { target: { value: 'carrier accepted' } });
    fireEvent.change(screen.getByTestId('admin-signature-lifecycle-signature-filing-result-status'), { target: { value: 'FAILED' } });
    fireEvent.click(screen.getByTestId('admin-signature-lifecycle-signature-filing-result-failed'));
    await screen.findByText('报备结果已记录。');
    expect(seen).toContainEqual(expect.objectContaining({
      method: 'POST',
      url: '/console/signatures/1201/filings/10/result',
      body: expect.objectContaining({ status: 'FAILED' }),
    }));
  });
});
