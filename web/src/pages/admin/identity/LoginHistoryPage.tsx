import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getLoginHistory, IDENTITY_PERMISSIONS } from '@/api/identity';
import { useIdentityAccess } from './useIdentityAccess';
import { protectedQueryKey } from '@/store/authStore';

function displayTime(value: string): string {
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString('zh-CN', { hour12: false });
}

export default function LoginHistoryPage() {
  const access = useIdentityAccess();
  const [userIdFilter, setUserIdFilter] = useState('');
  const [appliedUserId, setAppliedUserId] = useState<number | undefined>();
  const [page, setPage] = useState(0);
  const canViewHistory = access.can(IDENTITY_PERMISSIONS.historyRead);
  const canViewAllHistory = access.can(IDENTITY_PERMISSIONS.historyAll);
  const history = useQuery({
    queryKey: protectedQueryKey('login-history', appliedUserId ?? (canViewAllHistory ? 'all' : 'own'), page),
    queryFn: () => getLoginHistory(appliedUserId, page, 20, canViewAllHistory && appliedUserId === undefined),
    enabled: canViewHistory,
  });

  if (access.isLoading || (canViewHistory && history.isLoading)) return <div className="card">加载登录历史…</div>;
  if (access.isError) return <div className="card" role="alert">权限范围加载失败，请稍后重试</div>;
  if (!canViewHistory) return <div className="card" role="alert">无权查看登录历史</div>;
  if (history.isError) return <div className="card" role="alert">登录历史加载失败，请稍后重试</div>;

  const result = history.data!;
  const hasNextPage = (page + 1) * result.size < result.totalElements;

  return (
    <section className="card" data-testid="shared-console-identity-profile-login-history">
      <h1>登录历史</h1>
      {canViewAllHistory && <form onSubmit={(event) => {
        event.preventDefault();
        setPage(0);
        setAppliedUserId(userIdFilter ? Number(userIdFilter) : undefined);
      }} style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
        <label>用户 ID <input inputMode="numeric" pattern="[0-9]+" value={userIdFilter} onChange={(event) => setUserIdFilter(event.target.value)} /></label>
        <button type="submit">查询</button>
        <button type="button" onClick={() => { setUserIdFilter(''); setAppliedUserId(undefined); setPage(0); }}>重置</button>
      </form>}
      <table className="ratio-table">
        <caption className="visually-hidden">平台账号登录历史</caption>
        <thead><tr><th>用户</th><th>时间</th><th>登录地址</th><th>客户端</th><th>结果</th></tr></thead>
        <tbody>
          {result.items.map((item) => (
            <tr key={item.id}><td>{item.username}</td><td>{displayTime(item.occurredAt)}</td><td>{item.loginIp}</td><td>{item.userAgent ?? '未知'}</td><td>{item.outcome === 'SUCCESS_UNUSUAL' ? '异常登录' : item.outcome}</td></tr>
          ))}
        </tbody>
      </table>
      {result.items.length === 0 && <p>暂无登录记录。成功或失败的登录尝试会在这里显示。</p>}
      <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginTop: 12 }}>
        <button type="button" disabled={page === 0} onClick={() => setPage((current) => current - 1)}>上一页</button>
        <span>第 {page + 1} 页，共 {result.totalElements} 条</span>
        <button type="button" disabled={!hasNextPage} onClick={() => setPage((current) => current + 1)}>下一页</button>
      </div>
    </section>
  );
}
