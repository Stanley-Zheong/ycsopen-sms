import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { listRechargeReviews, reviewRecharge } from '@/api/tenantRechargeApi';
import { mutationErrorMessage } from '@/api/client';
import { protectedQueryKey } from '@/store/authStore';
import '@/styles/tenant-recharge.css';

function displayTime(value: string): string {
  return new Date(value).toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' });
}

export default function AdminRechargeReviewPage() {
  const [status, setStatus] = useState('PENDING');
  const [reason, setReason] = useState('到账一致');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const queryClient = useQueryClient();
  const queryKey = protectedQueryKey('tenant-recharge-operations-review', status);
  const reviews = useQuery({ queryKey, queryFn: () => listRechargeReviews(status), retry: false });
  const mutation = useMutation({
    mutationFn: ({ id, approved }: { id: number; approved: boolean }) => reviewRecharge(id, { approved, reason }),
    onSuccess: async (record) => {
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

      <section className="card" data-testid="admin-tenant-recharge-operations-review-filter">
        <h2>审核筛选</h2>
        <div className="tenant-recharge-form">
          <label>状态
            <select data-testid="admin-tenant-recharge-operations-review-status" value={status} onChange={(event) => setStatus(event.target.value)}>
              <option value="PENDING">待处理</option>
              <option value="APPROVED">已通过</option>
              <option value="REJECTED">已拒绝</option>
            </select>
          </label>
          <label>审核原因<input data-testid="admin-tenant-recharge-operations-review-reason" value={reason} onChange={(event) => setReason(event.target.value)} /></label>
        </div>
      </section>

      <section className="card" data-testid="admin-tenant-recharge-operations-review-table">
        <h2>充值申请</h2>
        {reviews.isError && <p role="alert">充值审核列表加载失败。</p>}
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
                <button type="button" data-testid="admin-tenant-recharge-operations-review-approve" disabled={row.status !== 'PENDING'} onClick={() => mutation.mutate({ id: row.id, approved: true })}>通过</button>
                <button type="button" data-testid="admin-tenant-recharge-operations-review-reject" disabled={row.status !== 'PENDING'} onClick={() => mutation.mutate({ id: row.id, approved: false })}>拒绝</button>
              </td>
            </tr>
          ))}</tbody>
        </table>
      </section>
    </section>
  );
}
