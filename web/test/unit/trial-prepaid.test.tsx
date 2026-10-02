import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as identityApi from '@/api/identity';
import * as contractApi from '@/api/contractPricingApi';
import * as operationalDashboardApi from '@/api/operationalDashboardApi';
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

vi.mock('@/api/operationalDashboardApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/operationalDashboardApi')>();
  return { ...actual, getTenantOperationalOverview: vi.fn() };
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
    vi.mocked(operationalDashboardApi.getTenantOperationalOverview).mockResolvedValue({
      tenantId: 42,
      balanceMil: 1000,
      trialStatus: 'TRIAL',
      contractStatus: 'NONE',
      todayMessages: 0,
      successRate: 0,
      serviceStatus: 'NORMAL',
      source: {
        registry: 'statistics_aggregates',
        formula: 'success_count/send_count',
        freshnessAt: '2026-09-10T09:00:00',
        permissionScope: 'TENANT',
        formulaVersion: 'v1',
      },
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
    fireEvent.change(screen.getByTestId('tenant-trial-prepaid-consumption-ledger-business-type'), { target: { value: 'SMS' } });
    fireEvent.click(screen.getByTestId('query-submit'));
    await waitFor(() => expect(trialPrepaidApi.listConsumption).toHaveBeenCalledWith(42, 'SMS'));
  });

  it('retries a failed ledger query when the submitted filter is unchanged', async () => {
    vi.mocked(trialPrepaidApi.listConsumption).mockReset();
    vi.mocked(trialPrepaidApi.listConsumption)
      .mockResolvedValueOnce([
        {
          tenantId: 42,
          messageRef: 'MSG-INITIAL',
          businessType: 'NOTICE',
          quotaDelta: -1,
          amountMil: 0,
          entryType: 'TRIAL_CONSUME',
          state: 'CONFIRMED',
          actor: 'tenant',
          createdAt: '2026-09-09T01:00:00',
        },
      ])
      .mockRejectedValueOnce(new Error('ledger unavailable'))
      .mockResolvedValueOnce([
        {
          tenantId: 42,
          messageRef: 'MSG-RETRY',
          businessType: 'SMS',
          quotaDelta: -1,
          amountMil: 0,
          entryType: 'TRIAL_CONSUME',
          state: 'CONFIRMED',
          actor: 'tenant',
          createdAt: '2026-09-09T01:00:00',
        },
      ])
      .mockResolvedValue([]);

    renderWithProviders(<TenantConsumptionLedgerPage />);

    await waitFor(() => expect(screen.getByTestId('tenant-trial-prepaid-consumption-ledger-query-status')).toHaveAttribute('data-state', 'success'));
    fireEvent.change(screen.getByTestId('tenant-trial-prepaid-consumption-ledger-business-type'), { target: { value: 'SMS' } });
    fireEvent.click(screen.getByTestId('query-submit'));
    await waitFor(() => expect(screen.getByTestId('tenant-trial-prepaid-consumption-ledger-query-status')).toHaveAttribute('data-state', 'error'));

    fireEvent.click(screen.getByTestId('query-submit'));

    await waitFor(() => expect(trialPrepaidApi.listConsumption).toHaveBeenCalledTimes(3));
    expect(await screen.findByTestId('tenant-trial-prepaid-consumption-ledger-row')).toHaveTextContent('MSG-RETRY');
    expect(screen.getByTestId('tenant-trial-prepaid-consumption-ledger-query-status')).toHaveAttribute('data-state', 'success');
  });

  it('keeps legacy ledger loading and error selectors as inert aliases of the four-state status', async () => {
    let rejectLedger!: (reason?: unknown) => void;
    vi.mocked(trialPrepaidApi.listConsumption).mockReset();
    vi.mocked(trialPrepaidApi.listConsumption)
      .mockImplementationOnce(() => new Promise<Awaited<ReturnType<typeof trialPrepaidApi.listConsumption>>>((_, reject) => {
        rejectLedger = reject;
      }))
      .mockResolvedValue([]);

    renderWithProviders(<TenantConsumptionLedgerPage />);

    const result = screen.getByTestId('query-result-table');
    expect(screen.getByTestId('tenant-trial-prepaid-consumption-ledger-query-status')).toHaveAttribute('data-state', 'loading');
    expect(screen.getByTestId('tenant-trial-prepaid-consumption-ledger-loading')).toHaveAttribute(
      'data-query-status-alias-for',
      'tenant-trial-prepaid-consumption-ledger-query-status',
    );
    expect(screen.getByTestId('tenant-trial-prepaid-consumption-ledger-loading')).toHaveAttribute('aria-hidden', 'true');
    expect(result.querySelectorAll('[role="status"], [role="alert"]')).toHaveLength(1);

    act(() => rejectLedger(new Error('ledger unavailable')));

    await waitFor(() => expect(screen.getByTestId('tenant-trial-prepaid-consumption-ledger-query-status')).toHaveAttribute('data-state', 'error'));
    expect(screen.queryByTestId('tenant-trial-prepaid-consumption-ledger-loading')).not.toBeInTheDocument();
    expect(screen.getByTestId('tenant-trial-prepaid-consumption-ledger-error')).toHaveAttribute(
      'data-query-status-alias-for',
      'tenant-trial-prepaid-consumption-ledger-query-status',
    );
    expect(screen.getByTestId('tenant-trial-prepaid-consumption-ledger-error')).toHaveAttribute('aria-hidden', 'true');
    expect(result.querySelectorAll('[role="status"], [role="alert"]')).toHaveLength(1);
  });

  it('activates trial quota/validity and shows append-only balance audits', async () => {
    useAuthStore.setState({ userType: 'OPERATOR', tenantId: null });
    renderWithProviders(<TrialPrepaidAdminPage />);

    expect(await screen.findByTestId('admin-trial-prepaid-tenant-trial-quota')).toHaveValue(500);
    expect(screen.getByTestId('admin-trial-prepaid-tenant-trial-validity')).toHaveTextContent('有效期结束');
    expect(await screen.findByTestId('admin-trial-prepaid-balance-audit-row')).toHaveTextContent('DOC-22');
    const queryTenant = screen.getByTestId('admin-trial-prepaid-balance-audit-tenant-filter');
    const queryPanel = screen.getByTestId('query-panel');
    expect(queryPanel).toContainElement(queryTenant);
    expect(queryPanel.querySelectorAll('input, select, textarea')).toHaveLength(1);
    expect(queryPanel).not.toContainElement(screen.getByTestId('admin-trial-prepaid-tenant-id'));
    expect(queryPanel).not.toContainElement(screen.getByTestId('admin-trial-prepaid-activate-trial'));
    expect(queryPanel).not.toContainElement(screen.getByTestId('admin-secure-async-balance-audit-export'));
    expect(screen.getByTestId('query-label-balance-audit-tenant')).toHaveTextContent('机构 ID');
    fireEvent.change(queryTenant, { target: { value: '99' } });
    expect(trialPrepaidApi.listBalanceAudits).not.toHaveBeenCalledWith(99);
    fireEvent.click(screen.getByTestId('query-submit'));
    await waitFor(() => expect(trialPrepaidApi.listBalanceAudits).toHaveBeenCalledWith(99));
    fireEvent.click(screen.getByTestId('admin-trial-prepaid-activate-trial'));
    await waitFor(() => expect(trialPrepaidApi.activateTrial).toHaveBeenCalledWith(42, 500, '2026-09-09T00:00:00', '2026-09-23T00:00:00'));
    expect(screen.getByTestId('admin-trial-prepaid-tenant-id')).toHaveValue('42');
  });

  it('does not load balance audits through refresh while read access is loading', () => {
    vi.mocked(identityApi.getAccountOverview).mockImplementationOnce(() => new Promise(() => undefined));
    useAuthStore.setState({ userType: 'OPERATOR', tenantId: null });
    renderWithProviders(<TrialPrepaidAdminPage />);

    expect(screen.getByTestId('query-submit')).toBeDisabled();
    expect(screen.getByTestId('query-refresh')).toBeDisabled();
    fireEvent.click(screen.getByTestId('query-refresh'));

    expect(trialPrepaidApi.listBalanceAudits).not.toHaveBeenCalled();
  });
});
