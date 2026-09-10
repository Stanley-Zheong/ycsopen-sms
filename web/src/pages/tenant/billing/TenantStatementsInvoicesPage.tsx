import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  listTenantInvoices,
  listTenantStatements,
  requestInvoice,
  tenantConfirmStatement,
} from '@/api/reconciliationSettlementApi';
import { mutationErrorMessage } from '@/api/client';
import { protectedQueryKey, useAuthStore } from '@/store/authStore';
import '@/styles/tenant-recharge.css';

export default function TenantStatementsInvoicesPage() {
  const tenantId = useAuthStore((state) => state.tenantId) ?? 42;
  const [differenceType, setDifferenceType] = useState('AMOUNT');
  const [claimedAmountMil, setClaimedAmountMil] = useState('10000');
  const [differenceNote, setDifferenceNote] = useState('机构核对金额不一致');
  const [differenceEvidence, setDifferenceEvidence] = useState('oss://tenant/diff-202609.txt');
  const [invoiceAmount, setInvoiceAmount] = useState('100000');
  const [invoiceType, setInvoiceType] = useState('VAT_NORMAL');
  const [invoiceEvidence, setInvoiceEvidence] = useState('开票资料齐全');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const queryClient = useQueryClient();
  const statementsKey = protectedQueryKey('reconciliation-settlement-tenant-statements', tenantId);
  const invoicesKey = protectedQueryKey('reconciliation-settlement-tenant-invoices', tenantId);
  const statements = useQuery({ queryKey: statementsKey, queryFn: () => listTenantStatements(tenantId), retry: false });
  const invoices = useQuery({ queryKey: invoicesKey, queryFn: () => listTenantInvoices(tenantId), retry: false });
  const selectedStatement = statements.data?.[0];
  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: statementsKey }),
      queryClient.invalidateQueries({ queryKey: invoicesKey }),
    ]);
  };
  const confirmMutation = useMutation({
    mutationFn: (statementId: number) => tenantConfirmStatement(statementId, {
      agree: true, differenceType: null, claimedAmountMil: null, note: null, evidenceRef: null,
    }),
    onSuccess: async (row) => {
      setMessage(`对账确认已提交：${row.reconcileStatus}`);
      setError('');
      await refresh();
    },
    onError: (failure) => {
      setError(mutationErrorMessage(failure, '对账确认失败'));
      setMessage('');
    },
  });
  const differenceMutation = useMutation({
    mutationFn: (statementId: number) => tenantConfirmStatement(statementId, {
      agree: false,
      differenceType,
      claimedAmountMil: Number(claimedAmountMil),
      note: differenceNote,
      evidenceRef: differenceEvidence,
    }),
    onSuccess: async (row) => {
      setMessage(`差异已提交：${row.reconcileStatus}`);
      setError('');
      await refresh();
    },
    onError: (failure) => {
      setError(mutationErrorMessage(failure, '差异提交失败'));
      setMessage('');
    },
  });
  const invoiceMutation = useMutation({
    mutationFn: () => requestInvoice(tenantId, {
      statementId: selectedStatement?.id ?? 0,
      amountMil: Number(invoiceAmount),
      invoiceType,
      evidenceRef: invoiceEvidence,
    }),
    onSuccess: async (row) => {
      setMessage(`发票申请已提交：${row.status}`);
      setError('');
      await refresh();
    },
    onError: (failure) => {
      setError(mutationErrorMessage(failure, '发票申请失败'));
      setMessage('');
    },
  });

  return (
    <section className="tenant-recharge-page" data-testid="tenant-reconciliation-settlement-statements-page">
      <nav aria-label="面包屑">机构端 / 对账与发票</nav>
      <header className="tenant-recharge-header">
        <div>
          <h1>对账单与发票</h1>
          <p className="page-description">机构确认账单、提交差异证据，并在结算后申请发票。</p>
        </div>
      </header>
      {message && <p role="status" className="tenant-recharge-alert success" data-testid="tenant-reconciliation-settlement-message">{message}</p>}
      {error && <p role="alert" className="tenant-recharge-alert error" data-testid="tenant-reconciliation-settlement-error">{error}</p>}

      <section className="card" data-testid="tenant-reconciliation-settlement-statements-table">
        <h2>对账单</h2>
        <table className="ratio-table">
          <thead>
            <tr><th>账单号</th><th>账期</th><th>发送/成功/计费</th><th>金额(厘)</th><th>对账</th><th>结算</th><th>动作</th></tr>
          </thead>
          <tbody>{(statements.data ?? []).map((row) => (
            <tr key={row.id} data-testid="tenant-reconciliation-settlement-statements-row">
              <td>{row.statementNo}</td>
              <td>{row.periodStart} 至 {row.periodEnd}</td>
              <td>{row.sendCount}/{row.successCount}/{row.billedCount}</td>
              <td>{row.amountDue}</td>
              <td>{row.reconcileStatus}</td>
              <td>{row.settlementStatus}</td>
              <td><button type="button" data-testid="tenant-reconciliation-settlement-statements-confirm" disabled={row.reconcileStatus === 'CONFIRMED'} onClick={() => confirmMutation.mutate(row.id)}>确认一致</button></td>
            </tr>
          ))}</tbody>
        </table>
      </section>

      <section className="card" data-testid="tenant-reconciliation-settlement-statements-difference-form">
        <h2>提交差异</h2>
        <div className="tenant-recharge-form">
          <label>差异类型
            <select data-testid="tenant-reconciliation-settlement-statements-difference-type" value={differenceType} onChange={(event) => setDifferenceType(event.target.value)}>
              <option value="COUNT">数量</option>
              <option value="AMOUNT">金额</option>
              <option value="PERIOD">账期</option>
              <option value="OTHER">其他</option>
            </select>
          </label>
          <label>差异金额（厘）<input data-testid="tenant-reconciliation-settlement-statements-difference-amount" type="number" value={claimedAmountMil} onChange={(event) => setClaimedAmountMil(event.target.value)} /></label>
          <label>差异说明<input data-testid="tenant-reconciliation-settlement-statements-difference-note" value={differenceNote} onChange={(event) => setDifferenceNote(event.target.value)} /></label>
          <label>证据地址<input data-testid="tenant-reconciliation-settlement-statements-difference-evidence" value={differenceEvidence} onChange={(event) => setDifferenceEvidence(event.target.value)} /></label>
          <button type="button" data-testid="tenant-reconciliation-settlement-statements-difference" disabled={!selectedStatement} onClick={() => selectedStatement && differenceMutation.mutate(selectedStatement.id)}>提交差异</button>
        </div>
      </section>

      <section className="card" data-testid="tenant-reconciliation-settlement-invoices-page">
        <h2>发票申请</h2>
        <div className="tenant-recharge-form">
          <label>申请金额（厘）<input data-testid="tenant-reconciliation-settlement-invoices-amount" type="number" value={invoiceAmount} onChange={(event) => setInvoiceAmount(event.target.value)} /></label>
          <label>发票类型
            <select data-testid="tenant-reconciliation-settlement-invoices-type" value={invoiceType} onChange={(event) => setInvoiceType(event.target.value)}>
              <option value="VAT_NORMAL">增值税普通发票</option>
              <option value="VAT_SPECIAL">增值税专用发票</option>
              <option value="ELECTRONIC">电子发票</option>
            </select>
          </label>
          <label>申请凭证<input data-testid="tenant-reconciliation-settlement-invoices-evidence" value={invoiceEvidence} onChange={(event) => setInvoiceEvidence(event.target.value)} /></label>
          <button type="button" data-testid="tenant-reconciliation-settlement-invoices-request" disabled={!selectedStatement} onClick={() => invoiceMutation.mutate()}>申请发票</button>
        </div>
        <table className="ratio-table" data-testid="tenant-reconciliation-settlement-invoices-table">
          <thead>
            <tr><th>发票 ID</th><th>账单</th><th>金额</th><th>类型</th><th>状态</th><th>发票号</th></tr>
          </thead>
          <tbody>{(invoices.data ?? []).map((row) => (
            <tr key={row.id} data-testid="tenant-reconciliation-settlement-invoices-row">
              <td>{row.id}</td>
              <td>{row.statementId}</td>
              <td>{row.amount}</td>
              <td>{row.invoiceType}</td>
              <td>{row.status}</td>
              <td>{row.invoiceNo ?? '-'}</td>
            </tr>
          ))}</tbody>
        </table>
      </section>
    </section>
  );
}
