import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as identityApi from '@/api/identity';
import * as numberApi from '@/api/numberAttributionApi';
import NumberAttributionPage from '@/pages/admin/tools/NumberAttributionPage';
import { useAuthStore } from '@/store/authStore';

vi.mock('@/api/identity', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/identity')>();
  return { ...actual, getAccountOverview: vi.fn() };
});

vi.mock('@/api/numberAttributionApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/numberAttributionApi')>();
  return {
    ...actual,
    listPrefixVersions: vi.fn(),
    importPrefixes: vi.fn(),
    lookupAttribution: vi.fn(),
    listPortabilityRows: vi.fn(),
    savePortability: vi.fn(),
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

describe('Phase 19 number attribution and portability UI', () => {
  beforeEach(() => {
    vi.mocked(identityApi.getAccountOverview).mockResolvedValue({
      id: 7,
      username: 'operator-7',
      userType: 'OPERATOR',
      roleNames: ['号码管理员'],
      permissions: [
        { code: 'number-attribution:menu', resourceType: 'MENU' },
        { code: 'number-attribution:read', resourceType: 'API' },
        { code: 'number-attribution:import', resourceType: 'BUTTON' },
        { code: 'number-attribution:portability', resourceType: 'API' },
      ],
      lastLoginAt: null,
      lastLoginIp: null,
    });
    vi.mocked(numberApi.listPrefixVersions).mockResolvedValue([
      { id: 1, versionNo: 'V20260909', updateType: 'FULL', status: 'ACTIVE', sourceName: '官方号段', totalRows: 2, conflictCount: 0, actor: 'operator', createdAt: '2026-09-09T00:00:00', activatedAt: '2026-09-09T00:00:00' },
    ]);
    vi.mocked(numberApi.listPortabilityRows).mockResolvedValue([
      { id: 2, maskedMobile: '139****0001', originalCarrier: 'MOBILE', currentCarrier: 'TELECOM', portedAt: '2026-09-09', sourceName: '携转缓存', freshnessExpiresAt: '2026-09-10T00:00:00', status: 'ACTIVE', updatedAt: '2026-09-09T00:00:00' },
    ]);
    vi.mocked(numberApi.importPrefixes).mockResolvedValue({ versionNo: 'V20260909', success: 2, failed: 0, errors: [] });
    vi.mocked(numberApi.lookupAttribution).mockResolvedValue({
      mobile: '13912345678',
      carrier: 'UNICOM',
      prefixCarrier: 'MOBILE',
      province: '广东',
      city: '深圳',
      source: 'PORTABILITY_CACHE',
      sourceName: '携转缓存',
      freshnessExpiresAt: '2026-09-10T00:00:00',
      providerFailure: false,
    });
    vi.mocked(numberApi.savePortability).mockResolvedValue({
      id: 2,
      maskedMobile: '139****0001',
      originalCarrier: 'MOBILE',
      currentCarrier: 'TELECOM',
      portedAt: '2026-09-09',
      sourceName: '携转缓存',
      freshnessExpiresAt: '2026-09-10T00:00:00',
      status: 'ACTIVE',
      updatedAt: '2026-09-09T00:00:00',
    });
    useAuthStore.setState({
      accessToken: 'test-token',
      userType: 'OPERATOR',
      tenantId: null,
      expiresAt: Date.now() + 60_000,
      principalKey: '19:test-token',
    });
  });

  afterEach(() => {
    vi.clearAllMocks();
    act(() => useAuthStore.getState().logout());
  });

  it('imports prefixes, queries attribution, and saves protected portability cache', async () => {
    renderWithProviders(<NumberAttributionPage />);

    await screen.findByTestId('admin-number-attribution-portability-attribution-page');
    expect(await screen.findByTestId('admin-prefixes-version-row')).toHaveTextContent('V20260909');
    expect(await screen.findByTestId('admin-number-portability-row')).toHaveTextContent('139****0001');

    fireEvent.click(screen.getByTestId('admin-prefixes-import'));
    await waitFor(() => expect(numberApi.importPrefixes).toHaveBeenCalled());
    fireEvent.click(screen.getByTestId('admin-number-attribution-lookup'));
    await waitFor(() => expect(screen.getByTestId('admin-number-attribution-result')).toHaveTextContent('UNICOM'));
    expect(screen.getByTestId('admin-number-attribution-fallback-source')).toHaveTextContent('PORTABILITY_CACHE');
    fireEvent.click(screen.getByTestId('admin-number-portability-save'));
    await waitFor(() => expect(numberApi.savePortability).toHaveBeenCalled());
  });

  it('groups the lookup controls in the shared compact choice-control layout', async () => {
    renderWithProviders(<NumberAttributionPage />);

    const lookupForm = await screen.findByTestId('admin-number-attribution-lookup-form');
    expect(lookupForm).toHaveClass('number-attribution-lookup-form');
    expect(screen.getByTestId('admin-number-attribution-mobile-label')).toHaveAttribute(
      'for',
      'admin-number-attribution-mobile-input',
    );
    expect(screen.getByTestId('admin-number-attribution-force-provider-failure-label')).toHaveAttribute(
      'for',
      'admin-number-attribution-force-provider-failure-input',
    );
    expect(screen.getByTestId('admin-number-attribution-force-provider-failure')).toHaveAttribute('type', 'checkbox');
  });
});
