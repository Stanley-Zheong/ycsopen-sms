import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AxiosError, type AxiosRequestConfig, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { apiClient } from '@/api/client';
import type { SystemConfigurationView } from '@/api/systemConfiguration';
import SystemConfigurationPage from '@/pages/admin/system/SystemConfigurationPage';
import { useAuthStore } from '@/store/authStore';

type SeenRequest = { method: string; url: string; body: unknown };

function apiResponse<T>(data: T) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-08T00:00:00Z', traceId: 'trace-p7' };
}

function axiosResponse(config: InternalAxiosRequestConfig, data: unknown): AxiosResponse {
  return { config, data, headers: {}, status: 200, statusText: 'OK' };
}

function configurationView(): SystemConfigurationView {
  return {
    active: { version: 0, checksum: 'a'.repeat(64), actorUserId: null, actor: null, activatedAt: null, reloadStatus: 'APPLIED', reloadErrorCode: null },
    settings: [
      { key: 'security.login.max-failures', label: '登录失败锁定阈值', type: 'INTEGER', value: '5', defaultValue: '5', validation: '3 到 20 的整数', sensitive: false, configured: true },
      { key: 'security.login.unusual-ip-enabled', label: '异常 IP 登录检测', type: 'BOOLEAN', value: 'true', defaultValue: 'true', validation: 'true 或 false', sensitive: false, configured: true },
      { key: 'security.export.signing-key-ref', label: '导出签名密钥引用', type: 'SECRET_REFERENCE', value: 'env:••••••', defaultValue: 'env:••••••', validation: 'env:UPPER_SNAKE_CASE', sensitive: true, configured: true },
    ],
    draft: null,
    history: [],
  };
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return {
    queryClient,
    ...render(<MemoryRouter><QueryClientProvider client={queryClient}><SystemConfigurationPage /></QueryClientProvider></MemoryRouter>),
  };
}

describe('Phase 07 system configuration UI', () => {
  const seen: SeenRequest[] = [];
  let permissions: string[];
  let userType: 'ADMIN' | 'OPERATOR';
  let view: ReturnType<typeof configurationView>;
  let failStage: boolean;
  let failActivateCode: string | null;

  beforeEach(() => {
    seen.length = 0;
    permissions = [];
    userType = 'ADMIN';
    view = configurationView();
    failStage = false;
    failActivateCode = null;
    useAuthStore.setState({
      accessToken: 'test-token', userType: 'ADMIN', tenantId: null,
      expiresAt: Date.now() + 60_000, principalKey: '7:test-token',
    });
    apiClient.defaults.adapter = async (config: AxiosRequestConfig) => {
      const request = config as InternalAxiosRequestConfig;
      const method = (request.method ?? 'get').toUpperCase();
      const url = request.url ?? '';
      const body = (typeof request.data === 'string' ? JSON.parse(request.data) : request.data) as Record<string, unknown>;
      seen.push({ method, url, body });
      if (url === '/console/account-overview') {
        return axiosResponse(request, apiResponse({
          id: 7, username: 'tester', userType, roleNames: [],
          permissions: permissions.map((code) => ({ code, resourceType: 'API' })),
          lastLoginAt: null, lastLoginIp: null,
        }));
      }
      if (url === '/console/system-configuration' && method === 'GET') {
        return axiosResponse(request, apiResponse(view));
      }
      if (url === '/console/system-configuration/versions' && method === 'POST') {
        if (failStage) throw new Error('synthetic server failure');
        view.draft = { version: 1, baseVersion: 0, changedKeys: ['security.login.max-failures'], reason: String(body.reason), actor: 'tester', createdAt: '2026-09-08T01:00:00' };
        return axiosResponse(request, apiResponse(1));
      }
      if (url === '/console/system-configuration/versions/1/activate' && method === 'POST') {
        if (failActivateCode) {
          throw new AxiosError('activation rejected', 'ERR_BAD_RESPONSE', request, undefined, {
            config: request,
            data: { code: 409, message: 'translated copy may change', data: { errorCode: failActivateCode } },
            headers: {},
            status: 409,
            statusText: 'Conflict',
          });
        }
        view.active = { ...view.active, version: 1, actorUserId: 7, actor: 'tester', activatedAt: '2026-09-08T01:01:00', reloadStatus: 'APPLIED' };
        view.settings[0] = { ...view.settings[0], value: '8' };
        view.history = [{ version: 1, sourceVersion: null, status: 'ACTIVE', changedKeys: ['security.login.max-failures'], reason: 'activate', actor: 'tester', createdAt: '2026-09-08T01:00:00', activatedBy: 7, activatedAt: '2026-09-08T01:01:00', reloadStatus: 'APPLIED', reloadErrorCode: null }];
        view.draft = null;
        return axiosResponse(request, apiResponse({ activeVersion: 1, reloadStatus: 'APPLIED' }));
      }
      return axiosResponse(request, apiResponse(null));
    };
  });

  afterEach(() => {
    delete apiClient.defaults.adapter;
    act(() => useAuthStore.getState().logout());
  });

  it('edits, validates, stages only changed values, and activates the staged version', async () => {
    const { queryClient } = renderPage();
    await screen.findByTestId('admin-platform-system-configuration-page');
    expect(screen.getAllByTestId('admin-platform-system-configuration-setting-value')[0]).toHaveTextContent('5');

    fireEvent.click(screen.getAllByTestId('admin-platform-system-configuration-edit-open')[0]);
    const dialog = screen.getByTestId('admin-platform-system-configuration-edit-dialog');
    fireEvent.change(within(dialog).getByTestId('admin-platform-system-configuration-edit-input'), { target: { value: '2' } });
    expect(within(dialog).getByTestId('admin-platform-system-configuration-validation-error')).toBeVisible();
    fireEvent.change(within(dialog).getByTestId('admin-platform-system-configuration-edit-input'), { target: { value: '8' } });
    fireEvent.click(within(dialog).getByTestId('admin-platform-system-configuration-edit-save'));
    fireEvent.change(screen.getByTestId('admin-platform-system-configuration-draft-reason'), { target: { value: '调整登录保护' } });
    fireEvent.click(screen.getByTestId('admin-platform-system-configuration-draft-save'));

    await waitFor(() => expect(seen).toContainEqual({
      method: 'POST', url: '/console/system-configuration/versions',
      body: { expectedActiveVersion: 0, changes: { 'security.login.max-failures': '8' }, reason: '调整登录保护' },
    }));
    expect(JSON.stringify(seen)).not.toContain('YCS_SMS_EXPORT_SIGNING_KEY');
    await waitFor(() => expect(screen.getByTestId('admin-platform-system-configuration-activate-open')).toBeEnabled());
    fireEvent.click(screen.getByTestId('admin-platform-system-configuration-activate-open'));
    fireEvent.click(within(screen.getByTestId('admin-platform-system-configuration-activate-dialog'))
      .getByTestId('admin-platform-system-configuration-activate-confirm'));
    await screen.findByText(/版本 v1 已激活/);
    expect(screen.getByTestId('admin-platform-system-configuration-active-version')).toHaveTextContent('v1');
    expect(queryClient.isMutating()).toBe(0);
  });

  it('renders explicit read-only and activation-denied states from current permissions', async () => {
    userType = 'OPERATOR';
    permissions = ['system:configuration:read'];
    useAuthStore.setState({ userType: 'OPERATOR' });

    renderPage();
    await screen.findByTestId('admin-platform-system-configuration-write-denied');
    expect(screen.getByTestId('admin-platform-system-configuration-write-denied')).toBeVisible();
    expect(screen.getByTestId('admin-platform-system-configuration-activate-denied')).toBeVisible();
    expect(screen.queryByTestId('admin-platform-system-configuration-edit-open')).not.toBeInTheDocument();
  });

  it('shows persisted safe reload rejection without exposing configuration secrets', async () => {
    view.history = [{ version: 2, sourceVersion: null, status: 'RELOAD_REJECTED', changedKeys: ['security.login.max-failures'], reason: 'unsafe', actor: 'tester', createdAt: '2026-09-08T01:00:00', activatedBy: null, activatedAt: null, reloadStatus: 'REJECTED', reloadErrorCode: 'TEST_REJECTED' }];

    renderPage();
    await screen.findByTestId('admin-platform-system-configuration-page');
    expect(screen.getByTestId('admin-platform-system-configuration-reload-error')).toHaveTextContent('TEST_REJECTED');
    expect(document.body).not.toHaveTextContent('YCS_SMS_EXPORT_SIGNING_KEY');
  });

  it('shows a safe mutation error and preserves the pending edit for retry', async () => {
    failStage = true;
    renderPage();
    await screen.findByTestId('admin-platform-system-configuration-page');

    fireEvent.click(screen.getAllByTestId('admin-platform-system-configuration-edit-open')[0]);
    const dialog = screen.getByTestId('admin-platform-system-configuration-edit-dialog');
    fireEvent.change(within(dialog).getByTestId('admin-platform-system-configuration-edit-input'), {
      target: { value: '8' },
    });
    fireEvent.click(within(dialog).getByTestId('admin-platform-system-configuration-edit-save'));
    fireEvent.change(screen.getByTestId('admin-platform-system-configuration-draft-reason'), {
      target: { value: '保留后重试' },
    });
    fireEvent.click(screen.getByTestId('admin-platform-system-configuration-draft-save'));

    expect(await screen.findByTestId('admin-platform-system-configuration-error'))
      .toHaveTextContent('保存配置失败');
    expect(screen.getByTestId('admin-platform-system-configuration-draft-reason'))
      .toHaveValue('保留后重试');
    expect(screen.getAllByTestId('admin-platform-system-configuration-setting-value')[0])
      .toHaveTextContent('8');
  });

  it('classifies a live reload rejection by stable response code', async () => {
    renderPage();
    await screen.findByTestId('admin-platform-system-configuration-page');
    fireEvent.click(screen.getAllByTestId('admin-platform-system-configuration-edit-open')[0]);
    const dialog = screen.getByTestId('admin-platform-system-configuration-edit-dialog');
    fireEvent.change(within(dialog).getByTestId('admin-platform-system-configuration-edit-input'), {
      target: { value: '8' },
    });
    fireEvent.click(within(dialog).getByTestId('admin-platform-system-configuration-edit-save'));
    fireEvent.change(screen.getByTestId('admin-platform-system-configuration-draft-reason'), {
      target: { value: '测试热加载拒绝' },
    });
    fireEvent.click(screen.getByTestId('admin-platform-system-configuration-draft-save'));
    await waitFor(() => expect(screen.getByTestId('admin-platform-system-configuration-activate-open')).toBeEnabled());

    failActivateCode = 'RELOAD_REJECTED';
    fireEvent.click(screen.getByTestId('admin-platform-system-configuration-activate-open'));
    fireEvent.click(within(screen.getByTestId('admin-platform-system-configuration-activate-dialog'))
      .getByTestId('admin-platform-system-configuration-activate-confirm'));

    expect(await screen.findByTestId('admin-platform-system-configuration-reload-error'))
      .toHaveTextContent('运行时拒绝应用');
  });
});
