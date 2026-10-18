import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
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
    listTrialCandidates: vi.fn(),
    getTrialAnalysis: vi.fn(),
    listActivePriceBooks: vi.fn(),
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

function candidateFixture(): contractApi.TrialCandidate {
  return {
    tenantId: 42,
    tenantNo: 'TENANT-042',
    shortName: 'Acme 短信',
    fullName: 'Acme Messaging Ltd',
    salesOwner: 'Alice',
    industry: 'SaaS',
    configurationSnapshotVersion: 'TRIAL-SNAPSHOT-V1-1234567890ABCDEF',
    lifecycleStatus: 'TRIAL_FROZEN',
    trialStatus: 'TRIAL_FROZEN',
    trialStartAt: '2026-09-01T00:00:00',
    trialEndAt: '2026-09-30T00:00:00',
    remainingDays: 0,
    quotaUsed: 400,
    quotaTotal: 500,
    messageCount: 2,
    successCount: 1,
    successRate: 0.5,
    complaintCount: 1,
    complaintRate: 0.5,
    statisticsAt: '2026-09-07T12:00:00',
    dataQuality: 'COMPLETE',
    sourceRegistry: 'tenants:trial_accounts:message_tasks:complaints',
    conversionEligible: true,
    ineligibilityReasons: [],
  };
}

describe('Phase 37 contract pricing and postpaid UI', () => {
  beforeEach(() => {
    vi.mocked(contractApi.listTrialCandidates).mockResolvedValue([candidateFixture()]);
    vi.mocked(contractApi.listActivePriceBooks).mockResolvedValue([{
      priceBookVersion: 'SMS_STANDARD_V1',
      productCode: 'SMS',
      unitPriceMil: 50,
    }]);
    vi.mocked(contractApi.getTrialAnalysis).mockResolvedValue({
      tenant: candidateFixture(),
      periodStart: '2026-09-01T00:00:00',
      periodEnd: '2026-09-30T00:00:00',
      trend: [{ date: '2026-09-05', messageCount: 2, successCount: 1, failureCount: 1, complaintCount: 1 }],
      messageStatuses: [{ status: 'DELIVERED', count: 1 }, { status: 'FAILED', count: 1 }],
      complaints: [{ id: 7, source: 'OPERATOR', messageId: 'MSG-7', summary: '内容投诉', status: 'PROCESSING', createdAt: '2026-09-05T12:00:00' }],
      sourceRegistry: 'tenants:trial_accounts:message_tasks:complaints',
      statisticsAt: '2026-09-07T12:00:00',
      dataQuality: 'COMPLETE',
    });
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

  it('filters candidates, inspects analysis, and submits the selected tenant contract', async () => {
    renderWithProviders(<TrialPrepaidAdminPage />);

    await screen.findByText('Acme 短信');
    expect(screen.getByTestId('admin-trial-conversion-workbench-table')).toHaveTextContent('TRIAL-SNAPSHOT-V1');
    const filters = screen.getByTestId('admin-trial-conversion-workbench-filters');
    fireEvent.change(screen.getByTestId('admin-trial-conversion-workbench-filter-keyword'), { target: { value: 'Acme' } });
    fireEvent.click(within(filters).getByTestId('query-submit'));
    await waitFor(() => expect(contractApi.listTrialCandidates).toHaveBeenLastCalledWith({
      keyword: 'Acme', salesOwner: '', industry: '', trialStatus: '',
    }));

    fireEvent.click(await screen.findByTestId('admin-trial-conversion-workbench-row-analysis'));
    await waitFor(() => expect(screen.getByTestId('admin-trial-conversion-workbench-analysis-dialog')).toHaveTextContent('内容投诉'));
    fireEvent.click(screen.getByTestId('admin-trial-conversion-workbench-analysis-close'));

    fireEvent.click(screen.getByTestId('admin-trial-conversion-workbench-row-convert'));
    expect(await screen.findByTestId('admin-trial-conversion-workbench-selected-tenant')).toHaveTextContent('TENANT-042');
    expect(screen.getByTestId('admin-contract-pricing-tenant-contract-billing-mode')).toHaveValue('POSTPAID');
    await waitFor(() => expect(screen.getByTestId('admin-contract-pricing-tenant-contract-price-version')).toHaveValue('SMS_STANDARD_V1'));
    expect(screen.getByTestId('admin-contract-pricing-tenant-contract-credit-period')).toHaveTextContent('账期');
    expect(screen.getByTestId('admin-contract-pricing-tenant-contract-postpaid-fields')).toBeVisible();
    expect(screen.getByTestId('admin-contract-pricing-tenant-contract-number')).toHaveValue('');
    fireEvent.change(screen.getByTestId('admin-contract-pricing-tenant-contract-number'), { target: { value: 'HT-2026-0001' } });
    fireEvent.change(screen.getByTestId('admin-contract-pricing-tenant-contract-signed-date'), { target: { value: '2026-09-10' } });
    fireEvent.change(screen.getByTestId('admin-contract-pricing-tenant-contract-attachment'), { target: { value: 'oss://contracts/HT-2026-0001.pdf' } });
    fireEvent.change(screen.getByTestId('admin-contract-pricing-tenant-contract-credit-limit'), { target: { value: '1000000' } });
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

  it('keeps selected-tenant form values after rejection and latches duplicate submit', async () => {
    let rejectApproval!: (reason: unknown) => void;
    vi.mocked(contractApi.approveContract).mockImplementationOnce(() => new Promise((_, reject) => {
      rejectApproval = reject;
    }));
    renderWithProviders(<TrialPrepaidAdminPage />);

    fireEvent.click(await screen.findByTestId('admin-trial-conversion-workbench-row-convert'));
    await waitFor(() => expect(screen.getByTestId('admin-contract-pricing-tenant-contract-price-version')).toHaveValue('SMS_STANDARD_V1'));
    const contractNumber = await screen.findByTestId('admin-contract-pricing-tenant-contract-number');
    fireEvent.change(contractNumber, { target: { value: 'HT-KEEP-122' } });
    fireEvent.change(screen.getByTestId('admin-contract-pricing-tenant-contract-signed-date'), { target: { value: '2026-09-10' } });
    fireEvent.change(screen.getByTestId('admin-contract-pricing-tenant-contract-attachment'), { target: { value: 'oss://contracts/HT-KEEP-122.pdf' } });
    fireEvent.change(screen.getByTestId('admin-contract-pricing-tenant-contract-credit-limit'), { target: { value: '1000000' } });
    const approve = screen.getByTestId('admin-contract-pricing-tenant-contract-approve');
    fireEvent.click(approve);
    fireEvent.click(approve);
    await waitFor(() => expect(contractApi.approveContract).toHaveBeenCalledTimes(1));

    act(() => rejectApproval({ response: { data: { message: '机构生命周期不允许转正式' } } }));
    await waitFor(() => expect(screen.getByTestId('admin-trial-conversion-workbench-conversion-feedback')).toHaveTextContent('机构生命周期不允许转正式'));
    expect(screen.getByTestId('admin-contract-pricing-tenant-contract-number')).toHaveValue('HT-KEEP-122');
    expect(screen.getByTestId('admin-trial-conversion-workbench-selected-tenant')).toHaveTextContent('TENANT-042');
  });

  it('retries active pricing and submits prepaid without postpaid-only fields', async () => {
    vi.mocked(contractApi.listActivePriceBooks)
      .mockRejectedValueOnce(new Error('price unavailable'))
      .mockResolvedValueOnce([{
        priceBookVersion: 'SMS_STANDARD_V1',
        productCode: 'SMS',
        unitPriceMil: 50,
      }]);
    renderWithProviders(<TrialPrepaidAdminPage />);

    fireEvent.click(await screen.findByTestId('admin-trial-conversion-workbench-row-convert'));
    fireEvent.click(await screen.findByTestId('admin-trial-conversion-workbench-price-retry'));
    await waitFor(() => expect(screen.getByTestId('admin-contract-pricing-tenant-contract-price-version'))
      .toHaveValue('SMS_STANDARD_V1'));

    fireEvent.change(screen.getByTestId('admin-contract-pricing-tenant-contract-billing-mode'), {
      target: { value: 'PREPAID' },
    });
    expect(screen.queryByTestId('admin-contract-pricing-tenant-contract-postpaid-fields')).not.toBeInTheDocument();
    fireEvent.change(screen.getByTestId('admin-contract-pricing-tenant-contract-number'), {
      target: { value: 'HT-PREPAID-122' },
    });
    fireEvent.change(screen.getByTestId('admin-contract-pricing-tenant-contract-signed-date'), {
      target: { value: '2026-09-10' },
    });
    fireEvent.change(screen.getByTestId('admin-contract-pricing-tenant-contract-attachment'), {
      target: { value: 'oss://contracts/HT-PREPAID-122.pdf' },
    });
    fireEvent.click(screen.getByTestId('admin-contract-pricing-tenant-contract-approve'));

    await waitFor(() => expect(contractApi.approveContract).toHaveBeenCalledWith(42, {
      billingMode: 'PREPAID',
      priceBookVersion: 'SMS_STANDARD_V1',
      contractNo: 'HT-PREPAID-122',
      signedAt: '2026-09-10',
      attachmentRef: 'oss://contracts/HT-PREPAID-122.pdf',
      creditLimitMil: null,
      billingPeriod: null,
    }));
  });

  it('shows contracted state and effective pricing in tenant overview', async () => {
    useAuthStore.setState({ userType: 'TENANT_ADMIN', tenantId: 42 });
    renderWithProviders(<OverviewPage />);

    await waitFor(() => expect(screen.getByTestId('tenant-contract-pricing-overview-contract-status')).toHaveTextContent('CONTRACTED'));
    expect(screen.getByTestId('tenant-contract-pricing-overview-contract-status')).toHaveTextContent('SMS_STANDARD_V1');
    expect(screen.getByTestId('tenant-contract-pricing-overview-contract-status')).toHaveTextContent('MONTHLY');
  });
});
