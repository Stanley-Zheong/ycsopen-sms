import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as identityApi from '@/api/identity';
import * as contractApi from '@/api/contractPricingApi';
import * as trialPrepaidApi from '@/api/trialPrepaidApi';
import TrialPrepaidAdminPage from '@/pages/admin/billing/TrialPrepaidAdminPage';
import TenantConsumptionLedgerPage from '@/pages/tenant/ledger/TenantConsumptionLedgerPage';
import OverviewPage from '@/pages/tenant/overview/OverviewPage';
import { useAuthStore } from '@/store/authStore';

vi.mock('@/api/identity', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/identity')>();
  return { ...actual, getAccountOverview: vi.fn() };
});

vi.mock('@/api/trialPrepaidApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/trialPrepaidApi')>();
  return {
    ...actual,
    getTrialOverview: vi.fn(),
    activateTrial: vi.fn(),
    consumeTrial: vi.fn(),
    requestConversion: vi.fn(),
    listConsumption: vi.fn(),
    listBalanceAudits: vi.fn(),
  };
});

vi.mock('@/api/contractPricingApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/contractPricingApi')>();
  return { ...actual, getContractOverview: vi.fn() };
});

function renderWithProviders(ui: ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
    </MemoryRouter>,
  );
}

describe('Phase 22 trial prepaid ledger UI', () => {
  beforeEach(() => {
    vi.mocked(identityApi.getAccountOverview).mockResolvedValue({
      id: 22,
      username: 'operator-22',
      userType: 'OPERATOR',
      roleNames: ['试用预付费管理员'],
      permissions: [
        { code: 'trial-prepaid:menu', resourceType: 'MENU' },
        { code: 'trial-prepaid:read', resourceType: 'API' },
        { code: 'trial-prepaid:write', resourceType: 'API' },
      ],
      lastLoginAt: null,
      lastLoginIp: null,
    });
    vi.mocked(trialPrepaidApi.getTrialOverview).mockResolvedValue({
      tenantId: 42,
      trialStatus: 'TRIAL',
      quotaTotal: 500,
      quotaRemaining: 498,
      validFrom: '2026-09-09T00:00:00',
      validUntil: '2026-09-23T00:00:00',
      version: 2,
    });
    vi.mocked(trialPrepaidApi.consumeTrial).mockResolvedValue({
      tenantId: 42,
      trialStatus: 'TRIAL',
      quotaTotal: 500,
      quotaRemaining: 497,
      validFrom: '2026-09-09T00:00:00',
      validUntil: '2026-09-23T00:00:00',
      version: 3,
    });
    vi.mocked(trialPrepaidApi.requestConversion).mockResolvedValue({
      id: 1,
      tenantId: 42,
      trialStatus: 'TRIAL',
      status: 'REQUESTED',
    });
    vi.mocked(trialPrepaidApi.activateTrial).mockResolvedValue({
      tenantId: 42,
      trialStatus: 'TRIAL',
      quotaTotal: 500,
      quotaRemaining: 500,
      validFrom: '2026-09-09T00:00:00',
      validUntil: '2026-09-23T00:00:00',
      version: 1,
    });
    vi.mocked(trialPrepaidApi.listConsumption).mockResolvedValue([
      {
        tenantId: 42,
        messageRef: 'MSG-22',
        businessType: 'SMS',
        quotaDelta: -1,
        amountMil: 0,
        entryType: 'TRIAL_CONSUME',
        state: 'CONFIRMED',
        actor: 'tenant',
        createdAt: '2026-09-09T01:00:00',
      },
    ]);
    vi.mocked(trialPrepaidApi.listBalanceAudits).mockResolvedValue([
      {
        tenantId: 42,
        businessDocId: 'DOC-22',
        mutationType: 'RESERVE',
        amountMil: 200,
        beforeBalanceMil: 1000,
        afterBalanceMil: 1000,
        beforeFrozenMil: 0,
        afterFrozenMil: 200,
        accountVersion: 1,
        actor: 'operator',
        createdAt: '2026-09-09T01:00:00',
      },
    ]);
    vi.mocked(contractApi.getContractOverview).mockResolvedValue({
      tenantId: 42,
      tenantState: 'NOT_CONTRACTED',
      billingMode: null,
      priceBookVersion: null,
      contractNo: null,
      signedAt: null,
      attachmentRef: null,
      creditLimitMil: null,
      billingPeriod: null,
      contractStatus: 'NONE',
      approvedBy: null,
    });
    useAuthStore.setState({
      accessToken: 'test-token',
      userType: 'TENANT_ADMIN',
      tenantId: 42,
      expiresAt: Date.now() + 60_000,
      principalKey: '22:test-token',
    });
  });

  afterEach(() => {
    vi.clearAllMocks();
    act(() => useAuthStore.getState().logout());
  });

  it('shows trial status, decrements quota, and requests conversion', async () => {
    renderWithProviders(<OverviewPage />);

    expect(await screen.findByTestId('tenant-trial-prepaid-overview-quota-remaining')).toHaveTextContent('498');
    expect(screen.getByTestId('tenant-trial-prepaid-overview-trial-status')).toHaveTextContent('TRIAL');
    fireEvent.click(screen.getByTestId('tenant-trial-prepaid-overview-consume-trial'));
    await waitFor(() => expect(trialPrepaidApi.consumeTrial).toHaveBeenCalled());
    fireEvent.click(screen.getByTestId('tenant-trial-prepaid-overview-conversion-request'));
    await waitFor(() => expect(screen.getByTestId('tenant-trial-prepaid-overview-message')).toHaveTextContent('REQUESTED'));
  });

  it('filters tenant consumption ledger without edit controls', async () => {
    renderWithProviders(<TenantConsumptionLedgerPage />);

    expect(await screen.findByTestId('tenant-trial-prepaid-consumption-ledger-filters')).toBeVisible();
    expect(await screen.findByTestId('tenant-trial-prepaid-consumption-ledger-row')).toHaveTextContent('MSG-22');
    const panel = screen.getByTestId('query-panel');
    vi.mocked(trialPrepaidApi.listConsumption).mockClear();
    fireEvent.change(screen.getByTestId('tenant-trial-prepaid-consumption-ledger-business-type'), { target: { value: 'SMS' } });
    expect(trialPrepaidApi.listConsumption).not.toHaveBeenCalled();
    fireEvent.click(within(panel).getByTestId('query-submit'));
    await waitFor(() => expect(trialPrepaidApi.listConsumption).toHaveBeenCalledWith(42, 'SMS'));
    expect(within(panel).queryByTestId('query-reset')).not.toBeInTheDocument();
    fireEvent.change(screen.getByTestId('tenant-trial-prepaid-consumption-ledger-business-type'), { target: { value: '' } });
    fireEvent.click(within(panel).getByTestId('query-submit'));
    expect(screen.getByTestId('tenant-trial-prepaid-consumption-ledger-business-type')).toHaveValue('');
    await waitFor(() => expect(trialPrepaidApi.listConsumption).toHaveBeenCalledWith(42, ''));
    expect(within(panel).getByTestId('query-result-table')).toHaveTextContent('MSG-22');
  });

  it('activates trial quota/validity and shows append-only balance audits', async () => {
    useAuthStore.setState({ userType: 'OPERATOR', tenantId: null });
    renderWithProviders(<TrialPrepaidAdminPage />);

    expect(await screen.findByTestId('admin-trial-prepaid-tenant-trial-quota')).toHaveValue(500);
    expect(screen.getByTestId('admin-trial-prepaid-tenant-trial-validity')).toHaveTextContent('有效期结束');
    expect(await screen.findByTestId('admin-trial-prepaid-balance-audit-row')).toHaveTextContent('DOC-22');
    fireEvent.click(screen.getByTestId('admin-trial-prepaid-activate-trial'));
    await waitFor(() => expect(trialPrepaidApi.activateTrial).toHaveBeenCalledWith(42, 500, '2026-09-09T00:00:00', '2026-09-23T00:00:00'));
  });
});
