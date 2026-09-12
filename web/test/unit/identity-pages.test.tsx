import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { apiClient } from '@/api/client';
import AdminLayout from '@/components/layout/AdminLayout';
import AccountOverviewPage from '@/pages/admin/identity/AccountOverviewPage';
import LoginHistoryPage from '@/pages/admin/identity/LoginHistoryPage';
import RoleManagementPage from '@/pages/admin/identity/RoleManagementPage';
import UserManagementPage from '@/pages/admin/identity/UserManagementPage';
import { useAuthStore } from '@/store/authStore';

type SeenRequest = { method: string; url: string; body: unknown };

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
  return render(<QueryClientProvider client={queryClient}>{page}</QueryClientProvider>);
}

describe('Phase 5 identity administration pages', () => {
  const seen: SeenRequest[] = [];
  let latestHistoryParams: unknown;
  let historyRequestCount: number;
  let savedPermissionIds: number[];
  let accountOverview = {
    id: 7,
    username: 'admin',
    userType: 'ADMIN' as 'ADMIN' | 'OPERATOR' | 'FINANCE',
    roleNames: ['系统管理员'],
    permissions: [
      { code: 'identity:menu', resourceType: 'MENU' as const },
      ...['identity:accounts:read', 'identity:roles:read', 'identity:history:read']
        .map((code) => ({ code, resourceType: 'API' as const })),
      ...['identity:accounts:create', 'identity:accounts:update', 'identity:roles:create',
        'identity:roles:grant', 'identity:roles:update', 'identity:roles:delete']
        .map((code) => ({ code, resourceType: 'BUTTON' as const })),
      { code: 'identity:history:all', resourceType: 'DATA' as const },
    ],
    lastLoginAt: '2026-09-07T08:30:00+08:00',
    lastLoginIp: '192.0.2.10',
  };

  beforeEach(() => {
    seen.length = 0;
    latestHistoryParams = undefined;
    historyRequestCount = 0;
    savedPermissionIds = [101];
    accountOverview = {
      id: 7,
      username: 'admin',
      userType: 'ADMIN',
      roleNames: ['系统管理员'],
      permissions: [
        { code: 'identity:menu', resourceType: 'MENU' },
        ...['identity:accounts:read', 'identity:roles:read', 'identity:history:read']
          .map((code) => ({ code, resourceType: 'API' as const })),
        ...['identity:accounts:create', 'identity:accounts:update', 'identity:roles:create',
          'identity:roles:grant', 'identity:roles:update', 'identity:roles:delete']
          .map((code) => ({ code, resourceType: 'BUTTON' as const })),
        { code: 'identity:history:all', resourceType: 'DATA' },
      ],
      lastLoginAt: '2026-09-07T08:30:00+08:00',
      lastLoginIp: '192.0.2.10',
    };
    apiClient.defaults.adapter = async (config: AxiosRequestConfig) => {
      const request = config as InternalAxiosRequestConfig;
      const method = (request.method ?? 'get').toUpperCase();
      const url = request.url ?? '';
      const body = typeof request.data === 'string' ? JSON.parse(request.data) : request.data;
      seen.push({ method, url, body });

      if (url === '/console/platform-accounts' && method === 'GET') {
        return axiosResponse(request, apiResponse([
          {
            id: 1,
            username: 'locked_admin',
            email: 'locked@example.com',
            realName: '锁定管理员',
            maskedPhone: '138****0001',
            userType: 'ADMIN',
            status: 'LOCKED',
            roleIds: [10],
            validUntil: null,
            createdBy: 'system',
            createdAt: '2026-01-01T08:00:00+08:00',
          },
          {
            id: 2,
            username: 'operator_user',
            email: 'operator@example.com',
            realName: '运营人员',
            maskedPhone: '139****0002',
            userType: 'OPERATOR',
            status: 'ACTIVE',
            roleIds: [11],
            validUntil: '2027-01-01',
            createdBy: 'system',
            createdAt: '2026-01-02T08:00:00+08:00',
          },
        ]));
      }
      if (url === '/console/platform-roles' && method === 'GET') {
        return axiosResponse(request, apiResponse([
          { id: 10, code: 'ADMIN', name: '系统管理员', description: '', status: 'ACTIVE', userCount: 2, permissionIds: savedPermissionIds },
          { id: 11, code: 'OPS', name: '运营人员', description: '', status: 'ACTIVE', userCount: 0, permissionIds: [102] },
        ]));
      }
      if (url === '/console/permissions' && method === 'GET') {
        return axiosResponse(request, apiResponse([
          { id: 101, code: 'menu:users', name: '账号菜单', resourceType: 'MENU', resourcePath: '/admin/system', httpMethod: null, parentId: null, sortOrder: 1, status: 'ACTIVE' },
          { id: 102, code: 'button:user:disable', name: '禁用账号', resourceType: 'BUTTON', resourcePath: 'disable', httpMethod: null, parentId: null, sortOrder: 2, status: 'ACTIVE' },
          { id: 103, code: 'api:user:update', name: '更新账号接口', resourceType: 'API', resourcePath: '/api/users', httpMethod: 'PUT', parentId: null, sortOrder: 3, status: 'ACTIVE' },
          { id: 104, code: 'data:platform', name: '平台数据', resourceType: 'DATA', resourcePath: 'platform:*', httpMethod: null, parentId: null, sortOrder: 4, status: 'ACTIVE' },
        ]));
      }
      if (url === '/console/platform-roles/10/permissions' && method === 'PUT') {
        savedPermissionIds = [...(body as { permissionIds: number[] }).permissionIds];
        return axiosResponse(request, apiResponse(null));
      }
      if (url === '/console/account-overview' && method === 'GET') {
        return axiosResponse(request, apiResponse(accountOverview));
      }
      if (url === '/console/login-history' && method === 'GET') {
        historyRequestCount += 1;
        latestHistoryParams = request.params;
        return axiosResponse(request, apiResponse({
          items: [{
            id: 50,
            userId: 7,
            username: 'admin',
            occurredAt: '2026-09-07T08:30:00+08:00',
            loginIp: '192.0.2.10',
            userAgent: 'Chrome',
            outcome: 'SUCCESS',
          }],
          page: 0,
          size: 20,
          totalElements: 1,
        }));
      }
      return axiosResponse(request, apiResponse(null));
    };
  });

  afterEach(() => {
    delete apiClient.defaults.adapter;
  });

  it('creates a validated platform account and can apply explicit state transitions', async () => {
    renderPage(<UserManagementPage />);
    await screen.findByText('locked_admin');

    fireEvent.click(screen.getByTestId('admin-console-identity-users-create'));
    fireEvent.change(screen.getByTestId('admin-console-identity-users-form-username'), { target: { value: 'new_admin' } });
    fireEvent.change(screen.getByTestId('admin-console-identity-users-form-password'), { target: { value: 'StrongPass1' } });
    fireEvent.change(screen.getByTestId('admin-console-identity-users-form-phone'), { target: { value: '13800138000' } });
    fireEvent.change(screen.getByLabelText('邮箱'), { target: { value: 'admin@example.com' } });
    fireEvent.change(screen.getByLabelText('真实姓名'), { target: { value: '新管理员' } });
    fireEvent.change(screen.getByTestId('admin-console-identity-users-form-type'), { target: { value: 'ADMIN' } });
    fireEvent.change(screen.getByTestId('admin-console-identity-users-form-validity'), { target: { value: '2027-01-01' } });
    fireEvent.change(screen.getByTestId('admin-console-identity-users-form-roles'), { target: { value: '10' } });
    fireEvent.click(screen.getByRole('button', { name: '保存账号' }));

    await waitFor(() => expect(seen).toContainEqual({
      method: 'POST',
      url: '/console/platform-accounts',
      body: {
        username: 'new_admin',
        password: 'StrongPass1',
        phone: '13800138000',
        email: 'admin@example.com',
        realName: '新管理员',
        userType: 'ADMIN',
        validUntil: '2027-01-01',
        roleIds: [10],
      },
    }));

    fireEvent.click(screen.getByTestId('admin-console-identity-users-unlock'));
    fireEvent.click(screen.getByTestId('admin-console-identity-users-disable'));
    fireEvent.click(screen.getByRole('button', { name: '确认禁用' }));
    await waitFor(() => {
      expect(seen).toContainEqual({ method: 'POST', url: '/console/platform-accounts/1/unlock', body: undefined });
      expect(seen).toContainEqual({ method: 'POST', url: '/console/platform-accounts/2/disable', body: undefined });
    });
  });

  it('edits an account with its existing roles and an optional replacement password', async () => {
    renderPage(<UserManagementPage />);
    await screen.findByText('locked_admin');

    fireEvent.click(screen.getAllByTestId('admin-console-identity-users-edit')[0]);
    expect(screen.getByTestId('admin-console-identity-users-form-roles')).toHaveValue(['10']);
    fireEvent.change(screen.getByTestId('admin-console-identity-users-form-phone'), { target: { value: '13800138000' } });
    fireEvent.click(screen.getByRole('button', { name: '保存账号' }));

    await waitFor(() => expect(seen).toContainEqual({
      method: 'PUT',
      url: '/console/platform-accounts/1',
      body: {
        username: 'locked_admin',
        phone: '13800138000',
        email: 'locked@example.com',
        realName: '锁定管理员',
        userType: 'ADMIN',
        validUntil: null,
        roleIds: [10],
      },
    }));
  });

  it('keeps documented action test ids unique and shows assigned roles in the account table', async () => {
    renderPage(<UserManagementPage />);
    const lockedRow = (await screen.findByText('locked_admin')).closest('tr');

    expect(screen.getAllByTestId('admin-console-identity-users-edit')).toHaveLength(1);
    expect(lockedRow).not.toBeNull();
    expect(within(lockedRow!).getByText('系统管理员')).toBeVisible();

    renderPage(<RoleManagementPage />);
    await screen.findByText('角色与权限');
    expect(screen.getAllByTestId('admin-console-identity-roles-edit')).toHaveLength(1);
  });

  it('blocks malformed username, password, phone, and past validity before sending account data', async () => {
    renderPage(<UserManagementPage />);
    await screen.findByText('locked_admin');
    fireEvent.click(screen.getByTestId('admin-console-identity-users-create'));
    const form = screen.getByTestId('admin-console-identity-users-form');
    const username = screen.getByTestId('admin-console-identity-users-form-username');
    const password = screen.getByTestId('admin-console-identity-users-form-password');
    const phone = screen.getByTestId('admin-console-identity-users-form-phone');
    fireEvent.change(screen.getByTestId('admin-console-identity-users-form-roles'), { target: { value: '10' } });

    fireEvent.change(username, { target: { value: 'bad!' } });
    fireEvent.change(password, { target: { value: 'StrongPass1' } });
    fireEvent.change(phone, { target: { value: '13800138000' } });
    fireEvent.submit(form);
    expect(screen.getByRole('alert')).toHaveTextContent('用户名须为 4-20 位字母、数字或下划线');

    fireEvent.change(username, { target: { value: 'valid_user' } });
    fireEvent.change(password, { target: { value: 'weakpass' } });
    fireEvent.submit(form);
    expect(screen.getByRole('alert')).toHaveTextContent('密码至少 8 位');

    fireEvent.change(password, { target: { value: 'StrongPass1' } });
    fireEvent.change(phone, { target: { value: '12800138000' } });
    fireEvent.submit(form);
    expect(screen.getByRole('alert')).toHaveTextContent('有效的 11 位国内手机号');

    fireEvent.change(phone, { target: { value: '13800138000' } });
    fireEvent.change(screen.getByTestId('admin-console-identity-users-form-validity'), { target: { value: '2020-01-01' } });
    fireEvent.submit(form);
    expect(screen.getByRole('alert')).toHaveTextContent('有效期不能早于当前日期');
    expect(seen.some((request) => request.method === 'POST' && request.url === '/console/platform-accounts')).toBe(false);
  });

  it('saves all four permission granularities and requires migration for an in-use role', async () => {
    renderPage(<RoleManagementPage />);
    const tree = await screen.findByTestId('admin-console-identity-roles-permission-tree');
    expect(within(tree).getByText('菜单权限')).toBeVisible();
    expect(within(tree).getByText('按钮权限')).toBeVisible();
    expect(within(tree).getByText('接口权限')).toBeVisible();
    expect(within(tree).getByText('数据权限')).toBeVisible();

    fireEvent.click(within(tree).getByLabelText('禁用账号'));
    fireEvent.click(screen.getByTestId('admin-console-identity-roles-save'));
    await waitFor(() => expect(seen).toContainEqual({
      method: 'PUT',
      url: '/console/platform-roles/10/permissions',
      body: { permissionIds: [101, 102] },
    }), { timeout: 5000 });
    await waitFor(() => {
      expect(within(tree).getByLabelText('禁用账号')).toBeChecked();
      expect(screen.getByTestId('admin-console-identity-roles-save')).toBeDisabled();
    });

    fireEvent.click(screen.getByTestId('admin-console-identity-roles-create'));
    fireEvent.change(screen.getByLabelText('角色编码'), { target: { value: 'AUDITOR' } });
    fireEvent.change(screen.getByLabelText('角色名称'), { target: { value: '审计员' } });
    fireEvent.change(screen.getByLabelText('描述'), { target: { value: '只读审计角色' } });
    fireEvent.click(screen.getByRole('button', { name: '创建角色' }));
    await waitFor(() => expect(seen).toContainEqual({
      method: 'POST',
      url: '/console/platform-roles',
      body: { code: 'AUDITOR', name: '审计员', description: '只读审计角色' },
    }));

    fireEvent.click(screen.getAllByTestId('admin-console-identity-roles-edit')[0]);
    fireEvent.change(screen.getByLabelText('描述'), { target: { value: '平台最高权限角色' } });
    fireEvent.click(screen.getByRole('button', { name: '保存角色' }));
    await waitFor(() => expect(seen).toContainEqual({
      method: 'PUT',
      url: '/console/platform-roles/10',
      body: { code: 'ADMIN', name: '系统管理员', description: '平台最高权限角色', status: 'ACTIVE' },
    }));

    fireEvent.click(screen.getByRole('button', { name: '删除系统管理员' }));
    const dialog = await screen.findByTestId('admin-console-identity-roles-migrate-dialog');
    fireEvent.change(within(dialog).getByLabelText('迁移到角色'), { target: { value: '11' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '迁移并删除' }));
    await waitFor(() => expect(seen).toContainEqual({
      method: 'DELETE',
      url: '/console/platform-roles/10',
      body: { replacementRoleId: 11 },
    }));
  });

  it('shows only the current platform identity, permission scope, and login history', async () => {
    renderPage(<AccountOverviewPage />);

    const page = await screen.findByTestId('admin-console-identity-account-overview');
    expect(within(page).getByText('admin')).toBeVisible();
    expect(within(page).getByText('identity:menu')).toBeVisible();
    const history = within(page).getByTestId('shared-console-identity-profile-login-history');
    expect(await within(history).findByText('192.0.2.10')).toBeVisible();
    expect(latestHistoryParams).toEqual({ page: 0, size: 20 });
    expect(within(page).queryByText(/余额|授信|机构数据/)).not.toBeInTheDocument();
  });

  it('provides the administrator login-history query page', async () => {
    renderPage(<LoginHistoryPage />);

    const history = await screen.findByTestId('shared-console-identity-profile-login-history');
    expect(await within(history).findByText('admin')).toBeVisible();
    expect(within(history).getByText('192.0.2.10')).toBeVisible();
    expect(seen.some((request) => request.method === 'GET' && request.url === '/console/login-history')).toBe(true);
    expect(latestHistoryParams).toEqual({ all: true, page: 0, size: 20 });

    const panel = within(history).getByTestId('query-panel');
    expect(within(panel).queryByTestId('query-panel-toggle')).not.toBeInTheDocument();
    expect(within(panel).getByTestId('query-panel-fields')).toBeVisible();
    expect(within(panel).getByTestId('query-result-table')).toHaveTextContent('192.0.2.10');
    const userId = within(panel).getByLabelText('用户 ID');
    fireEvent.change(userId, { target: { value: '42' } });
    expect(historyRequestCount).toBe(1);

    fireEvent.click(within(panel).getByTestId('query-submit'));
    await waitFor(() => expect(latestHistoryParams).toEqual({ userId: 42, page: 0, size: 20 }));
    expect(within(panel).queryByTestId('query-reset')).not.toBeInTheDocument();
    fireEvent.change(userId, { target: { value: '' } });
    fireEvent.click(within(panel).getByTestId('query-submit'));
    expect(userId).toHaveValue('');
    await waitFor(() => expect(latestHistoryParams).toEqual({ all: true, page: 0, size: 20 }));
    expect(within(panel).getByText(/第 1 页/)).toBeVisible();
  });

  it('hides identity menus and mutation controls missing from the current permission scope', async () => {
    accountOverview = {
      id: 8,
      username: 'limited-operator',
      userType: 'OPERATOR',
      roleNames: ['只读运营'],
      permissions: [
        { code: 'identity:menu', resourceType: 'MENU' },
        { code: 'identity:accounts:read', resourceType: 'API' },
      ],
      lastLoginAt: '2026-09-07T08:30:00+08:00',
      lastLoginIp: '192.0.2.11',
    };

    const token = `${btoa(JSON.stringify({ alg: 'HS256' }))}.${btoa(JSON.stringify({ sub: '8', exp: Date.now() / 1000 + 120 }))}.signature`;
    useAuthStore.getState().setSession({ accessToken: token, userType: 'OPERATOR', tenantId: null });
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/admin/system/users']} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
          <Routes>
            <Route path="/admin" element={<AdminLayout />}>
              <Route path="system/users" element={<UserManagementPage />} />
            </Route>
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await screen.findByText('locked_admin');
    expect(screen.getByRole('link', { name: '系统账号' })).toBeVisible();
    expect(screen.queryByRole('link', { name: '系统角色' })).not.toBeInTheDocument();
    expect(screen.queryByTestId('admin-console-identity-users-create')).not.toBeInTheDocument();
    expect(screen.queryByTestId('admin-console-identity-users-disable')).not.toBeInTheDocument();
    expect(screen.queryByTestId('admin-console-identity-users-unlock')).not.toBeInTheDocument();
  });
});
