import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '@/api/reconciliationSettlementApi';
import AdminReconciliationSettlementPage from '@/pages/admin/billing/AdminReconciliationSettlementPage';
import TenantStatementsInvoicesPage from '@/pages/tenant/billing/TenantStatementsInvoicesPage';
import { useAuthStore } from '@/store/authStore';

vi.mock('@/api/reconciliationSettlementApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/reconciliationSettlementApi')>();
  return {
    ...actual,
    generateStatement: vi.fn(),
    listAdminStatements: vi.fn(),
    listTenantStatements: vi.fn(),
    listDifferences: vi.fn(),
    tenantConfirmStatement: vi.fn(),
    financeConfirmStatement: vi.fn(),
    resolveDifference: vi.fn(),
    startSettlement: vi.fn(),
    listSettlements: vi.fn(),
    completeSettlement: vi.fn(),
    markSettlementReceived: vi.fn(),
    requestInvoice: vi.fn(),
    listTenantInvoices: vi.fn(),
    listAdminInvoices: vi.fn(),
    issueInvoice: vi.fn(),
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

const statement = {
  id: 38,
  tenantId: 42,
  statementNo: 'STMT-42-2026-09-01-2026-09-30',
  periodStart: '2026-09-01',
  periodEnd: '2026-09-30',
  sendCount: 2,
  successCount: 2,
  billedCount: 2,
  amountDue: 300000,
  reconcileStatus: 'PENDING',
  settlementStatus: 'NOT_SETTLED',
  billingMode: 'POSTPAID',
  priceBookVersion: 'SMS_STANDARD_V1',
  disputeNote: null,
  tenantConfirmedAt: null,
  financeConfirmedAt: null,
  confirmedAt: null,
};

describe('Phase 38 reconciliation settlement invoice UI', () => {
  beforeEach(() => {
    vi.mocked(api.listAdminStatements).mockResolvedValue([statement]);
    vi.mocked(api.listTenantStatements).mockResolvedValue([statement]);
    vi.mocked(api.generateStatement).mockResolvedValue(statement);
    vi.mocked(api.financeConfirmStatement).mockResolvedValue({ ...statement, reconcileStatus: 'CONFIRMED', financeConfirmedAt: '2026-09-10T01:00:00' });
    vi.mocked(api.tenantConfirmStatement).mockResolvedValue({ ...statement, reconcileStatus: 'DISPUTED', disputeNote: '机构核对金额不一致' });
    vi.mocked(api.listDifferences).mockResolvedValue([{
      id: 39,
      statementId: 38,
      tenantId: 42,
      differenceType: 'AMOUNT',
      claimedAmountMil: 10000,
      note: '机构核对金额不一致',
      evidenceRef: 'oss://tenant/diff-202609.txt',
      ownerActor: 'tenant',
      status: 'OPEN',
      resolutionNote: null,
    }]);
    vi.mocked(api.resolveDifference).mockResolvedValue(statement);
    vi.mocked(api.listSettlements).mockResolvedValue([{
      id: 40,
      statementId: 38,
      tenantId: 42,
      amountMil: 300000,
      status: 'PENDING_SETTLEMENT',
      startEvidence: 'bank-flow-202609',
      completeEvidence: null,
      receivedEvidence: null,
    }]);
    vi.mocked(api.startSettlement).mockResolvedValue({
      id: 40,
      statementId: 38,
      tenantId: 42,
      amountMil: 300000,
      status: 'PENDING_SETTLEMENT',
      startEvidence: 'bank-flow-202609',
      completeEvidence: null,
      receivedEvidence: null,
    });
    vi.mocked(api.completeSettlement).mockResolvedValue({
      id: 40,
      statementId: 38,
      tenantId: 42,
      amountMil: 300000,
      status: 'SETTLED',
      startEvidence: 'bank-flow-202609',
      completeEvidence: 'bank-flow-202609',
      receivedEvidence: null,
    });
    vi.mocked(api.markSettlementReceived).mockResolvedValue({
      id: 40,
      statementId: 38,
      tenantId: 42,
      amountMil: 300000,
      status: 'RECEIVED',
      startEvidence: 'bank-flow-202609',
      completeEvidence: 'bank-flow-202609',
      receivedEvidence: 'bank-flow-202609',
    });
    vi.mocked(api.listAdminInvoices).mockResolvedValue([{
      id: 41,
      tenantId: 42,
      statementId: 38,
      amount: 100000,
      invoiceType: 'VAT_NORMAL',
      status: 'PENDING',
      invoiceNo: null,
      requestEvidence: '开票资料齐全',
      issuedAt: null,
    }]);
    vi.mocked(api.listTenantInvoices).mockResolvedValue([]);
    vi.mocked(api.requestInvoice).mockResolvedValue({
      id: 41,
      tenantId: 42,
      statementId: 38,
      amount: 100000,
      invoiceType: 'VAT_NORMAL',
      status: 'PENDING',
      invoiceNo: null,
      requestEvidence: '开票资料齐全',
      issuedAt: null,
    });
    vi.mocked(api.issueInvoice).mockResolvedValue({
      id: 41,
      tenantId: 42,
      statementId: 38,
      amount: 100000,
      invoiceType: 'VAT_NORMAL',
      status: 'ISSUED',
      invoiceNo: 'INV-2026-0001',
      requestEvidence: '开票资料齐全',
      issuedAt: '2026-09-10T02:00:00',
    });
    useAuthStore.setState({
      accessToken: 'test-token',
      userType: 'FINANCE',
      tenantId: null,
      expiresAt: Date.now() + 60_000,
      principalKey: 'finance:test-token',
    });
  });

  afterEach(() => {
    vi.clearAllMocks();
    act(() => useAuthStore.getState().logout());
  });

  it('lets finance generate confirm resolve settle receive and issue invoice', async () => {
    renderWithProviders(<AdminReconciliationSettlementPage />);

    expect(await screen.findByTestId('admin-reconciliation-settlement-reconciliation-table')).toBeVisible();
    expect(await screen.findByTestId('admin-reconciliation-settlement-reconciliation-row')).toHaveTextContent('300000');
    fireEvent.click(screen.getByTestId('admin-reconciliation-settlement-reconciliation-generate'));
    await waitFor(() => expect(api.generateStatement).toHaveBeenCalledWith(42, { periodStart: '2026-09-01', periodEnd: '2026-09-30' }));

    fireEvent.click(screen.getByTestId('admin-reconciliation-settlement-reconciliation-finance-confirm'));
    await waitFor(() => expect(api.financeConfirmStatement).toHaveBeenCalledWith(38, {
      agree: true,
      differenceType: null,
      claimedAmountMil: null,
      note: null,
      evidenceRef: null,
    }));

    fireEvent.click(await screen.findByTestId('admin-reconciliation-settlement-reconciliation-resolve'));
    await waitFor(() => expect(api.resolveDifference).toHaveBeenCalledWith(39, '已核对详单并修正差异'));

    fireEvent.click(screen.getByTestId('admin-reconciliation-settlement-settlements-complete'));
    await waitFor(() => expect(api.completeSettlement).toHaveBeenCalledWith(40, 'bank-flow-202609'));

    fireEvent.click(screen.getByTestId('admin-reconciliation-settlement-invoices-issue'));
    await waitFor(() => expect(api.issueInvoice).toHaveBeenCalledWith(41, 'INV-2026-0001'));
  });

  it('lets tenant confirm statement submit difference and request invoice', async () => {
    useAuthStore.setState({ userType: 'TENANT_ADMIN', tenantId: 42, principalKey: '42:test-token' });
    renderWithProviders(<TenantStatementsInvoicesPage />);

    expect(await screen.findByTestId('tenant-reconciliation-settlement-statements-table')).toBeVisible();
    expect(await screen.findByTestId('tenant-reconciliation-settlement-statements-row')).toHaveTextContent('STMT-42');
    fireEvent.click(screen.getByTestId('tenant-reconciliation-settlement-statements-confirm'));
    await waitFor(() => expect(api.tenantConfirmStatement).toHaveBeenCalledWith(38, {
      agree: true,
      differenceType: null,
      claimedAmountMil: null,
      note: null,
      evidenceRef: null,
    }));

    fireEvent.click(screen.getByTestId('tenant-reconciliation-settlement-statements-difference'));
    await waitFor(() => expect(api.tenantConfirmStatement).toHaveBeenCalledWith(38, {
      agree: false,
      differenceType: 'AMOUNT',
      claimedAmountMil: 10000,
      note: '机构核对金额不一致',
      evidenceRef: 'oss://tenant/diff-202609.txt',
    }));

    fireEvent.click(screen.getByTestId('tenant-reconciliation-settlement-invoices-request'));
    await waitFor(() => expect(api.requestInvoice).toHaveBeenCalledWith(42, {
      statementId: 38,
      amountMil: 100000,
      invoiceType: 'VAT_NORMAL',
      evidenceRef: '开票资料齐全',
    }));
  });
});
