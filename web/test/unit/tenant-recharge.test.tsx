import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as rechargeApi from '@/api/tenantRechargeApi';
import AdminRechargeReviewPage from '@/pages/admin/billing/AdminRechargeReviewPage';
import TenantRechargePage from '@/pages/tenant/recharge/TenantRechargePage';
import { useAuthStore } from '@/store/authStore';

vi.mock('@/api/tenantRechargeApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/tenantRechargeApi')>();
  return {
    ...actual,
    submitRecharge: vi.fn(),
    listTenantRecharges: vi.fn(),
    listRechargeReviews: vi.fn(),
    reviewRecharge: vi.fn(),
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

describe('Phase 36 tenant recharge operations UI', () => {
  beforeEach(() => {
    vi.mocked(rechargeApi.listTenantRecharges).mockResolvedValue([
      {
        id: 1,
        tenantId: 42,
        amountMil: 100000,
        rechargeMethod: 'BANK_TRANSFER',
        transactionRefMask: 'BANK****0001',
        evidenceText: '银行回单',
        status: 'PENDING',
        submitterActor: 'tenant-user',
        reviewerActor: null,
        reviewReason: null,
        reviewedAt: null,
        createdAt: '2026-09-10T00:00:00',
      },
    ]);
    vi.mocked(rechargeApi.submitRecharge).mockResolvedValue({
      id: 2,
      tenantId: 42,
      amountMil: 200000,
      rechargeMethod: 'ALIPAY',
      transactionRefMask: 'ALI-****0002',
      evidenceText: '支付宝凭证',
      status: 'PENDING',
      submitterActor: 'tenant-user',
      reviewerActor: null,
      reviewReason: null,
      reviewedAt: null,
      createdAt: '2026-09-10T01:00:00',
    });
    vi.mocked(rechargeApi.listRechargeReviews).mockResolvedValue([
      {
        id: 3,
        tenantId: 42,
        amountMil: 300000,
        rechargeMethod: 'WECHAT',
        transactionRefMask: 'WX-R****0003',
        evidenceText: '微信凭证',
        status: 'PENDING',
        submitterActor: 'tenant-user',
        reviewerActor: null,
        reviewReason: null,
        reviewedAt: null,
        createdAt: '2026-09-10T02:00:00',
      },
    ]);
    vi.mocked(rechargeApi.reviewRecharge).mockResolvedValue({
      id: 3,
      tenantId: 42,
      amountMil: 300000,
      rechargeMethod: 'WECHAT',
      transactionRefMask: 'WX-R****0003',
      evidenceText: '微信凭证',
      status: 'APPROVED',
      submitterActor: 'tenant-user',
      reviewerActor: 'finance',
      reviewReason: '到账一致',
      reviewedAt: '2026-09-10T03:00:00',
      createdAt: '2026-09-10T02:00:00',
    });
    useAuthStore.setState({
      accessToken: 'test-token',
      userType: 'TENANT_ADMIN',
      tenantId: 42,
      expiresAt: Date.now() + 60_000,
      principalKey: '42:test-token',
    });
  });

  afterEach(() => {
    vi.clearAllMocks();
    act(() => useAuthStore.getState().logout());
  });

  it('submits amount method transaction evidence and shows processing state', async () => {
    renderWithProviders(<TenantRechargePage />);

    await screen.findByTestId('tenant-recharge-operations-recharge-history');
    expect(screen.queryByTestId('tenant-recharge-operations-recharge-form')).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId('tenant-recharge-operations-recharge-create-open'));
    expect(screen.getByTestId('tenant-recharge-operations-recharge-form')).toBeVisible();
    fireEvent.change(screen.getByTestId('tenant-recharge-operations-recharge-amount'), { target: { value: '200000' } });
    fireEvent.change(screen.getByTestId('tenant-recharge-operations-recharge-method'), { target: { value: 'ALIPAY' } });
    fireEvent.change(screen.getByTestId('tenant-recharge-operations-recharge-transaction'), { target: { value: 'ALI-RECHARGE-0002' } });
    fireEvent.change(screen.getByTestId('tenant-recharge-operations-recharge-evidence'), { target: { value: '支付宝凭证' } });
    fireEvent.click(screen.getByTestId('tenant-recharge-operations-recharge-submit'));

    await waitFor(() => expect(rechargeApi.submitRecharge).toHaveBeenCalledWith(42, {
      amountMil: 200000,
      rechargeMethod: 'ALIPAY',
      transactionRef: 'ALI-RECHARGE-0002',
      evidenceText: '支付宝凭证',
    }));
    expect(await screen.findByTestId('tenant-recharge-operations-recharge-message')).toHaveTextContent('PENDING');
    expect(screen.queryByTestId('tenant-recharge-operations-recharge-form')).not.toBeInTheDocument();
    expect(screen.getByTestId('tenant-recharge-operations-recharge-state')).toHaveTextContent('PENDING');
  });

  it('finance approves or rejects recharge requests from the review page', async () => {
    useAuthStore.setState({ userType: 'FINANCE', tenantId: null });
    renderWithProviders(<AdminRechargeReviewPage />);

    expect(await screen.findByTestId('admin-tenant-recharge-operations-review-table')).toBeVisible();
    expect(await screen.findByTestId('admin-tenant-recharge-operations-review-row')).toHaveTextContent('WX-R****0003');
    fireEvent.change(screen.getByTestId('admin-tenant-recharge-operations-review-reason'), { target: { value: '到账一致' } });
    fireEvent.click(screen.getByTestId('admin-tenant-recharge-operations-review-approve'));

    await waitFor(() => expect(rechargeApi.reviewRecharge).toHaveBeenCalledWith(3, { approved: true, reason: '到账一致' }));
    expect(await screen.findByTestId('admin-tenant-recharge-operations-review-message')).toHaveTextContent('APPROVED');
  });

  it('applies and resets the recharge status only through the shared query panel', async () => {
    useAuthStore.setState({ userType: 'FINANCE', tenantId: null });
    renderWithProviders(<AdminRechargeReviewPage />);

    const panel = screen.getByTestId('query-panel');
    const status = within(panel).getByTestId('admin-tenant-recharge-operations-review-status');
    await waitFor(() => expect(rechargeApi.listRechargeReviews).toHaveBeenCalledWith(''));
    vi.mocked(rechargeApi.listRechargeReviews).mockClear();

    fireEvent.change(status, { target: { value: 'APPROVED' } });
    expect(rechargeApi.listRechargeReviews).not.toHaveBeenCalled();
    fireEvent.click(within(panel).getByTestId('query-submit'));
    await waitFor(() => expect(rechargeApi.listRechargeReviews).toHaveBeenCalledWith('APPROVED'));

    fireEvent.click(within(panel).getByTestId('query-reset'));
    expect(status).toHaveValue('');
    await waitFor(() => expect(rechargeApi.listRechargeReviews).toHaveBeenLastCalledWith(''));
    expect(within(panel).getByTestId('query-result-table')).toContainElement(
      screen.getByTestId('admin-tenant-recharge-operations-review-table'),
    );
    expect(within(panel).queryByTestId('admin-tenant-recharge-operations-review-reason')).not.toBeInTheDocument();
  });
});
