import { useQuery } from '@tanstack/react-query';
import { getLoginHistory } from '@/api/identity';
import { useIdentityAccess } from './useIdentityAccess';
import { protectedQueryKey } from '@/store/authStore';

function displayTime(value: string | null): string {
  if (!value) return '暂无';
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString('zh-CN', { hour12: false });
}

export default function AccountOverviewPage() {
  const overview = useIdentityAccess();
  const history = useQuery({
    queryKey: protectedQueryKey('login-history', 'own', 0),
    queryFn: () => getLoginHistory(),
    enabled: Boolean(overview.data?.id),
  });

  if (overview.isLoading) return <div className="card">加载账号概览…</div>;
  if (overview.isError || !overview.data) return <div className="card" role="alert">账号概览加载失败，请稍后重试</div>;

  return (
    <section data-testid="admin-console-identity-account-overview">
      <div className="card">
        <h1>我的账号</h1>
        <dl style={{ display: 'grid', gridTemplateColumns: '140px 1fr', gap: 10 }}>
          <dt>用户名</dt><dd>{overview.data.username}</dd>
          <dt>账号类型</dt><dd>{overview.data.userType}</dd>
          <dt>当前角色</dt><dd>{overview.data.roleNames.join('、') || '未分配角色'}</dd>
          <dt>最近登录时间</dt><dd>{displayTime(overview.data.lastLoginAt)}</dd>
          <dt>最近登录地址</dt><dd>{overview.data.lastLoginIp ?? '暂无'}</dd>
        </dl>
      </div>
      <div className="card">
        <h2>当前权限范围</h2>
        {overview.data.permissions.length ? (
          <div className="permission-scope-groups">
            {(['MENU', 'BUTTON', 'API', 'DATA'] as const).map((type) => (
              <section key={type} aria-label={`${type}权限`}>
                <h3>{{ MENU: '菜单权限', BUTTON: '按钮权限', API: '接口权限', DATA: '数据权限' }[type]}</h3>
                <ul>{overview.data.permissions.filter((item) => item.resourceType === type)
                  .map((item) => <li key={item.code}><code>{item.code}</code></li>)}</ul>
              </section>
            ))}
          </div>
        ) : <p>当前没有额外授权。</p>}
      </div>
      <div className="card" data-testid="shared-console-identity-profile-login-history">
        <h2>登录历史</h2>
        {history.isLoading && <p>加载登录历史…</p>}
        {history.isError && <p role="alert">登录历史加载失败，请稍后重试</p>}
        {history.data && (
          <table className="ratio-table">
            <caption className="visually-hidden">当前账号最近登录记录</caption>
            <thead><tr><th>时间</th><th>登录地址</th><th>客户端</th><th>结果</th></tr></thead>
            <tbody>
              {history.data.items.map((item) => (
                <tr key={item.id}><td>{displayTime(item.occurredAt)}</td><td>{item.loginIp}</td><td>{item.userAgent ?? '未知'}</td><td>{item.outcome === 'SUCCESS_UNUSUAL' ? '异常登录' : item.outcome}</td></tr>
              ))}
            </tbody>
          </table>
        )}
        {history.data?.items.length === 0 && <p>暂无登录记录。成功或失败的登录尝试会在这里显示。</p>}
      </div>
    </section>
  );
}
