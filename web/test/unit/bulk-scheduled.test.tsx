import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { apiClient } from '@/api/client';
import TenantBulkSendPage from '@/pages/tenant/bulk/TenantBulkSendPage';
import TenantScheduledTasksPage from '@/pages/tenant/bulk/TenantScheduledTasksPage';
import AdminBulkDetailsPage from '@/pages/admin/bulk/AdminBulkDetailsPage';
import AdminSendJobsPage from '@/pages/admin/bulk/AdminSendJobsPage';

type SeenRequest = { method: string; url: string; body: Record<string, unknown> };

function apiResponse<T>(data: T): unknown {
  return { code: 200, message: 'OK', data, timestamp: '2026-09-09T00:00:00Z', traceId: 'trace-p29' };
}

function axiosResponse(config: InternalAxiosRequestConfig, data: unknown): AxiosResponse {
  return { config, data, headers: {}, status: 200, statusText: 'OK' };
}

function renderWithQuery(element: React.ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}>{element}</QueryClientProvider>);
}

const task = {
  bulkId: 301,
  tenantId: 42,
  batchKey: 'BULK-301',
  taskName: '批量发送任务',
  messageType: 'NOTIFY',
  priority: 'HIGH',
  totalCount: 3,
  validCount: 2,
  invalidCount: 1,
  runningCount: 1,
  completedCount: 1,
  failCount: 1,
  cancelledCount: 0,
  totalCost: 0.05,
  state: 'RUNNING',
  scheduleAt: '2026-09-10T09:00:00',
  createdAt: '2026-09-09T00:00:00',
  failureReason: null,
  controlReason: null,
};

describe('Phase 29 bulk scheduled pages', () => {
  const seen: SeenRequest[] = [];

  beforeEach(() => {
    seen.length = 0;
    apiClient.defaults.adapter = async (config: AxiosRequestConfig) => {
      const request = config as InternalAxiosRequestConfig;
      const method = (request.method ?? 'get').toUpperCase();
      const url = request.url ?? '';
      const body = typeof request.data === 'string' && request.data ? JSON.parse(request.data) : request.data;
      seen.push({ method, url, body });
      if (url === '/console/tenant/bulk/preview') {
        return axiosResponse(request, apiResponse({
          tenantId: 42,
          batchKey: body.batchKey,
          taskName: body.taskName,
          sourceFileName: 'contacts.csv',
          total: 3,
          valid: 1,
          invalid: 2,
          rows: [
            { rowNo: 1, phoneNumber: '13800138000', maskedPhone: '138****8000', variables: { code: '2468' }, validationStatus: 'VALID', validationReason: null },
            { rowNo: 2, phoneNumber: '13800138000', maskedPhone: '138****8000', variables: { code: '1357' }, validationStatus: 'INVALID', validationReason: '重复手机号' },
          ],
        }));
      }
      if (url === '/console/tenant/bulk/tasks') return axiosResponse(request, apiResponse(task));
      if (url === '/console/tenant/scheduled-tasks') return axiosResponse(request, apiResponse([task]));
      if (url.includes('/console/tenant/scheduled-tasks/301/')) return axiosResponse(request, apiResponse({ ...task, state: 'PAUSED' }));
      if (url === '/console/bulk/tasks') return axiosResponse(request, apiResponse([task]));
      if (url === '/console/bulk/tasks/301') return axiosResponse(request, apiResponse({ task, items: [{ itemId: 1, itemTrackingId: 'BIT-301-1', rowNo: 1, messageId: 'MSG_1', sendStatus: 'PENDING', validationStatus: 'VALID', validationReason: null, cost: 0.05, updatedAt: '2026-09-09T00:00:00' }] }));
      if (url.includes('/console/bulk/tasks/301/')) return axiosResponse(request, apiResponse({ ...task, state: 'PAUSED' }));
      return axiosResponse(request, apiResponse({}));
    };
  });

  afterEach(() => {
    apiClient.defaults.adapter = undefined;
  });

  it('previews and creates a tenant bulk task with validation results', async () => {
    renderWithQuery(<TenantBulkSendPage />);

    expect(screen.getByTestId('tenant-bulk-scheduled-bulk-send-page')).toBeVisible();
    fireEvent.click(screen.getByTestId('tenant-bulk-scheduled-bulk-send-preview'));
    expect(await screen.findByTestId('tenant-bulk-scheduled-bulk-send-validation-results')).toHaveTextContent('重复手机号');
    fireEvent.click(screen.getByTestId('tenant-bulk-scheduled-bulk-send-create'));
    await waitFor(() => expect(seen.some((request) => request.url === '/console/tenant/bulk/tasks')).toBe(true));
    expect(await screen.findByRole('status')).toHaveTextContent('批任务已创建');
  });

  it('shows tenant scheduled task controls', async () => {
    renderWithQuery(<TenantScheduledTasksPage />);

    expect(screen.getByTestId('tenant-bulk-scheduled-scheduled-tasks-page')).toBeVisible();
    expect(await screen.findByTestId('tenant-bulk-scheduled-scheduled-tasks-state')).toHaveTextContent('RUNNING');
    fireEvent.click(screen.getByTestId('tenant-bulk-scheduled-scheduled-tasks-pause'));
    await waitFor(() => expect(seen.some((request) => request.url === '/console/tenant/scheduled-tasks/301/pause')).toBe(true));
  });

  it('shows admin detail reconciliation and operations search controls', async () => {
    renderWithQuery(<AdminBulkDetailsPage />);
    expect(screen.getByTestId('admin-bulk-scheduled-bulk-details-page')).toBeVisible();
    await waitFor(() => expect(screen.getByTestId('admin-bulk-scheduled-bulk-details-table')).toHaveTextContent('BULK-301'));
    expect(await screen.findByTestId('admin-bulk-scheduled-bulk-details-items')).toHaveTextContent('BIT-301-1');

    renderWithQuery(<AdminSendJobsPage />);
    expect(screen.getByTestId('admin-bulk-scheduled-send-jobs-page')).toBeVisible();
    await waitFor(() => expect(screen.getByTestId('admin-bulk-scheduled-send-jobs-control')).toHaveTextContent('BULK-301'));
    fireEvent.click(screen.getByTestId('admin-bulk-scheduled-send-jobs-pause'));
    await waitFor(() => expect(seen.some((request) => request.url === '/console/bulk/tasks/301/pause')).toBe(true));
  });
});
