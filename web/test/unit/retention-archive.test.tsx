import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '@/api/retentionArchiveApi';
import AdminRetentionArchivePage from '@/pages/admin/archive/AdminRetentionArchivePage';

vi.mock('@/api/retentionArchiveApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/retentionArchiveApi')>();
  return {
    ...actual,
    listArchivePolicies: vi.fn(),
    saveArchivePolicy: vi.fn(),
    listArchiveManifests: vi.fn(),
    scanArchive: vi.fn(),
    verifyArchiveManifest: vi.fn(),
    restoreArchiveManifest: vi.fn(),
    exportArchiveManifest: vi.fn(),
  };
});

const manifest = {
  id: 47,
  policyId: 1,
  dataDomain: 'MESSAGE_TASKS',
  sourceTable: 'message_tasks',
  partitionKey: '2026-01',
  tenantId: 42,
  archiveStatus: 'COMPLETED',
  rowCount: 2,
  sourceIdentityJson: '{"ids":[1,2]}',
  manifestJson: '{"rowCount":2}',
  checksumSha256: 'abcdef1234567890',
  encryptionKeyVersion: 'archive-v1',
  retentionUntil: '2028-01-01T00:00:00',
  legalHoldUntil: null,
  deletionEligible: false,
  failureReason: null,
  createdBy: '7',
  createdAt: '2026-09-10T08:00:00',
  verifiedAt: null,
  restoredAt: null,
  exportedTaskId: null,
};

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <AdminRetentionArchivePage />
    </QueryClientProvider>,
  );
}

describe('Phase 47 retention archive UI', () => {
  beforeEach(() => {
    vi.mocked(api.listArchivePolicies).mockResolvedValue([
      { id: 1, dataDomain: 'MESSAGE_TASKS', sourceTable: 'message_tasks', retentionDays: 730, hotMonths: 3, partitionUnit: 'MONTH', legalHoldUntil: null, encryptionRequired: true, status: 'ACTIVE', updatedBy: 'phase47', updatedAt: '2026-09-10T08:00:00' },
    ]);
    vi.mocked(api.listArchiveManifests).mockResolvedValue([manifest]);
    vi.mocked(api.saveArchivePolicy).mockResolvedValue({ id: 1, dataDomain: 'MESSAGE_TASKS', sourceTable: 'message_tasks', retentionDays: 730, hotMonths: 3, partitionUnit: 'MONTH', legalHoldUntil: null, encryptionRequired: true, status: 'ACTIVE', updatedBy: '7', updatedAt: '2026-09-10T08:01:00' });
    vi.mocked(api.scanArchive).mockResolvedValue(manifest);
    vi.mocked(api.verifyArchiveManifest).mockResolvedValue({ ...manifest, verifiedAt: '2026-09-10T08:02:00' });
    vi.mocked(api.restoreArchiveManifest).mockResolvedValue({ id: 90, manifestId: 47, requestType: 'RESTORE', status: 'COMPLETED', requestedBy: '7', resultMessage: '已恢复', restoredRecordCount: 2, exportTaskId: null, createdAt: '2026-09-10T08:02:00', completedAt: '2026-09-10T08:02:01' });
    vi.mocked(api.exportArchiveManifest).mockResolvedValue({ id: 91, manifestId: 47, requestType: 'EXPORT', status: 'COMPLETED', requestedBy: '7', resultMessage: '已导出', restoredRecordCount: 2, exportTaskId: 46, createdAt: '2026-09-10T08:03:00', completedAt: '2026-09-10T08:03:01' });
  });

  afterEach(() => vi.clearAllMocks());

  it('shows policy, manifests, verification, restore and export controls', async () => {
    renderPage();

    expect(await screen.findByTestId('admin-retention-archive-page')).toBeVisible();
    await waitFor(() => expect(screen.getByTestId('admin-retention-archive-policy-card')).toHaveTextContent('message_tasks'));
    expect(await screen.findByText('2026-01')).toBeVisible();

    fireEvent.click(screen.getByTestId('admin-retention-archive-policy-save'));
    await waitFor(() => expect(api.saveArchivePolicy).toHaveBeenCalledWith('MESSAGE_TASKS', { retentionDays: 730, hotMonths: 3 }));

    fireEvent.click(screen.getByTestId('admin-retention-archive-scan'));
    await waitFor(() => expect(api.scanArchive).toHaveBeenCalledWith('MESSAGE_TASKS', ''));

    fireEvent.click(screen.getByTestId('admin-retention-archive-manifest-verify'));
    await waitFor(() => expect(api.verifyArchiveManifest).toHaveBeenCalledWith(47));

    fireEvent.click(screen.getByTestId('admin-retention-archive-manifest-restore'));
    await waitFor(() => expect(api.restoreArchiveManifest).toHaveBeenCalledWith(47));

    fireEvent.click(screen.getByTestId('admin-retention-archive-export'));
    await waitFor(() => expect(api.exportArchiveManifest).toHaveBeenCalledWith(47));
  });
});
