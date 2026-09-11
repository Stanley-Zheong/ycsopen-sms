import { useQuery } from '@tanstack/react-query';
import { getResourceStatistics } from '@/api/operationalDashboardApi';
import { formatRate, MetricSourceBlock } from './DashboardPage';

export default function ResourceStatisticsPage() {
  const stats = useQuery({ queryKey: ['operational-dashboard-resource-statistics'], queryFn: () => getResourceStatistics(), retry: false });
  const data = stats.data;
  return (
    <section className="card" data-testid="admin-operational-dashboards-statistics-resources-page">
      <h1>资源统计</h1>
      {stats.isLoading && <p>正在加载资源统计…</p>}
      {stats.isError && <p role="alert">资源统计加载失败。</p>}
      {data && (
        <>
          <div>空状态：{data.empty ? 'EMPTY' : 'HAS_DATA'} / 错误状态：{data.errorState}</div>
          <table data-testid="admin-operational-dashboards-statistics-resources-table">
            <thead><tr>{data.accessibleColumns.map((column) => <th key={column}>{column}</th>)}</tr></thead>
            <tbody>{data.resources.map((row) => (
              <tr key={`${row.tenantId}-${row.signatureId}-${row.templateId}`}>
                <td>{row.tenantId}</td><td>{row.signatureId ?? '-'}</td><td>{row.templateId ?? '-'}</td>
                <td>{row.submitCount}</td><td>{row.successCount}</td><td>{row.rejectedCount}</td><td>{row.freshnessAt || '-'}</td>
              </tr>
            ))}</tbody>
          </table>
          <table data-testid="admin-operational-dashboards-channel-statistics-comparison">
            <thead><tr><th>租户</th><th>通道</th><th>发送</th><th>成功</th><th>失败</th><th>成功率</th></tr></thead>
            <tbody>{data.channelComparisons.map((row) => (
              <tr key={`${row.tenantId}-${row.channelId}`}>
                <td>{row.tenantId}</td><td>{row.channelId ?? '-'}</td><td>{row.sendCount}</td><td>{row.successCount}</td><td>{row.failureCount}</td><td>{formatRate(row.successRate)}</td>
              </tr>
            ))}</tbody>
          </table>
          <div data-testid="admin-operational-dashboards-statistics-channel-period-compare">
            {data.channelComparisons.map((row) => `${row.channelId ?? '-'}:${formatRate(row.successRate)}`).join(' / ')}
          </div>
          <MetricSourceBlock source={data.source} />
        </>
      )}
    </section>
  );
}
