import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as contractApi from '@/api/contractPricingApi';
import * as operationalDashboardApi from '@/api/operationalDashboardApi';
import * as trialPrepaidApi from '@/api/trialPrepaidApi';
import TrialPrepaidAdminPage from '@/pages/admin/billing/TrialPrepaidAdminPage';
import OverviewPage from '@/pages/tenant/overview/OverviewPage';
import { useAuthStore } from '@/store/authStore';

vi.mock('@/api/contractPricingApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/contractPricingApi')>();
  return {
    ...actual,
    approveContract: vi.fn(),
    getContractOverview: vi.fn(),
  };
});

vi.mock('@/api/trialPrepaidApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/trialPrepaidApi')>();
  return {
    ...actual,
    activateTrial: vi.fn(),
    listBalanceAudits: vi.fn(),
    getTrialOverview: vi.fn(),
    consumeTrial: vi.fn(),
    requestConversion: vi.fn(),
  };
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

describe('Phase 37 contract pricing and postpaid UI', () => {
  beforeEach(() => {
    vi.mocked(trialPrepaidApi.listBalanceAudits).mockResolvedValue([]);
    vi.mocked(trialPrepaidApi.activateTrial).mockResolvedValue({
      tenantId: 42,
      trialStatus: 'TRIAL',
      quotaTotal: 500,
      quotaRemaining: 500,
      validFrom: '2026-09-09T00:00:00',
      validUntil: '2026-09-23T00:00:00',
      version: 1,
    });
    vi.mocked(trialPrepaidApi.getTrialOverview).mockResolvedValue({
      tenantId: 42,
      trialStatus: 'TRIAL_FROZEN',
      quotaTotal: 500,
      quotaRemaining: 0,
      validFrom: '2026-09-09T00:00:00',
      validUntil: '2026-09-23T00:00:00',
      version: 2,
    });
    vi.mocked(trialPrepaidApi.consumeTrial).mockResolvedValue({
      tenantId: 42,
      trialStatus: 'TRIAL_FROZEN',
      quotaTotal: 500,
      quotaRemaining: 0,
      validFrom: '2026-09-09T00:00:00',
      validUntil: '2026-09-23T00:00:00',
      version: 3,
    });
    vi.mocked(trialPrepaidApi.requestConversion).mockResolvedValue({
      id: 1,
      tenantId: 42,
      trialStatus: 'TRIAL_FROZEN',
      status: 'REQUESTED',
    });
    vi.mocked(contractApi.approveContract).mockResolvedValue({
      tenantId: 42,
      billingMode: 'POSTPAID',
      priceBookVersion: 'SMS_STANDARD_V1',
      contractNo: 'HT-2026-0001',
      signedAt: '2026-09-10',
      attachmentRef: 'oss://contracts/HT-2026-0001.pdf',
      creditLimitMil: 1000000,
      billingPeriod: 'MONTHLY',
      contractStatus: 'ACTIVE',
      approvedBy: 'operator',
    });
    vi.mocked(contractApi.getContractOverview).mockResolvedValue({
      tenantId: 42,
      tenantState: 'CONTRACTED',
      billingMode: 'POSTPAID',
      priceBookVersion: 'SMS_STANDARD_V1',
      contractNo: 'HT-2026-0001',
      signedAt: '2026-09-10',
      attachmentRef: 'oss://contracts/HT-2026-0001.pdf',
      creditLimitMil: 1000000,
      billingPeriod: 'MONTHLY',
      contractStatus: 'ACTIVE',
      approvedBy: 'operator',
    });
    vi.mocked(operationalDashboardApi.getTenantOperationalOverview).mockResolvedValue({
      tenantId: 42,
      balanceMil: 1000,
      trialStatus: 'TRIAL_FROZEN',
      contractStatus: 'ACTIVE',
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
      userType: 'ADMIN',
      tenantId: null,
      expiresAt: Date.now() + 60_000,
      principalKey: '37:test-token',
    });
  });

  afterEach(() => {
    vi.clearAllMocks();
    act(() => useAuthStore.getState().logout());
  });

  it('captures billing mode price version contract and postpaid credit period fields', async () => {
    renderWithProviders(<TrialPrepaidAdminPage />);

    expect(await screen.findByTestId('admin-contract-pricing-tenant-contract-billing-mode')).toHaveValue('POSTPAID');
    expect(screen.getByTestId('admin-contract-pricing-tenant-contract-credit-period')).toHaveTextContent('账期');
    expect(screen.getByTestId('admin-contract-pricing-tenant-contract-postpaid-fields')).toBeVisible();
    fireEvent.click(screen.getByTestId('admin-contract-pricing-tenant-contract-approve'));

    await waitFor(() => expect(contractApi.approveContract).toHaveBeenCalledWith(42, {
      billingMode: 'POSTPAID',
      priceBookVersion: 'SMS_STANDARD_V1',
      contractNo: 'HT-2026-0001',
      signedAt: '2026-09-10',
      attachmentRef: 'oss://contracts/HT-2026-0001.pdf',
      creditLimitMil: 1000000,
      billingPeriod: 'MONTHLY',
    }));
    expect(await screen.findByTestId('admin-trial-prepaid-message')).toHaveTextContent('POSTPAID');
  });

  it('shows contracted state and effective pricing in tenant overview', async () => {
    useAuthStore.setState({ userType: 'TENANT_ADMIN', tenantId: 42 });
    renderWithProviders(<OverviewPage />);

    await waitFor(() => expect(screen.getByTestId('tenant-contract-pricing-overview-contract-status')).toHaveTextContent('CONTRACTED'));
    expect(screen.getByTestId('tenant-contract-pricing-overview-contract-status')).toHaveTextContent('SMS_STANDARD_V1');
    expect(screen.getByTestId('tenant-contract-pricing-overview-contract-status')).toHaveTextContent('MONTHLY');
  });
});
