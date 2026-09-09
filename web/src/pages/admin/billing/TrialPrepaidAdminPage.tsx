import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  activateTrial,
  listBalanceAudits,
  TRIAL_PREPAID_PERMISSIONS,
} from '@/api/trialPrepaidApi';
import { mutationErrorMessage } from '@/api/client';
import { useIdentityAccess } from '@/pages/admin/identity/useIdentityAccess';
import { isPlatformRole, protectedQueryKey, useAuthStore } from '@/store/authStore';
import '@/styles/trial-prepaid.css';

function displayTime(value: string): string {
  return new Date(value).toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' });
}

export default function TrialPrepaidAdminPage() {
  const userType = useAuthStore((state) => state.userType);
  const platformRole = isPlatformRole(userType);
  const admin = userType === 'ADMIN';
  const access = useIdentityAccess(platformRole);
  const canRead = admin || access.can(TRIAL_PREPAID_PERMISSIONS.read);
  const canWrite = admin || access.can(TRIAL_PREPAID_PERMISSIONS.write);
  const queryClient = useQueryClient();
  const [tenantId, setTenantId] = useState('42');
  const [quota, setQuota] = useState('500');
  const [startAt, setStartAt] = useState('2026-09-09T00:00');
  const [endAt, setEndAt] = useState('2026-09-23T00:00');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const auditKey = protectedQueryKey('trial-prepaid-balance-audits', tenantId);
  const audits = useQuery({
    queryKey: auditKey,
    queryFn: () => listBalanceAudits(tenantId ? Number(tenantId) : null),
    enabled: platformRole && canRead,
    retry: false,
  });
  const activateMutation = useMutation({
    mutationFn: () => activateTrial(Number(tenantId), Number(quota), startAt ? `${startAt}:00` : null, endAt ? `${endAt}:00` : null),
    onSuccess: async (overview) => {
      setMessage(`试用已启用：${overview.quotaRemaining}/${overview.quotaTotal}，有效期至 ${overview.validUntil}`);
      setError('');
      await queryClient.invalidateQueries({ queryKey: auditKey });
    },
    onError: (failure) => {
      setError(mutationErrorMessage(failure, '试用配置保存失败'));
      setMessage('');
    },
  });

  if (!platformRole || (!canRead && !access.isLoading)) {
    return <p role="alert" data-testid="admin-trial-prepaid-access-denied">无权查看试用和余额账本。</p>;
  }

  return (
    <section className="trial-prepaid-page" data-testid="admin-trial-prepaid-page">
      <nav aria-label="面包屑">平台管理 / 试用与预付费</nav>
      <header className="trial-prepaid-header">
        <div>
          <h1>试用配置与余额审计</h1>
          <p className="page-description">平台侧维护机构试用额度和有效期，并查看预付费余额所有追加审计记录。</p>
        </div>
      </header>
      {message && <p role="status" data-testid="admin-trial-prepaid-message" className="trial-prepaid-alert success">{message}</p>}
      {error && <p role="alert" data-testid="admin-trial-prepaid-error" className="trial-prepaid-alert error">{error}</p>}

      <section className="card">
        <h2>机构试用配置</h2>
        <div className="trial-prepaid-form">
          <label>机构 ID<input data-testid="admin-trial-prepaid-tenant-id" value={tenantId} onChange={(event) => setTenantId(event.target.value)} /></label>
          <label>试用额度<input data-testid="admin-trial-prepaid-tenant-trial-quota" type="number" min="1" value={quota} onChange={(event) => setQuota(event.target.value)} /></label>
          <label data-testid="admin-trial-prepaid-tenant-trial-validity">
            有效期结束
            <input data-testid="admin-trial-prepaid-tenant-trial-validity-end" type="datetime-local" value={endAt} onChange={(event) => setEndAt(event.target.value)} />
          </label>
          <label>
            有效期开始
            <input data-testid="admin-trial-prepaid-tenant-trial-validity-start" type="datetime-local" value={startAt} onChange={(event) => setStartAt(event.target.value)} />
          </label>
          <button type="button" data-testid="admin-trial-prepaid-activate-trial" disabled={!canWrite} onClick={() => activateMutation.mutate()}>启用/调整试用</button>
        </div>
      </section>

      <section className="card" data-testid="admin-balance-audit">
        <h2>余额审计</h2>
        {audits.isError && <p role="alert" data-testid="admin-trial-prepaid-balance-audit-error">余额审计加载失败。</p>}
        <table className="ratio-table" data-testid="admin-trial-prepaid-balance-audit-table">
          <thead>
            <tr><th>机构</th><th>业务单</th><th>类型</th><th>金额(厘)</th><th>余额前/后</th><th>冻结前/后</th><th>版本</th><th>操作人</th><th>时间</th></tr>
          </thead>
          <tbody>{(audits.data ?? []).map((row) => (
            <tr key={`${row.businessDocId}-${row.mutationType}-${row.createdAt}`} data-testid="admin-trial-prepaid-balance-audit-row">
              <td>{row.tenantId}</td>
              <td>{row.businessDocId}</td>
              <td>{row.mutationType}</td>
              <td>{row.amountMil}</td>
              <td>{row.beforeBalanceMil}/{row.afterBalanceMil}</td>
              <td>{row.beforeFrozenMil}/{row.afterFrozenMil}</td>
              <td>{row.accountVersion}</td>
              <td>{row.actor}</td>
              <td>{displayTime(row.createdAt)}</td>
            </tr>
          ))}</tbody>
        </table>
      </section>
    </section>
  );
}
