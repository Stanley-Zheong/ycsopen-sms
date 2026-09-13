import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  completeSettlement,
  financeConfirmStatement,
  generateStatement,
  issueInvoice,
  listAdminInvoices,
  listAdminStatements,
  listDifferences,
  listSettlements,
  markSettlementReceived,
  resolveDifference,
  startSettlement,
  type DifferenceRow,
} from '@/api/reconciliationSettlementApi';
import { mutationErrorMessage } from '@/api/client';
import { protectedQueryKey } from '@/store/authStore';
import '@/styles/tenant-recharge.css';

export default function AdminReconciliationSettlementPage() {
  const [tenantId, setTenantId] = useState('42');
  const [periodStart, setPeriodStart] = useState('2026-09-01');
  const [periodEnd, setPeriodEnd] = useState('2026-09-30');
  const [evidenceRef, setEvidenceRef] = useState('bank-flow-202609');
  const [resolutionNote, setResolutionNote] = useState('已核对详单并修正差异');
  const [invoiceNo, setInvoiceNo] = useState('INV-2026-0001');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const queryClient = useQueryClient();
  const statementsKey = protectedQueryKey('reconciliation-settlement-admin-statements');
  const settlementsKey = protectedQueryKey('reconciliation-settlement-admin-settlements');
  const invoicesKey = protectedQueryKey('reconciliation-settlement-admin-invoices');
  const statements = useQuery({ queryKey: statementsKey, queryFn: listAdminStatements, retry: false });
  const settlements = useQuery({ queryKey: settlementsKey, queryFn: listSettlements, retry: false });
  const invoices = useQuery({ queryKey: invoicesKey, queryFn: listAdminInvoices, retry: false });
  const firstStatementId = statements.data?.[0]?.id ?? 0;
  const differences = useQuery({
    queryKey: protectedQueryKey('reconciliation-settlement-admin-differences', firstStatementId),
    queryFn: () => listDifferences(firstStatementId),
    enabled: firstStatementId > 0,
    retry: false,
  });
  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: statementsKey }),
      queryClient.invalidateQueries({ queryKey: settlementsKey }),
      queryClient.invalidateQueries({ queryKey: invoicesKey }),
      queryClient.invalidateQueries({ queryKey: protectedQueryKey('reconciliation-settlement-admin-differences', firstStatementId) }),
    ]);
  };
  const handleSuccess = async (text: string) => {
    setMessage(text);
    setError('');
    await refresh();
  };
  const handleError = (failure: unknown, text: string) => {
    setError(mutationErrorMessage(failure, text));
    setMessage('');
  };
  const generateMutation = useMutation({
    mutationFn: () => generateStatement(Number(tenantId), { periodStart, periodEnd }),
    onSuccess: (row) => handleSuccess(`对账单已生成：${row.statementNo}`),
    onError: (failure) => handleError(failure, '对账单生成失败'),
  });
  const financeConfirmMutation = useMutation({
    mutationFn: (statementId: number) => financeConfirmStatement(statementId, {
      agree: true, differenceType: null, claimedAmountMil: null, note: null, evidenceRef: null,
    }),
    onSuccess: (row) => handleSuccess(`财务确认完成：${row.reconcileStatus}`),
    onError: (failure) => handleError(failure, '财务确认失败'),
  });
  const resolveMutation = useMutation({
    mutationFn: (difference: DifferenceRow) => resolveDifference(difference.id, resolutionNote),
    onSuccess: (row) => handleSuccess(`差异已处理：${row.reconcileStatus}`),
    onError: (failure) => handleError(failure, '差异处理失败'),
  });
  const startMutation = useMutation({
    mutationFn: (statementId: number) => startSettlement(statementId, evidenceRef),
    onSuccess: (row) => handleSuccess(`结算已发起：${row.status}`),
    onError: (failure) => handleError(failure, '结算发起失败'),
  });
  const completeMutation = useMutation({
    mutationFn: (settlementId: number) => completeSettlement(settlementId, evidenceRef),
    onSuccess: (row) => handleSuccess(`结算已完成：${row.status}`),
    onError: (failure) => handleError(failure, '结算完成失败'),
  });
  const receivedMutation = useMutation({
    mutationFn: (settlementId: number) => markSettlementReceived(settlementId, evidenceRef),
    onSuccess: (row) => handleSuccess(`收款已确认：${row.status}`),
    onError: (failure) => handleError(failure, '收款确认失败'),
  });
  const issueMutation = useMutation({
    mutationFn: (invoiceId: number) => issueInvoice(invoiceId, invoiceNo),
    onSuccess: (row) => handleSuccess(`发票已开具：${row.invoiceNo}`),
    onError: (failure) => handleError(failure, '发票开具失败'),
  });

  return (
    <section className="tenant-recharge-page" data-testid="admin-reconciliation-settlement-reconciliation-page">
      <nav aria-label="面包屑">平台管理 / 财务对账结算</nav>
      <header className="tenant-recharge-header">
        <div>
          <h1>对账、结算与发票</h1>
          <p className="page-description">财务生成来源账单，处理差异，推进结算并开具发票。</p>
        </div>
      </header>
      {message && <p role="status" className="tenant-recharge-alert success" data-testid="admin-reconciliation-settlement-message">{message}</p>}
      {error && <p role="alert" className="tenant-recharge-alert error" data-testid="admin-reconciliation-settlement-error">{error}</p>}

      <section className="card" data-testid="admin-reconciliation-settlement-generate-form">
        <h2>生成账单</h2>
        <div className="tenant-recharge-form">
          <label>机构 ID<input data-testid="admin-reconciliation-settlement-tenant-id" value={tenantId} onChange={(event) => setTenantId(event.target.value)} /></label>
          <label>账期开始<input data-testid="admin-reconciliation-settlement-period-start" type="date" value={periodStart} onChange={(event) => setPeriodStart(event.target.value)} /></label>
          <label>账期结束<input data-testid="admin-reconciliation-settlement-period-end" type="date" value={periodEnd} onChange={(event) => setPeriodEnd(event.target.value)} /></label>
          <button type="button" data-testid="admin-reconciliation-settlement-reconciliation-generate" onClick={() => generateMutation.mutate()}>生成来源对账单</button>
        </div>
      </section>

      <section className="card" data-testid="admin-reconciliation-settlement-reconciliation-table">
        <h2>对账单</h2>
        <table className="ratio-table">
          <thead>
            <tr><th>账单号</th><th>机构</th><th>账期</th><th>发送/成功/计费</th><th>金额(厘)</th><th>对账</th><th>结算</th><th>动作</th></tr>
          </thead>
          <tbody>{(statements.data ?? []).map((row) => (
            <tr key={row.id} data-testid="admin-reconciliation-settlement-reconciliation-row">
              <td>{row.statementNo}</td>
              <td>{row.tenantId}</td>
              <td>{row.periodStart} 至 {row.periodEnd}</td>
              <td>{row.sendCount}/{row.successCount}/{row.billedCount}</td>
              <td>{row.amountDue}</td>
              <td>{row.reconcileStatus}</td>
              <td>{row.settlementStatus}</td>
              <td>
                <button type="button" data-testid="admin-reconciliation-settlement-reconciliation-finance-confirm" disabled={row.reconcileStatus === 'CONFIRMED'} onClick={() => financeConfirmMutation.mutate(row.id)}>财务确认</button>
                <button type="button" data-testid="admin-reconciliation-settlement-settlements-start" disabled={row.reconcileStatus !== 'CONFIRMED' || row.settlementStatus !== 'NOT_SETTLED'} onClick={() => startMutation.mutate(row.id)}>发起结算</button>
              </td>
            </tr>
          ))}</tbody>
        </table>
      </section>

      <section className="card" data-testid="admin-reconciliation-settlement-reconciliation-differences">
        <h2>差异处理</h2>
        <div className="tenant-recharge-form">
          <label>处理说明<input data-testid="admin-reconciliation-settlement-reconciliation-resolution-note" value={resolutionNote} onChange={(event) => setResolutionNote(event.target.value)} /></label>
        </div>
        {(differences.data ?? []).map((difference) => (
          <div key={difference.id} className="trial-prepaid-metric" data-testid="admin-reconciliation-settlement-reconciliation-difference-row">
            <dt>{difference.differenceType} / {difference.claimedAmountMil}</dt>
            <dd>{difference.note} / {difference.status}</dd>
            <button type="button" data-testid="admin-reconciliation-settlement-reconciliation-resolve" disabled={difference.status !== 'OPEN'} onClick={() => resolveMutation.mutate(difference)}>处理差异</button>
          </div>
        ))}
      </section>

      <section className="card" data-testid="admin-reconciliation-settlement-settlements-page">
        <h2>结算单</h2>
        <div className="tenant-recharge-form">
          <label>付款/收款凭证<input data-testid="admin-reconciliation-settlement-settlements-evidence" value={evidenceRef} onChange={(event) => setEvidenceRef(event.target.value)} /></label>
        </div>
        <table className="ratio-table" data-testid="admin-reconciliation-settlement-settlements-table">
          <thead>
            <tr><th>结算 ID</th><th>账单 ID</th><th>机构</th><th>金额</th><th>状态</th><th>凭证</th><th>动作</th></tr>
          </thead>
          <tbody>{(settlements.data ?? []).map((row) => (
            <tr key={row.id} data-testid="admin-reconciliation-settlement-settlements-row">
              <td>{row.id}</td>
              <td>{row.statementId}</td>
              <td>{row.tenantId}</td>
              <td>{row.amountMil}</td>
              <td>{row.status}</td>
              <td>{row.startEvidence}</td>
              <td>
                <button type="button" data-testid="admin-reconciliation-settlement-settlements-complete" disabled={row.status !== 'PENDING_SETTLEMENT'} onClick={() => completeMutation.mutate(row.id)}>完成结算</button>
                <button type="button" data-testid="admin-reconciliation-settlement-settlements-received" disabled={row.status !== 'SETTLED'} onClick={() => receivedMutation.mutate(row.id)}>确认收款</button>
              </td>
            </tr>
          ))}</tbody>
        </table>
      </section>

      <section className="card" data-testid="admin-reconciliation-settlement-invoices-page">
        <h2>发票开具</h2>
        <div className="tenant-recharge-form">
          <label>发票号<input data-testid="admin-reconciliation-settlement-invoices-number" value={invoiceNo} onChange={(event) => setInvoiceNo(event.target.value)} /></label>
        </div>
        <table className="ratio-table" data-testid="admin-reconciliation-settlement-invoices-table">
          <thead>
            <tr><th>发票 ID</th><th>机构</th><th>账单</th><th>金额</th><th>类型</th><th>状态</th><th>发票号</th><th>动作</th></tr>
          </thead>
          <tbody>{(invoices.data ?? []).map((row) => (
            <tr key={row.id} data-testid="admin-reconciliation-settlement-invoices-row">
              <td>{row.id}</td>
              <td>{row.tenantId}</td>
              <td>{row.statementId}</td>
              <td>{row.amount}</td>
              <td>{row.invoiceType}</td>
              <td>{row.status}</td>
              <td>{row.invoiceNo ?? '-'}</td>
              <td><button type="button" data-testid="admin-reconciliation-settlement-invoices-issue" disabled={row.status !== 'PENDING'} onClick={() => issueMutation.mutate(row.id)}>开具发票</button></td>
            </tr>
          ))}</tbody>
        </table>
      </section>
    </section>
  );
}
