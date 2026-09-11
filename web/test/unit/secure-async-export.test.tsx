import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '@/api/secureAsyncExportApi';
import AdminExportCenterPage from '@/pages/admin/exports/AdminExportCenterPage';

vi.mock('@/api/secureAsyncExportApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/secureAsyncExportApi')>();
  return {
    ...actual,
    listSecureExports: vi.fn(),
    retrySecureExport: vi.fn(),
    downloadSecureExport: vi.fn(),
  };
});

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <AdminExportCenterPage />
    </QueryClientProvider>,
  );
}

describe('Phase 46 secure async export UI', () => {
  beforeEach(() => {
    vi.mocked(api.listSecureExports).mockResolvedValue([
      { id: 46, requestId: 'REQ-46', tenantId: 42, exportType: 'SEND_DETAIL', producer: 'MESSAGE_OPERATIONS', jobName: '发送详单导出', createdBy: '7', format: 'CSV', status: 'COMPLETED', progress: 100, recordCount: 2, fileSizeBytes: 128, fileSha256: 'abc', encryptionState: 'ENCRYPTED', retryCount: 0, splitCount: 1, partialFailureCount: 0, failureReason: null, createdAt: '2026-09-10T08:00:00', completedAt: '2026-09-10T08:00:01' },
      { id: 47, requestId: 'REQ-47', tenantId: 42, exportType: 'RECEIPT_DETAIL', producer: 'MESSAGE_OPERATIONS', jobName: '回执详单导出', createdBy: '7', format: 'JSON', status: 'FAILED', progress: 40, recordCount: 1, fileSizeBytes: 64, fileSha256: 'def', encryptionState: 'ENCRYPTED', retryCount: 0, splitCount: 1, partialFailureCount: 1, failureReason: '测试失败', createdAt: '2026-09-10T08:01:00', completedAt: null },
    ]);
    vi.mocked(api.downloadSecureExport).mockResolvedValue({ id: 46, requestId: 'REQ-46', fileName: 'send_detail-46.csv', format: 'CSV', encryptionState: 'ENCRYPTED', artifactBase64: 'ZmFrZQ==', sha256: 'abc', sizeBytes: 128 });
    vi.mocked(api.retrySecureExport).mockResolvedValue({ id: 47, requestId: 'REQ-47', tenantId: 42, exportType: 'RECEIPT_DETAIL', producer: 'MESSAGE_OPERATIONS', jobName: '回执详单导出', createdBy: '7', format: 'JSON', status: 'COMPLETED', progress: 100, recordCount: 1, fileSizeBytes: 64, fileSha256: 'def', encryptionState: 'ENCRYPTED', retryCount: 1, splitCount: 1, partialFailureCount: 0, failureReason: null, createdAt: '2026-09-10T08:01:00', completedAt: '2026-09-10T08:02:00' });
  });

  afterEach(() => vi.clearAllMocks());

  it('shows cards, rows, encrypted download and retry controls', async () => {
    renderPage();

    expect(await screen.findByTestId('admin-secure-async-export-center-page')).toBeVisible();
    expect(screen.getByTestId('admin-secure-async-export-center-cards')).toHaveTextContent('总任务');
    expect(await screen.findByText('发送详单导出')).toBeVisible();
    fireEvent.click(screen.getAllByTestId('admin-secure-async-export-center-download')[0]);
    await waitFor(() => expect(api.downloadSecureExport).toHaveBeenCalledWith(46));
    expect(await screen.findByTestId('admin-secure-async-export-center-message')).toHaveTextContent('已获取加密下载包');

    fireEvent.click(screen.getAllByTestId('admin-secure-async-export-center-retry')[1]);
    await waitFor(() => expect(api.retrySecureExport).toHaveBeenCalledWith(47, '人工确认重试失败导出'));
  });
});
