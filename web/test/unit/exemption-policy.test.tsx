import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { apiClient } from '@/api/client';
import ExemptionPolicyPage from '@/pages/admin/exemptions/ExemptionPolicyPage';
import { useAuthStore } from '@/store/authStore';

type SeenRequest = { method: string; url: string; body: Record<string, unknown> | undefined };

function apiResponse<T>(data: T) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-09T00:00:00Z', traceId: 'trace-p14' };
}

function axiosResponse(config: InternalAxiosRequestConfig, data: unknown): AxiosResponse {
  return { config, data, headers: {}, status: 200, statusText: 'OK' };
}

function policy(overrides: Record<string, unknown> = {}) {
  return {
    id: 1401,
    tenantId: 42,
    exemptionType: 'SIGNATURE',
    resourceId: 'sig-1201',
    productCode: 'DOMESTIC_SMS',
    scopeExpression: 'LOGIN',
    approvalStatus: 'APPROVED',
    validFrom: '2026-09-01T00:00:00',
    validUntil: '2026-10-01T00:00:00',
    revoked: false,
    versionNo: 1,
    usageCount: 0,
    reason: '临时业务豁免',
    createdBy: 'operator-7',
    revokedBy: null,
    revokeReason: null,
    revokedAt: null,
    createdAt: '2026-09-09T00:00:00',
    ...overrides,
  };
}

function usage(overrides: Record<string, unknown> = {}) {
  return {
    id: 1,
    exemptionRuleId: 1401,
    versionNo: 1,
    tenantId: 42,
    subjectType: 'SIGNATURE',
    subjectId: 'sig-1201',
    productCode: 'DOMESTIC_SMS',
    scopeExpression: 'LOGIN',
    controlCode: 'SIGNATURE_REVIEW',
    actor: 'operator-7',
    reason: '范围验证',
    result: 'ACTIVE',
    createdAt: '2026-09-09T00:00:00',
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

describe('Phase 14 auditable exemption policy UI', () => {
  const seen: SeenRequest[] = [];
  let rows: ReturnType<typeof policy>[];
  let usageRows: ReturnType<typeof usage>[];

  beforeEach(() => {
    seen.length = 0;
    rows = [policy()];
    usageRows = [];
    useAuthStore.setState({
      accessToken: 'test-token',
      userType: 'ADMIN',
      tenantId: null,
      expiresAt: Date.now() + 60_000,
      principalKey: '14:test-token',
    });
    apiClient.defaults.adapter = async (config: AxiosRequestConfig) => {
      const request = config as InternalAxiosRequestConfig;
      const method = (request.method ?? 'get').toUpperCase();
      const url = request.url ?? '';
      const body = typeof request.data === 'string' ? JSON.parse(request.data) : request.data;
      seen.push({ method, url, body });
      if (url === '/console/exemptions' && method === 'GET') return axiosResponse(request, apiResponse(rows));
      if (url === '/console/exemptions/usage-history' && method === 'GET') return axiosResponse(request, apiResponse(usageRows));
      if (url === '/console/exemptions' && method === 'POST') {
        const created = policy({ id: 1402, ...body });
        rows = [created, ...rows];
        return axiosResponse(request, apiResponse(created));
      }
      if (url === '/console/exemptions/effective-preview' && method === 'POST') {
        usageRows = [usage(), ...usageRows];
        return axiosResponse(request, apiResponse({
          result: body.controlCode === 'ACCOUNT_BALANCE' ? 'DENIED_NON_EXEMPTABLE' : 'ACTIVE',
          exemptionRuleId: body.controlCode === 'ACCOUNT_BALANCE' ? null : 1401,
          versionNo: body.controlCode === 'ACCOUNT_BALANCE' ? null : 1,
          reason: body.controlCode === 'ACCOUNT_BALANCE' ? '当前控制不可豁免' : '匹配已批准且有效的豁免',
          subjectType: body.exemptionType,
          subjectId: body.resourceId,
          productCode: body.productCode,
          scopeExpression: body.scopeExpression,
        }));
      }
      if (url === '/console/exemptions/1401/revoke' && method === 'POST') {
        rows = rows.map((row) => row.id === 1401 ? policy({ ...row, revoked: true, revokedBy: 'operator-7', revokeReason: body.reason }) : row);
        return axiosResponse(request, apiResponse(rows.find((row) => row.id === 1401)));
      }
      return axiosResponse(request, apiResponse(null));
    };
  });

  afterEach(() => {
    delete apiClient.defaults.adapter;
    act(() => useAuthStore.getState().logout());
  });

  it('configures bounded exemptions and records the request payload', async () => {
    renderWithProviders(<ExemptionPolicyPage />);
    await screen.findByTestId('admin-auditable-exemption-exemption-policy-page');

    fireEvent.change(screen.getByTestId('admin-auditable-exemption-exemption-policy-type'), { target: { value: 'CONTENT' } });
    fireEvent.change(screen.getByTestId('admin-auditable-exemption-exemption-policy-resource'), { target: { value: 'tpl-1' } });
    fireEvent.click(screen.getByTestId('admin-auditable-exemption-exemption-policy-save'));

    await screen.findByText('豁免策略已保存。');
    expect(seen).toContainEqual(expect.objectContaining({
      method: 'POST',
      url: '/console/exemptions',
      body: expect.objectContaining({
        tenantId: 42,
        exemptionType: 'CONTENT',
        resourceId: 'tpl-1',
        productCode: 'DOMESTIC_SMS',
        scopeExpression: 'LOGIN',
        approvalStatus: 'APPROVED',
      }),
    }));
  });

  it('previews effective results and refreshes usage history', async () => {
    renderWithProviders(<ExemptionPolicyPage />);
    await screen.findByTestId('admin-auditable-exemption-exemption-effective-preview');

    fireEvent.click(screen.getByTestId('admin-auditable-exemption-exemption-preview-run'));

    await screen.findByText('豁免生效预览已刷新。');
    await waitFor(() => expect(screen.getByTestId('admin-auditable-exemption-exemption-preview-result')).toHaveTextContent('ACTIVE'));
    await waitFor(() => expect(screen.getByTestId('admin-auditable-exemption-exemption-usage-history')).toHaveTextContent('SIGNATURE_REVIEW'));
  });

  it('revokes policies and shows audited state', async () => {
    renderWithProviders(<ExemptionPolicyPage />);
    await screen.findByTestId('admin-auditable-exemption-exemption-policy-table');

    const row = screen.getByTestId('admin-auditable-exemption-exemption-policy-row');
    fireEvent.click(within(row).getByTestId('admin-auditable-exemption-exemption-policy-revoke'));

    await screen.findByText('豁免策略已撤销。');
    await waitFor(() => expect(screen.getByTestId('admin-auditable-exemption-exemption-policy-row')).toHaveTextContent('已撤销'));
  });
});
