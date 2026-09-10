import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as feeApi from '@/api/feeWarningCreditApi';
import AdminFeeWarningPage from '@/pages/admin/billing/AdminFeeWarningPage';
import TenantFeeWarningPage from '@/pages/tenant/billing/TenantFeeWarningPage';

vi.mock('@/api/feeWarningCreditApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/feeWarningCreditApi')>();
  return {
    ...actual,
    listFeeWarningRules: vi.fn(),
    saveFeeWarningRule: vi.fn(),
    evaluateFeeWarning: vi.fn(),
    listFeeWarningEpisodes: vi.fn(),
    approveFeeWarningEpisode: vi.fn(),
    listTenantFeeWarningEpisodes: vi.fn(),
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

const episode = {
  id: 501,
  tenantId: 42,
  ruleId: 9,
  alertRecordId: 3001,
  metricType: 'POSTPAID_CREDIT_RATIO',
  sourceKey: 'fee-warning:42:9:POSTPAID_CREDIT_RATIO',
  sourceAmountMil: 950,
  creditLimitMil: 1000,
  usedAmountMil: 900,
  thresholdValue: 0.85,
  ratio: 0.95,
  action: 'MANUAL_APPROVAL',
  status: 'ACTIVE',
  approvalState: 'PENDING',
  deliveryState: 'DELIVERED',
  sourceSnapshot: 'usedMil=900,estimatedAmountMil=50,creditLimitMil=1000,ratio=0.9500',
  actor: 'finance',
  resolutionNote: null,
  createdAt: '2026-09-10T12:00:00',
  updatedAt: '2026-09-10T12:00:00',
};

describe('Phase 40 fee warning and credit enforcement UI', () => {
  beforeEach(() => {
    vi.mocked(feeApi.listFeeWarningRules).mockResolvedValue([{
      id: 9,
      ruleName: '授信比例预警',
      tenantId: 42,
      metricType: 'POSTPAID_CREDIT_RATIO',
      thresholdValue: 0.85,
      action: 'MANUAL_APPROVAL',
      notifyChannels: '["SMS","EMAIL"]',
      notificationTargets: '["tenant:42","finance","operations"]',
      status: 'ACTIVE',
      updatedAt: '2026-09-10T12:00:00',
    }]);
    vi.mocked(feeApi.listFeeWarningEpisodes).mockResolvedValue([episode]);
    vi.mocked(feeApi.listTenantFeeWarningEpisodes).mockResolvedValue([{ ...episode, metricType: 'PREPAID_AMOUNT', sourceAmountMil: 1000 }]);
    vi.mocked(feeApi.saveFeeWarningRule).mockResolvedValue({ id: 10 } as feeApi.FeeWarningRule);
    vi.mocked(feeApi.evaluateFeeWarning).mockResolvedValue([episode]);
    vi.mocked(feeApi.approveFeeWarningEpisode).mockResolvedValue({ ...episode, status: 'APPROVED', approvalState: 'APPROVED' });
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('configures thresholds, notification targets, and manual approval actions', async () => {
    renderWithProviders(<AdminFeeWarningPage />);

    expect(await screen.findByTestId('admin-fee-warning-credit-page')).toBeVisible();
    expect(screen.getByTestId('admin-fee-warning-fee-warning-targets')).toHaveValue('["tenant:42","finance","operations"]');
    await waitFor(() => expect(screen.getByTestId('admin-fee-warning-fee-warning-credit-action')).toHaveTextContent('MANUAL_APPROVAL'));
    await waitFor(() => expect(screen.getByTestId('admin-fee-warning-fee-warning-enforcement-action')).toHaveTextContent('PENDING'));

    fireEvent.click(screen.getByTestId('admin-fee-warning-fee-warning-rule-save'));
    await waitFor(() => expect(feeApi.saveFeeWarningRule).toHaveBeenCalled());
    fireEvent.click(screen.getByTestId('admin-fee-warning-fee-warning-evaluate'));
    await waitFor(() => expect(feeApi.evaluateFeeWarning).toHaveBeenCalledWith({ tenantId: 42, estimatedAmountMil: 50 }));
    fireEvent.click(screen.getByTestId('admin-fee-warning-fee-warning-approve'));
    await waitFor(() => expect(feeApi.approveFeeWarningEpisode).toHaveBeenCalledWith(501, '允许本次提交'));
  });

  it('shows tenant low balance warning with source amount and delivery state', async () => {
    renderWithProviders(<TenantFeeWarningPage />);

    expect(await screen.findByTestId('tenant-fee-warning-overview-row')).toHaveTextContent('PREPAID_AMOUNT');
    expect(screen.getByTestId('tenant-fee-warning-overview-low-balance-warning')).toHaveTextContent('1000');
    expect(screen.getByTestId('tenant-fee-warning-overview-delivery-evidence')).toHaveTextContent('DELIVERED');
  });
});
