import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { listRechargeReviews, reviewRecharge, type RechargeRecord } from '@/api/tenantRechargeApi';
import { mutationErrorMessage } from '@/api/client';
import ActionReasonDialog from '@/components/common/ActionReasonDialog';
import { QueryField, QueryPanel } from '@/components/common/QueryPanel';
import { protectedQueryKey } from '@/store/authStore';
import '@/styles/tenant-recharge.css';

function displayTime(value: string): string {
  return new Date(value).toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' });
}

export default function AdminRechargeReviewPage() {
  const [draftStatus, setDraftStatus] = useState('PENDING');
  const [appliedStatus, setAppliedStatus] = useState('PENDING');
  const [decision, setDecision] = useState<{ row: RechargeRecord; approved: boolean } | null>(null);
  const [reason, setReason] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const queryClient = useQueryClient();
  const queryKey = protectedQueryKey('tenant-recharge-operations-review', appliedStatus);
  const reviews = useQuery({ queryKey, queryFn: () => listRechargeReviews(appliedStatus), retry: false });
  const mutation = useMutation({
    mutationFn: ({ row, approved, actionReason }: { row: RechargeRecord; approved: boolean; actionReason: string }) => reviewRecharge(row.id, { approved, reason: actionReason }),
    onSuccess: async (record) => {
      setDecision(null);
      setReason('');
      setMessage(`充值审核已处理：${record.status}`);
      setError('');
      await queryClient.invalidateQueries({ queryKey });
    },
    onError: (failure) => {
      setError(mutationErrorMessage(failure, '充值审核处理失败'));
      setMessage('');
    },
  });

  return (
    <section className="tenant-recharge-page" data-testid="admin-tenant-recharge-operations-review-page">
      <nav aria-label="面包屑">平台管理 / 充值审核</nav>
      <header className="tenant-recharge-header">
        <div>
          <h1>账户充值审核</h1>
          <p className="page-description">财务核对机构充值凭证，审核通过后对预付费可用余额执行一次性入账。</p>
        </div>
      </header>
      {message && <p role="status" className="tenant-recharge-alert success" data-testid="admin-tenant-recharge-operations-review-message">{message}</p>}
      {error && <p role="alert" className="tenant-recharge-alert error" data-testid="admin-tenant-recharge-operations-review-error">{error}</p>}

      <QueryPanel
        legacyPanelTestId="admin-tenant-recharge-operations-review-filter"
        onSubmit={() => setAppliedStatus(draftStatus)}
        onReset={() => {
          setDraftStatus('PENDING');
          setAppliedStatus('PENDING');
        }}
        result={(
          <section className="card" data-testid="admin-tenant-recharge-operations-review-table">
            <h2>充值申请</h2>
            {reviews.isLoading && <p>正在加载充值审核列表…</p>}
            {reviews.isError && <p role="alert">充值审核列表加载失败。</p>}
            {!reviews.isLoading && !reviews.isError && (reviews.data ?? []).length === 0 && <p>暂无充值申请。</p>}
            <table className="ratio-table">
              <thead>
                <tr><th>机构</th><th>金额(厘)</th><th>方式</th><th>交易号</th><th>凭证</th><th>状态</th><th>提交人</th><th>提交时间</th><th>动作</th></tr>
              </thead>
              <tbody>{(reviews.data ?? []).map((row) => (
                <tr key={row.id} data-testid="admin-tenant-recharge-operations-review-row">
                  <td>{row.tenantId}</td>
                  <td>{row.amountMil}</td>
                  <td>{row.rechargeMethod}</td>
                  <td>{row.transactionRefMask}</td>
                  <td>{row.evidenceText}</td>
                  <td>{row.status}</td>
                  <td>{row.submitterActor}</td>
                  <td>{displayTime(row.createdAt)}</td>
                  <td>
                    <button type="button" data-testid="admin-tenant-recharge-operations-review-approve" disabled={row.status !== 'PENDING'} onClick={() => { setDecision({ row, approved: true }); setReason(''); }}>通过</button>
                    <button type="button" data-testid="admin-tenant-recharge-operations-review-reject" disabled={row.status !== 'PENDING'} onClick={() => { setDecision({ row, approved: false }); setReason(''); }}>拒绝</button>
                  </td>
                </tr>
              ))}</tbody>
            </table>
          </section>
        )}
      >
        <QueryField name="status" label="状态">
          <select data-testid="admin-tenant-recharge-operations-review-status" value={draftStatus} onChange={(event) => setDraftStatus(event.target.value)}>
              <option value="PENDING">待处理</option>
              <option value="APPROVED">已通过</option>
              <option value="REJECTED">已拒绝</option>
          </select>
        </QueryField>
      </QueryPanel>

      {decision && (
        <ActionReasonDialog
          idPrefix="admin-tenant-recharge-operations-review-action"
          title={`确认${decision.approved ? '通过' : '拒绝'}充值申请`}
          target={`充值申请 #${decision.row.id} · 机构 ${decision.row.tenantId} · ${decision.row.amountMil} 厘`}
          consequence={decision.approved ? '确认后，本次金额将一次性计入机构预付费可用余额。' : '确认后，本次申请将被拒绝，所填原因会保存在审核记录中。'}
          reasonLabel={`${decision.approved ? '通过' : '拒绝'}原因`}
          reasonTestId="admin-tenant-recharge-operations-review-reason"
          reason={reason}
          placeholder={decision.approved ? '请填写到账核对依据' : '请填写拒绝依据，便于提交人核对'}
          maxLength={255}
          confirmLabel={`确认${decision.approved ? '通过' : '拒绝'}`}
          pending={mutation.isPending}
          onReasonChange={setReason}
          onCancel={() => { setDecision(null); setReason(''); }}
          onConfirm={() => mutation.mutate({ ...decision, actionReason: reason.trim() })}
        />
      )}
    </section>
  );
}
