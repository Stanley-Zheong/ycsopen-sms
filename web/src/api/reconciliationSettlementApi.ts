import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export interface StatementRow {
  id: number;
  tenantId: number;
  statementNo: string;
  periodStart: string;
  periodEnd: string;
  sendCount: number;
  successCount: number;
  billedCount: number;
  amountDue: number;
  reconcileStatus: string;
  settlementStatus: string;
  billingMode: string;
  priceBookVersion: string;
  disputeNote: string | null;
  tenantConfirmedAt: string | null;
  financeConfirmedAt: string | null;
  confirmedAt: string | null;
}

export interface DifferenceRow {
  id: number;
  statementId: number;
  tenantId: number;
  differenceType: string;
  claimedAmountMil: number;
  note: string;
  evidenceRef: string;
  ownerActor: string;
  status: string;
  resolutionNote: string | null;
}

export interface SettlementRow {
  id: number;
  statementId: number;
  tenantId: number;
  amountMil: number;
  status: string;
  startEvidence: string;
  completeEvidence: string | null;
  receivedEvidence: string | null;
}

export interface InvoiceRow {
  id: number;
  tenantId: number;
  statementId: number | null;
  amount: number;
  invoiceType: string;
  status: string;
  invoiceNo: string | null;
  requestEvidence: string | null;
  issuedAt: string | null;
}

function data<T>(res: { data: ApiResponse<T> }): T {
  return res.data.data;
}

export async function generateStatement(
  tenantId: number,
  input: { periodStart: string; periodEnd: string },
): Promise<StatementRow> {
  return data(await apiClient.post<ApiResponse<StatementRow>>(`/console/reconciliation/statements/tenant/${tenantId}`, input));
}

export async function listAdminStatements(): Promise<StatementRow[]> {
  return data(await apiClient.get<ApiResponse<StatementRow[]>>('/console/reconciliation/statements'));
}

export async function listTenantStatements(tenantId: number): Promise<StatementRow[]> {
  return data(await apiClient.get<ApiResponse<StatementRow[]>>(`/console/reconciliation/statements/tenant/${tenantId}`));
}

export async function listDifferences(statementId: number): Promise<DifferenceRow[]> {
  return data(await apiClient.get<ApiResponse<DifferenceRow[]>>(`/console/reconciliation/statements/${statementId}/differences`));
}

export async function tenantConfirmStatement(
  statementId: number,
  input: { agree: boolean; differenceType: string | null; claimedAmountMil: number | null; note: string | null; evidenceRef: string | null },
): Promise<StatementRow> {
  return data(await apiClient.post<ApiResponse<StatementRow>>(`/console/reconciliation/statements/${statementId}/tenant-confirm`, input));
}

export async function financeConfirmStatement(
  statementId: number,
  input: { agree: boolean; differenceType: string | null; claimedAmountMil: number | null; note: string | null; evidenceRef: string | null },
): Promise<StatementRow> {
  return data(await apiClient.post<ApiResponse<StatementRow>>(`/console/reconciliation/statements/${statementId}/finance-confirm`, input));
}

export async function resolveDifference(differenceId: number, resolutionNote: string): Promise<StatementRow> {
  return data(await apiClient.post<ApiResponse<StatementRow>>(`/console/reconciliation/differences/${differenceId}/resolve`, { resolutionNote }));
}

export async function startSettlement(statementId: number, evidenceRef: string): Promise<SettlementRow> {
  return data(await apiClient.post<ApiResponse<SettlementRow>>(`/console/reconciliation/statements/${statementId}/settlements`, { evidenceRef }));
}

export async function listSettlements(): Promise<SettlementRow[]> {
  return data(await apiClient.get<ApiResponse<SettlementRow[]>>('/console/reconciliation/settlements'));
}

export async function completeSettlement(settlementId: number, evidenceRef: string): Promise<SettlementRow> {
  return data(await apiClient.post<ApiResponse<SettlementRow>>(`/console/reconciliation/settlements/${settlementId}/complete`, { evidenceRef }));
}

export async function markSettlementReceived(settlementId: number, evidenceRef: string): Promise<SettlementRow> {
  return data(await apiClient.post<ApiResponse<SettlementRow>>(`/console/reconciliation/settlements/${settlementId}/received`, { evidenceRef }));
}

export async function requestInvoice(
  tenantId: number,
  input: { statementId: number; amountMil: number; invoiceType: string; evidenceRef: string },
): Promise<InvoiceRow> {
  return data(await apiClient.post<ApiResponse<InvoiceRow>>(`/console/reconciliation/invoices/tenant/${tenantId}`, input));
}

export async function listTenantInvoices(tenantId: number): Promise<InvoiceRow[]> {
  return data(await apiClient.get<ApiResponse<InvoiceRow[]>>(`/console/reconciliation/invoices/tenant/${tenantId}`));
}

export async function listAdminInvoices(): Promise<InvoiceRow[]> {
  return data(await apiClient.get<ApiResponse<InvoiceRow[]>>('/console/reconciliation/invoices'));
}

export async function issueInvoice(invoiceId: number, invoiceNo: string): Promise<InvoiceRow> {
  return data(await apiClient.post<ApiResponse<InvoiceRow>>(`/console/reconciliation/invoices/${invoiceId}/issue`, { invoiceNo }));
}
