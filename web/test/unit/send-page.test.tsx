import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { apiClient } from '@/api/client';
import SendPage from '@/pages/tenant/send/SendPage';
import { useAuthStore } from '@/store/authStore';

type SeenRequest = { method: string; url: string; body: Record<string, unknown> };

function apiResponse<T>(data: T, message = 'success') {
  return { code: 200, message, data, timestamp: '2026-09-09T00:00:00Z', traceId: 'trace-p26' };
}

function axiosResponse(config: InternalAxiosRequestConfig, data: unknown): AxiosResponse {
  return { config, data, headers: {}, status: 200, statusText: 'OK' };
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <SendPage />
    </QueryClientProvider>,
  );
}

describe('Phase 26 tenant console send page', () => {
  const seen: SeenRequest[] = [];
  let failNextSendWithNetwork = false;

  beforeEach(() => {
    seen.length = 0;
    failNextSendWithNetwork = false;
    useAuthStore.setState({
      accessToken: 'test-token',
      userType: 'TENANT_USER',
      tenantId: 17,
      expiresAt: Date.now() + 60_000,
      principalKey: '17:test-token',
    });
    apiClient.defaults.adapter = async (config: AxiosRequestConfig) => {
      const request = config as InternalAxiosRequestConfig;
      const method = (request.method ?? 'get').toUpperCase();
      const url = request.url ?? '';
      const body = typeof request.data === 'string' ? JSON.parse(request.data) : request.data;
      seen.push({ method, url, body });
      if (url === '/console/tenant/templates' && method === 'GET') {
        return axiosResponse(request, apiResponse([
          {
            id: 8101,
            tenantId: 17,
            templateCode: 'TPL-OK',
            templateName: '登录验证码',
            content: '您的验证码是 ${code}',
            templateType: 'VERIFICATION',
            signatureId: 9101,
            paramCheckRule: 'code:digits(4-8)',
            description: null,
            variableNames: ['code'],
            versionNo: 1,
            previousTemplateId: null,
            auditStatus: 'APPROVED',
            auditComment: null,
            auditTime: null,
            createdAt: '2026-09-09T00:00:00',
            history: [],
          },
          {
            id: 8102,
            tenantId: 17,
            templateCode: 'TPL-BAD',
            templateName: '未审核模板',
            content: '不可发送',
            templateType: 'MARKETING',
            signatureId: 9101,
            paramCheckRule: null,
            description: null,
            variableNames: [],
            versionNo: 1,
            previousTemplateId: null,
            auditStatus: 'PENDING',
            auditComment: null,
            auditTime: null,
            createdAt: '2026-09-09T00:00:00',
            history: [],
          },
        ]));
      }
      if (url === '/console/tenant/signatures' && method === 'GET') {
        return axiosResponse(request, apiResponse([
          {
            id: 9101,
            tenantId: 17,
            signCode: 'SIG-OK',
            signContent: '优创云',
            signType: 'ENTERPRISE',
            usageType: 'SELF',
            riskLevel: 'LOW',
            evidenceRef: null,
            applicantName: null,
            auditStatus: 'APPROVED',
            auditComment: null,
            auditTime: null,
            createdAt: '2026-09-09T00:00:00',
            history: [],
          },
        ]));
      }
      if (url === '/console/tenant/templates/8101/preview' && method === 'POST') {
        return axiosResponse(request, apiResponse({ renderedContent: '【优创云】您的验证码是 2468' }));
      }
      if (url === '/console/tenant/send' && method === 'POST') {
        if (failNextSendWithNetwork) {
          failNextSendWithNetwork = false;
          throw Object.assign(new Error('network'), { isAxiosError: true, code: 'ERR_NETWORK', config: request });
        }
        return axiosResponse(request, apiResponse({ messageId: `MSG-${body.submitId}`, status: 'PENDING' }));
      }
      return axiosResponse(request, apiResponse(null));
    };
  });

  afterEach(() => {
    delete apiClient.defaults.adapter;
    act(() => useAuthStore.getState().logout());
  });

  it('previews approved resources and submits console send with a stable request shape', async () => {
    renderPage();

    expect(await screen.findByText('登录验证码')).toBeVisible();
    expect(screen.queryByText('未审核模板')).toBeNull();
    fireEvent.change(screen.getByTestId('tenant-tenant-console-send-variable-code'), { target: { value: '2468' } });
    fireEvent.click(screen.getByRole('button', { name: '生成预览' }));
    expect(await screen.findByText('【优创云】您的验证码是 2468')).toBeVisible();
    fireEvent.click(screen.getByTestId('tenant-tenant-console-send-submit'));

    await screen.findByText(/已提交 1 条/);
    expect(seen).toContainEqual(expect.objectContaining({
      method: 'POST',
      url: '/console/tenant/send',
      body: expect.objectContaining({
        phoneNumber: '13800138000',
        templateId: '8101',
        signId: '9101',
        templateParams: { code: '2468' },
      }),
    }));
  });

  it('shows the prescribed network message and retries with the same correlation identity', async () => {
    failNextSendWithNetwork = true;
    renderPage();
    await screen.findByText('登录验证码');
    fireEvent.change(screen.getByTestId('tenant-tenant-console-send-variable-code'), { target: { value: '1357' } });
    fireEvent.click(screen.getByTestId('tenant-tenant-console-send-submit'));
    await screen.findByText('网络异常，请检查网络后重试');

    const failedSubmit = seen.find((request) => request.url === '/console/tenant/send')?.body.submitId;
    fireEvent.click(screen.getByTestId('shared-tenant-console-send-network-error-retry'));
    await waitFor(() => {
      const sends = seen.filter((request) => request.url === '/console/tenant/send');
      expect(sends).toHaveLength(2);
      expect(sends[1].body.submitId).toBe(failedSubmit);
    });
  });
});
