import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { apiClient } from '@/api/client';
import UserManagementPage from '@/pages/admin/identity/UserManagementPage';
import OperationAuditPage from '@/pages/admin/security/OperationAuditPage';
import SecurityEventsPage from '@/pages/admin/security/SecurityEventsPage';
import { useAuthStore } from '@/store/authStore';

type SeenRequest = { method: string; url: string; body: unknown; params: unknown };

function apiResponse<T>(data: T) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-07T00:00:00Z', traceId: 'trace-test' };
}

function axiosResponse(config: InternalAxiosRequestConfig, data: unknown): AxiosResponse {
  return { config, data, headers: {}, status: 200, statusText: 'OK' };
}

function renderPage(page: React.ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const rendered = render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <QueryClientProvider client={queryClient}>{page}</QueryClientProvider>
    </MemoryRouter>,
  );
  return { ...rendered, queryClient };
}

async function settleQueries(queryClient: QueryClient) {
  await waitFor(() => expect(queryClient.isFetching()).toBe(0));
  await act(async () => Promise.resolve());
}

describe('Phase 6 privileged audit UI', () => {
  const seen: SeenRequest[] = [];
  let failAuditRequest = false;
  let overviewUserType = 'ADMIN';
  let overviewPermissions: Array<{ code: string; resourceType: string }> = [];

  beforeEach(() => {
    seen.length = 0;
    failAuditRequest = false;
    overviewUserType = 'ADMIN';
    overviewPermissions = [];
    useAuthStore.setState({
      accessToken: 'test-token',
      userType: 'ADMIN',
      tenantId: null,
      expiresAt: Date.now() + 60_000,
      principalKey: '7:test-token',
    });
    apiClient.defaults.adapter = async (config: AxiosRequestConfig) => {
      const request = config as InternalAxiosRequestConfig;
      const method = (request.method ?? 'get').toUpperCase();
      const url = request.url ?? '';
      const body = typeof request.data === 'string' ? JSON.parse(request.data) : request.data;
      seen.push({ method, url, body, params: request.params });

      if (url === '/console/account-overview') {
        return axiosResponse(request, apiResponse({
          id: 7,
          username: 'admin',
          userType: overviewUserType,
          roleNames: ['系统管理员'],
          permissions: overviewPermissions,
          lastLoginAt: null,
          lastLoginIp: null,
        }));
      }
      if (url === '/console/operation-audits') {
        if (failAuditRequest) throw new Error('audit unavailable');
        return axiosResponse(request, apiResponse({
          items: [{
            id: 31,
            actor: 'admin',
            tenantId: null,
            operation: 'UPDATE_PLATFORM_ROLE_PERMISSIONS',
            resource: 'PUT /api/v1/console/platform-roles/10/permissions',
            result: 'SUCCESS',
            ipAddress: '192.0.2.10',
            traceId: 'trace-audit-31',
            latencyMs: 18,
            requestSummary: 'parameterNames=[roleId]',
            occurredAt: '2026-09-07T09:00:00+08:00',
          }],
          page: 0,
          size: 20,
          totalElements: 1,
        }));
      }
      if (url === '/console/security-events') {
        return axiosResponse(request, apiResponse({
          items: [{
            id: 41,
            eventType: 'UNUSUAL_LOGIN',
            actor: 'admin',
            tenantId: null,
            result: 'DETECTED',
            ipAddress: '192.0.2.20',
            traceId: 'trace-event-41',
            summary: '检测到登录来源变化',
            detectedAt: '2026-09-07T09:30:00+08:00',
          }],
          page: 0,
          size: 20,
          totalElements: 1,
        }));
      }
      if (url === '/console/platform-accounts' && method === 'GET') {
        return axiosResponse(request, apiResponse([{
          id: 1,
          username: 'admin',
          email: 'admin@example.test',
          realName: '管理员',
          maskedPhone: '138****0000',
          userType: 'ADMIN',
          status: 'ACTIVE',
          roleIds: [10],
          validUntil: null,
          lastLoginAt: null,
          createdBy: 'system',
          createdAt: '2026-01-01T00:00:00+08:00',
        }]));
      }
      if (url === '/console/platform-roles' && method === 'GET') {
        return axiosResponse(request, apiResponse([{
          id: 10,
          code: 'ADMIN',
          name: '系统管理员',
          description: '',
          status: 'ACTIVE',
          userCount: 1,
          permissionIds: [],
        }]));
      }
      if (url === '/console/platform-accounts/1/phone/reveal' && method === 'POST') {
        return axiosResponse(request, apiResponse({
          value: '13800138000',
          expiresAt: new Date(Date.now() + 60_000).toISOString(),
          auditId: 51,
        }));
      }
      return axiosResponse(request, apiResponse(null));
    };
  });

  afterEach(() => {
    delete apiClient.defaults.adapter;
    act(() => {
      useAuthStore.setState({
        accessToken: null,
        userType: null,
        tenantId: null,
        expiresAt: null,
        principalKey: null,
      });
    });
  });

  it('submits audit filters and opens only the sanitized detail record', async () => {
    const { queryClient } = renderPage(<OperationAuditPage />);
    await screen.findByText('UPDATE_PLATFORM_ROLE_PERMISSIONS');

    const filters = screen.getByTestId('admin-privileged-data-system-logs-filter');
    fireEvent.change(within(filters).getByLabelText('操作人'), { target: { value: 'admin' } });
    fireEvent.change(within(filters).getByLabelText('操作'), { target: { value: 'UPDATE_PLATFORM_ROLE_PERMISSIONS' } });
    fireEvent.change(within(filters).getByLabelText('结果'), { target: { value: 'SUCCESS' } });
    fireEvent.change(within(filters).getByLabelText('开始时间'), { target: { value: '2026-09-07T09:00' } });
    fireEvent.click(within(filters).getByRole('button', { name: '查询' }));

    await waitFor(() => expect(seen.some((request) => request.url === '/console/operation-audits'
      && JSON.stringify(request.params).includes('UPDATE_PLATFORM_ROLE_PERMISSIONS'))).toBe(true));
    const filteredRequest = [...seen].reverse().find((request) => request.url === '/console/operation-audits');
    expect(filteredRequest?.params).toMatchObject({ from: new Date('2026-09-07T09:00').toISOString() });
    fireEvent.click(screen.getByRole('button', { name: '查看审计详情' }));
    const drawer = screen.getByTestId('admin-privileged-data-system-logs-detail-drawer');
    expect(drawer).toHaveTextContent('parameterNames=[roleId]');
    expect(drawer).not.toHaveTextContent('password');
    fireEvent.click(within(drawer).getByTestId('admin-privileged-data-system-logs-detail-close'));
    expect(screen.queryByTestId('admin-privileged-data-system-logs-detail-drawer')).not.toBeInTheDocument();
    await settleQueries(queryClient);
  });

  it('filters attributable security events by controlled values', async () => {
    const { queryClient } = renderPage(<SecurityEventsPage />);
    const table = await screen.findByTestId('admin-privileged-data-security-events-table');
    expect(within(table).getByText('异常登录')).toBeVisible();

    const filters = screen.getByTestId('admin-privileged-data-security-events-filter');
    fireEvent.change(within(filters).getByLabelText('事件类型'), { target: { value: 'UNUSUAL_LOGIN' } });
    fireEvent.change(within(filters).getByLabelText('操作人'), { target: { value: 'admin' } });
    fireEvent.change(within(filters).getByLabelText('结果'), { target: { value: 'DETECTED' } });
    fireEvent.click(within(filters).getByRole('button', { name: '查询' }));

    await waitFor(() => expect(seen.some((request) => request.url === '/console/security-events'
      && JSON.stringify(request.params).includes('UNUSUAL_LOGIN'))).toBe(true));
    expect(screen.getByTestId('admin-privileged-data-security-events-table')).toHaveTextContent('检测到登录来源变化');
    await settleQueries(queryClient);
  });

  it('retains the audit filters and retries a failed request explicitly', async () => {
    failAuditRequest = true;
    const { queryClient } = renderPage(<OperationAuditPage />);
    expect(await screen.findByRole('alert')).toHaveTextContent('操作日志加载失败');
    const filters = screen.getByTestId('admin-privileged-data-system-logs-filter');
    fireEvent.change(within(filters).getByLabelText('操作人'), { target: { value: 'admin' } });

    failAuditRequest = false;
    fireEvent.click(screen.getByRole('button', { name: '重试加载操作日志' }));
    expect(await screen.findByText('UPDATE_PLATFORM_ROLE_PERMISSIONS')).toBeVisible();
    expect(within(filters).getByLabelText('操作人')).toHaveValue('admin');
    await settleQueries(queryClient);
  });

  it('reveals a phone only for a controlled purpose and clears plaintext on close', async () => {
    const { queryClient } = renderPage(<UserManagementPage />);
    expect(await screen.findByTestId('shared-privileged-data-sensitive-value')).toHaveTextContent('138****0000');
    expect(screen.queryByText('13800138000')).not.toBeInTheDocument();

    fireEvent.click(screen.getByTestId('shared-privileged-data-sensitive-value-reveal'));
    const dialog = screen.getByTestId('shared-privileged-data-sensitive-value-dialog');
    const confirm = within(dialog).getByTestId('shared-privileged-data-sensitive-value-confirm');
    expect(confirm).toBeDisabled();
    fireEvent.change(within(dialog).getByTestId('shared-privileged-data-sensitive-value-purpose'), {
      target: { value: 'SECURITY_INVESTIGATION' },
    });
    fireEvent.click(confirm);

    expect(await within(dialog).findByTestId('shared-privileged-data-sensitive-value-result')).toHaveTextContent('13800138000');
    expect(seen).toContainEqual({
      method: 'POST',
      url: '/console/platform-accounts/1/phone/reveal',
      body: { purpose: 'SECURITY_INVESTIGATION' },
      params: undefined,
    });
    fireEvent.click(within(dialog).getByTestId('shared-privileged-data-sensitive-value-close'));
    expect(screen.queryByText('13800138000')).not.toBeInTheDocument();
    expect(screen.getByTestId('shared-privileged-data-sensitive-value')).toHaveTextContent('138****0000');
    await settleQueries(queryClient);
  });

  it('does not expose reveal when only the button permission is present', async () => {
    overviewUserType = 'OPERATOR';
    overviewPermissions = [
      { code: 'identity:accounts:read', resourceType: 'API' },
      { code: 'privileged:data:reveal', resourceType: 'BUTTON' },
    ];
    useAuthStore.setState({ userType: 'OPERATOR' });

    const { queryClient } = renderPage(<UserManagementPage />);
    expect(await screen.findByTestId('shared-privileged-data-sensitive-value')).toHaveTextContent('138****0000');
    expect(screen.queryByTestId('shared-privileged-data-sensitive-value-reveal')).not.toBeInTheDocument();
    await settleQueries(queryClient);
  });
});
