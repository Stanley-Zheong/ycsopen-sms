import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { listTenantRecharges, submitRecharge } from '@/api/tenantRechargeApi';
import { mutationErrorMessage } from '@/api/client';
import ModalDialog from '@/components/common/ModalDialog';
import { protectedQueryKey, useAuthStore } from '@/store/authStore';
import '@/styles/tenant-recharge.css';

function displayTime(value: string): string {
  return new Date(value).toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' });
}

export default function TenantRechargePage() {
  const tenantId = useAuthStore((state) => state.tenantId) ?? 42;
  const queryClient = useQueryClient();
  const [amountMil, setAmountMil] = useState('100000');
  const [rechargeMethod, setRechargeMethod] = useState('BANK_TRANSFER');
  const [transactionRef, setTransactionRef] = useState('BANK-ORDER-001');
  const [evidenceText, setEvidenceText] = useState('银行回单编号或凭证地址');
  const [createOpen, setCreateOpen] = useState(false);
  const [createError, setCreateError] = useState('');
  const [message, setMessage] = useState('');
  const queryKey = protectedQueryKey('tenant-recharge-operations-records', tenantId);
  const records = useQuery({ queryKey, queryFn: () => listTenantRecharges(tenantId), retry: false });
  const mutation = useMutation({
    mutationFn: () => submitRecharge(tenantId, {
      amountMil: Number(amountMil),
      rechargeMethod,
      transactionRef,
      evidenceText,
    }),
    onSuccess: async (record) => {
      setMessage(`充值申请已提交：${record.status}，交易号 ${record.transactionRefMask}`);
      await queryClient.invalidateQueries({ queryKey });
      setCreateOpen(false);
      setCreateError('');
      setAmountMil('100000');
      setRechargeMethod('BANK_TRANSFER');
      setTransactionRef('BANK-ORDER-001');
      setEvidenceText('银行回单编号或凭证地址');
    },
    onError: (failure) => {
      setMessage('');
      setCreateError(mutationErrorMessage(failure, '充值申请提交失败'));
    },
  });

  return (
    <section className="tenant-recharge-page" data-testid="tenant-recharge-operations-recharge-page">
      <nav aria-label="面包屑">机构端 / 账户充值</nav>
      <header className="tenant-recharge-header">
        <div>
          <h1>账户充值</h1>
          <p className="page-description">提交线下充值申请，平台财务审核通过后计入可用余额。</p>
        </div>
        <button type="button" data-testid="tenant-recharge-operations-recharge-create-open" onClick={() => { setCreateError(''); setCreateOpen(true); }}>新建充值申请</button>
      </header>
      {message && <p role="status" className="tenant-recharge-alert success" data-testid="tenant-recharge-operations-recharge-message">{message}</p>}

      {createOpen && (
        <ModalDialog labelledBy="tenant-recharge-create-title" onRequestClose={() => { setCreateOpen(false); setCreateError(''); }}>
          <form className="tenant-recharge-form" data-testid="tenant-recharge-operations-recharge-form" onSubmit={(event) => { event.preventDefault(); mutation.mutate(); }}>
            <h2 id="tenant-recharge-create-title">新建充值申请</h2>
            <label>金额（厘）<input required data-testid="tenant-recharge-operations-recharge-amount" type="number" min="1" value={amountMil} onChange={(event) => setAmountMil(event.target.value)} /></label>
            <label>充值方式
              <select data-testid="tenant-recharge-operations-recharge-method" value={rechargeMethod} onChange={(event) => setRechargeMethod(event.target.value)}><option value="BANK_TRANSFER">银行转账</option><option value="ALIPAY">支付宝</option><option value="WECHAT">微信</option><option value="OFFLINE">线下确认</option></select>
            </label>
            <label>交易号<input required data-testid="tenant-recharge-operations-recharge-transaction" value={transactionRef} onChange={(event) => setTransactionRef(event.target.value)} /></label>
            <label>凭证<input required data-testid="tenant-recharge-operations-recharge-evidence" value={evidenceText} onChange={(event) => setEvidenceText(event.target.value)} /></label>
            {createError && <p role="alert" data-testid="tenant-recharge-operations-recharge-create-error" className="tenant-recharge-alert error">{createError}</p>}
            <div className="dialog-actions"><button type="button" className="button-secondary" data-testid="tenant-recharge-operations-recharge-create-cancel" onClick={() => { setCreateOpen(false); setCreateError(''); }}>取消</button><button type="submit" data-testid="tenant-recharge-operations-recharge-submit" disabled={mutation.isPending}>提交充值申请</button></div>
          </form>
        </ModalDialog>
      )}

      <section className="card" data-testid="tenant-recharge-operations-recharge-history">
        <h2>充值记录</h2>
        {records.isError && <p role="alert">充值记录加载失败。</p>}
        <table className="ratio-table">
          <thead>
            <tr><th>金额(厘)</th><th>方式</th><th>交易号</th><th>凭证</th><th>状态</th><th>审核原因</th><th>提交时间</th></tr>
          </thead>
          <tbody>{(records.data ?? []).map((row) => (
            <tr key={row.id} data-testid="tenant-recharge-operations-recharge-row">
              <td>{row.amountMil}</td>
              <td>{row.rechargeMethod}</td>
              <td>{row.transactionRefMask}</td>
              <td>{row.evidenceText}</td>
              <td data-testid="tenant-recharge-operations-recharge-state">{row.status}</td>
              <td>{row.reviewReason ?? '-'}</td>
              <td>{displayTime(row.createdAt)}</td>
            </tr>
          ))}</tbody>
        </table>
      </section>
    </section>
  );
}
