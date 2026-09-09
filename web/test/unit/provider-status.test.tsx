import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as identityApi from '@/api/identity';
import * as providerStatusApi from '@/api/providerStatusApi';
import ProviderStatusPage from '@/pages/admin/tools/ProviderStatusPage';
import { useAuthStore } from '@/store/authStore';

vi.mock('@/api/identity', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/identity')>();
  return { ...actual, getAccountOverview: vi.fn() };
});

vi.mock('@/api/providerStatusApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/providerStatusApi')>();
  return {
    ...actual,
    listStatusVersions: vi.fn(),
    listStatusMappings: vi.fn(),
    importStatusMappings: vi.fn(),
    normalizeStatus: vi.fn(),
    requestStatusExport: vi.fn(),
  };
});

function renderWithProviders(ui: ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
    </MemoryRouter>,
  );
}

describe('Phase 20 provider status taxonomy UI', () => {
  beforeEach(() => {
    vi.mocked(identityApi.getAccountOverview).mockResolvedValue({
      id: 20,
      username: 'operator-20',
      userType: 'OPERATOR',
      roleNames: ['状态码管理员'],
      permissions: [
        { code: 'provider-status:menu', resourceType: 'MENU' },
        { code: 'provider-status:read', resourceType: 'API' },
        { code: 'provider-status:import', resourceType: 'BUTTON' },
        { code: 'provider-status:export', resourceType: 'BUTTON' },
      ],
      lastLoginAt: null,
      lastLoginIp: null,
    });
    vi.mocked(providerStatusApi.listStatusVersions).mockResolvedValue([
      { id: 1, versionNo: 'ST20260909', status: 'ACTIVE', sourceName: '供应商文档', effectiveAt: '2026-09-09T00:00:00', conflictCount: 0, actor: 'operator', createdAt: '2026-09-09T00:00:00' },
    ]);
    vi.mocked(providerStatusApi.listStatusMappings).mockResolvedValue([
      { providerName: 'YTO', protocol: 'HTTP', providerCode: 'DELIVRD', platformCategory: 'SUCCESS', finalState: true, billable: true, retryable: false, severity: 'INFO', advice: '确认送达' },
    ]);
    vi.mocked(providerStatusApi.importStatusMappings).mockResolvedValue({ versionNo: 'ST20260909', success: 1, failed: 0, errors: [] });
    vi.mocked(providerStatusApi.normalizeStatus).mockResolvedValue({
      providerName: 'YTO',
      protocol: 'HTTP',
      providerCode: 'DELIVRD',
      versionNo: 'ST20260909',
      platformCategory: 'SUCCESS',
      finalState: true,
      billable: true,
      retryable: false,
      severity: 'INFO',
      advice: '确认送达',
      source: 'MAPPED',
    });
    vi.mocked(providerStatusApi.requestStatusExport).mockResolvedValue({ requestId: 'STATUS_EXPORT_1', matchedRows: 1, status: 'REQUESTED' });
    useAuthStore.setState({
      accessToken: 'test-token',
      userType: 'OPERATOR',
      tenantId: null,
      expiresAt: Date.now() + 60_000,
      principalKey: '20:test-token',
    });
  });

  afterEach(() => {
    vi.clearAllMocks();
    act(() => useAuthStore.getState().logout());
  });

  it('imports, normalizes, exports, and shows version history', async () => {
    renderWithProviders(<ProviderStatusPage />);

    await screen.findByTestId('admin-provider-status-taxonomy-status-codes-page');
    expect(await screen.findByTestId('admin-provider-status-taxonomy-status-codes-version-row')).toHaveTextContent('ST20260909');
    expect(await screen.findByTestId('admin-provider-status-taxonomy-status-codes-row')).toHaveTextContent('DELIVRD');

    fireEvent.click(screen.getByTestId('admin-provider-status-taxonomy-status-codes-import'));
    await waitFor(() => expect(providerStatusApi.importStatusMappings).toHaveBeenCalled());
    fireEvent.click(screen.getByTestId('admin-provider-status-taxonomy-normalize'));
    await waitFor(() => expect(screen.getByTestId('admin-provider-status-taxonomy-normalized-result')).toHaveTextContent('SUCCESS'));
    expect(screen.getByTestId('admin-provider-status-taxonomy-unknown-fallback')).toHaveTextContent('MAPPED');
    fireEvent.click(screen.getByTestId('admin-provider-status-taxonomy-status-codes-export'));
    await waitFor(() => expect(providerStatusApi.requestStatusExport).toHaveBeenCalled());
  });
});
