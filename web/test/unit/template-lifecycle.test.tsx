import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { apiClient } from '@/api/client';
import TemplateReviewPage from '@/pages/admin/templates/TemplateReviewPage';
import TemplateLifecyclePage from '@/pages/tenant/templates/TemplateLifecyclePage';
import { useAuthStore } from '@/store/authStore';

type SeenRequest = { method: string; url: string; body: Record<string, unknown> | undefined };

function apiResponse<T>(data: T) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-09T00:00:00Z', traceId: 'trace-p13' };
}

function axiosResponse(config: InternalAxiosRequestConfig, data: unknown): AxiosResponse {
  return { config, data, headers: {}, status: 200, statusText: 'OK' };
}

function template(overrides: Record<string, unknown> = {}) {
  return {
    id: 1301,
    tenantId: 42,
    templateCode: 'TPL42',
    templateName: '登录验证码',
    content: '您的验证码是 ${code}',
    templateType: 'VERIFY',
    signatureId: 1201,
    paramCheckRule: 'code:digits(4-8)',
    description: '登录验证',
    variableNames: ['code'],
    versionNo: 1,
    previousTemplateId: null,
    auditStatus: 'PENDING',
    auditComment: '',
    auditTime: null,
    createdAt: '2026-09-09T00:00:00',
    history: [{ eventType: 'SUBMITTED', actor: 'tenant:42', opinion: '登录验证', snapshotContent: '您的验证码是 ${code}', variableNames: ['code'], createdAt: '2026-09-09T00:00:00' }],
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

describe('Phase 13 template lifecycle UI', () => {
  const seen: SeenRequest[] = [];
  let tenantRows: ReturnType<typeof template>[];
  let adminRows: ReturnType<typeof template>[];

  beforeEach(() => {
    seen.length = 0;
    tenantRows = [template({
      auditStatus: 'REJECTED',
      auditComment: '变量说明不足',
      content: '尊敬的 ${name}，本次消费 ${amount} 元',
      variableNames: ['name', 'amount'],
      paramCheckRule: 'name:text(1-20),amount:number(1-12)',
    })];
    adminRows = [template()];
    useAuthStore.setState({
      accessToken: 'test-token',
      userType: 'TENANT_ADMIN',
      tenantId: 42,
      expiresAt: Date.now() + 60_000,
      principalKey: '13:test-token',
    });
    apiClient.defaults.adapter = async (config: AxiosRequestConfig) => {
      const request = config as InternalAxiosRequestConfig;
      const method = (request.method ?? 'get').toUpperCase();
      const url = request.url ?? '';
      const body = typeof request.data === 'string' ? JSON.parse(request.data) : request.data;
      seen.push({ method, url, body });
      if (url === '/console/tenant/templates' && method === 'GET') return axiosResponse(request, apiResponse(tenantRows));
      if (url === '/console/tenant/templates' && method === 'POST') {
        const created = template({ id: 1302, templateName: body.templateName, content: body.content, paramCheckRule: body.paramCheckRule, auditStatus: 'PENDING' });
        tenantRows = [created, ...tenantRows];
        return axiosResponse(request, apiResponse(created));
      }
      if (url.match(/^\/console\/tenant\/templates\/\d+\/preview$/) && method === 'POST') {
        if ('code' in body.variables) {
          return axiosResponse(request, apiResponse({ renderedContent: `您的验证码是 ${body.variables.code}` }));
        }
        return axiosResponse(request, apiResponse({ renderedContent: `尊敬的 ${body.variables.name}，本次消费 ${body.variables.amount} 元` }));
      }
      if (url === '/console/tenant/templates/1301/resubmit' && method === 'POST') {
        const created = template({ id: 1303, previousTemplateId: 1301, versionNo: 2, auditStatus: 'PENDING', description: body.description });
        tenantRows = [created, ...tenantRows];
        return axiosResponse(request, apiResponse(created));
      }
      if (url.startsWith('/console/templates/review') && method === 'GET') {
        return axiosResponse(request, apiResponse({
          summary: { total: 1, pending: 1, approved: 0, rejected: 0, amendmentRequired: 0 },
          items: adminRows,
        }));
      }
      if (url === '/console/templates/1301/decisions' && method === 'POST') {
        adminRows = adminRows.map((row) => row.id === 1301 ? template({ ...row, auditStatus: body.decision === 'APPROVE' ? 'APPROVED' : 'AMENDMENT_REQUIRED', auditComment: body.opinion }) : row);
        return axiosResponse(request, apiResponse(adminRows[0]));
      }
      return axiosResponse(request, apiResponse(null));
    };
  });

  afterEach(() => {
    delete apiClient.defaults.adapter;
    act(() => useAuthStore.getState().logout());
  });

  it('submits and previews a tenant template with exact variable selectors', async () => {
    renderWithProviders(<TemplateLifecyclePage />);
    await screen.findByTestId('tenant-template-lifecycle-templates-page');

    fireEvent.change(await screen.findByTestId('tenant-template-lifecycle-templates-variable-name'), { target: { value: '张三' } });
    fireEvent.change(screen.getByTestId('tenant-template-lifecycle-templates-variable-amount'), { target: { value: '99.5' } });
    fireEvent.click(screen.getByTestId('tenant-template-lifecycle-templates-variable-preview'));
    await screen.findByText('尊敬的 张三，本次消费 99.5 元');

    expect(screen.queryByTestId('tenant-template-lifecycle-templates-create-dialog')).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId('tenant-template-lifecycle-templates-create-open'));
    fireEvent.change(screen.getByTestId('tenant-template-lifecycle-templates-form-name'), { target: { value: '登录验证码' } });
    fireEvent.change(screen.getByTestId('tenant-template-lifecycle-templates-form-content'), { target: { value: '您的验证码是 ${code}' } });
    fireEvent.change(screen.getByTestId('tenant-template-lifecycle-templates-form-type'), { target: { value: 'verification' } });
    fireEvent.change(screen.getByTestId('tenant-template-lifecycle-templates-form-signature'), { target: { value: '1201' } });
    fireEvent.change(screen.getByTestId('tenant-template-lifecycle-templates-form-param-rule'), { target: { value: 'code:digits(4-8)' } });
    fireEvent.click(screen.getByTestId('tenant-template-lifecycle-templates-submit'));

    await screen.findByText('模板申请已提交。');
    expect(screen.queryByTestId('tenant-template-lifecycle-templates-create-dialog')).not.toBeInTheDocument();
    expect(seen).toContainEqual(expect.objectContaining({
      method: 'POST',
      url: '/console/tenant/templates',
      body: expect.objectContaining({ templateName: '登录验证码', signatureId: 1201 }),
    }));

    fireEvent.change(await screen.findByTestId('tenant-template-lifecycle-templates-variable-code'), { target: { value: '2468' } });
    fireEvent.click(screen.getByTestId('tenant-template-lifecycle-templates-variable-preview'));
    await screen.findByText('您的验证码是 2468');
    expect(seen).toContainEqual(expect.objectContaining({
      method: 'POST',
      url: '/console/tenant/templates/1302/preview',
      body: { variables: { code: '2468' } },
    }));
  });

  it('resubmits rejected template as a new pending version', async () => {
    renderWithProviders(<TemplateLifecyclePage />);
    await screen.findByTestId('tenant-template-lifecycle-templates-page');
    const row = await screen.findByTestId('tenant-template-lifecycle-templates-row');

    fireEvent.click(within(row).getByTestId('tenant-template-lifecycle-templates-resubmit'));
    await screen.findByText('模板重新提交成功。');

    expect(seen).toContainEqual(expect.objectContaining({ method: 'POST', url: '/console/tenant/templates/1301/resubmit' }));
  });

  it('lets operators filter and decide template review outcomes', async () => {
    useAuthStore.setState({ userType: 'OPERATOR', tenantId: null });
    renderWithProviders(<TemplateReviewPage />);
    await screen.findByTestId('admin-template-lifecycle-template-review-page');
    await waitFor(() => expect(screen.getByTestId('admin-template-lifecycle-template-review-stats')).toHaveTextContent('待审核 1'));
    expect(screen.getByTestId('query-panel')).toBeVisible();
    expect(screen.queryByTestId('query-panel-toggle')).not.toBeInTheDocument();
    expect(screen.getByTestId('query-result-table')).toContainElement(screen.getByTestId('admin-template-lifecycle-template-review-row'));

    fireEvent.change(screen.getByTestId('admin-template-lifecycle-template-review-filters'), { target: { value: '验证码' } });
    expect(seen.some((req) => decodeURIComponent(req.url).includes('keyword=验证码'))).toBe(false);
    fireEvent.click(screen.getByTestId('query-submit'));
    await waitFor(() => expect(seen.some((req) => decodeURIComponent(req.url).includes('keyword=验证码'))).toBe(true));
    fireEvent.click(screen.getByTestId('query-reset'));
    expect(screen.getByTestId('admin-template-lifecycle-template-review-filters')).toHaveValue('');
    await waitFor(() => expect(seen.filter((req) => req.url === '/console/templates/review')).toHaveLength(2));
    const row = screen.getByTestId('admin-template-lifecycle-template-review-row');
    fireEvent.click(within(row).getByTestId('admin-template-lifecycle-template-review-decision-open'));
    fireEvent.change(screen.getByTestId('admin-template-lifecycle-template-review-decision-status'), { target: { value: 'AMENDMENT_REQUIRED' } });
    fireEvent.change(screen.getByTestId('admin-template-lifecycle-template-review-decision-opinion'), { target: { value: '请补充变量用途说明' } });
    fireEvent.click(screen.getByTestId('admin-template-lifecycle-template-review-decision'));

    await screen.findByText('模板审核结果已保存。');
    await waitFor(() => expect(screen.getByTestId('admin-template-lifecycle-template-review-row')).toHaveTextContent('AMENDMENT_REQUIRED'));
    expect(seen).toContainEqual(expect.objectContaining({
      method: 'POST',
      url: '/console/templates/1301/decisions',
      body: expect.objectContaining({ decision: 'AMENDMENT_REQUIRED' }),
    }));
  });
});
