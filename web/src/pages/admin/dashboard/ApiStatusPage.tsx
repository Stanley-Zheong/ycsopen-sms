import { useQuery } from '@tanstack/react-query';
import { getApiStatus } from '@/api/operationalDashboardApi';
import { MetricSourceBlock } from './DashboardPage';

export default function ApiStatusPage() {
  const status = useQuery({ queryKey: ['operational-dashboard-api-status'], queryFn: getApiStatus, retry: false });
  const data = status.data;
  return (
    <section className="card" data-testid="admin-operational-dashboards-api-status-monitor-page">
      <h1>API 状态监控</h1>
      {status.isLoading && <p>正在加载 API 状态…</p>}
      {status.isError && <p role="alert">API 状态加载失败。</p>}
      {data && (
        <>
          <table>
            <thead><tr><th>组件</th><th>状态</th><th>来源</th><th>新鲜度</th><th>影响</th><th>钻取</th></tr></thead>
            <tbody>{data.rows.map((row) => (
              <tr key={row.drilldownKey}>
                <td>{row.component}</td><td>{row.status}</td><td>{row.source}</td><td>{row.freshnessAt || '-'}</td><td>{row.impact}</td><td>{row.drilldownKey}</td>
              </tr>
            ))}</tbody>
          </table>
          <MetricSourceBlock source={data.source} />
        </>
      )}
    </section>
  );
}
