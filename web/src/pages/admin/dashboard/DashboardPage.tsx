import { useQuery } from '@tanstack/react-query';
import { getPlatformDashboard, type MetricSource } from '@/api/operationalDashboardApi';
import ComplaintRatioPanel from './ComplaintRatioPanel';

export default function DashboardPage() {
  const dashboard = useQuery({ queryKey: ['operational-dashboard-platform'], queryFn: getPlatformDashboard, retry: false });
  const data = dashboard.data;
  const refreshButton = (
    <button data-testid="admin-operational-dashboards-dashboard-realtime-refresh" type="button" onClick={() => dashboard.refetch()}>
      {dashboard.isFetching ? '刷新中' : '手动刷新'}
    </button>
  );
  return (
    <div>
      <h1>关键指标概览</h1>
      {dashboard.isLoading && <p>正在加载运营仪表盘…</p>}
      {dashboard.isError && <p role="alert">运营仪表盘加载失败，可重试。</p>}
      {!data && refreshButton}
      {data && (
        <>
          <section className="card" data-testid="admin-operational-dashboards-dashboard-realtime-page">
            <h2>实时概览</h2>
            {refreshButton}
            <dl className="form-grid">
              <div><dt>总用户</dt><dd>{data.realtime.totalUsers}</dd></div>
              <div><dt>今日消息</dt><dd>{data.realtime.todayMessages}</dd></div>
              <div><dt>成功率</dt><dd>{formatRate(data.realtime.successRate)}</dd></div>
              <div><dt>活跃机构</dt><dd>{data.realtime.activeTenants}</dd></div>
            </dl>
          </section>

          <section className="card" data-testid="admin-operational-dashboards-dashboard-kpi-page">
            <h2>运营 KPI</h2>
            <dl className="form-grid">
              <div><dt>今日发送</dt><dd>{data.kpi.todaySend}</dd></div>
              <div><dt>活跃机构</dt><dd>{data.kpi.activeTenants}</dd></div>
              <div><dt>成功率</dt><dd>{formatRate(data.kpi.successRate)}</dd></div>
              <div><dt>收入</dt><dd>{data.kpi.todayRevenue}</dd></div>
            </dl>
            <div data-testid="admin-operational-dashboards-dashboard-realtime-send-trend">
              <table data-testid="admin-operational-dashboards-dashboard-kpi-hourly-trend">
                <thead><tr><th>小时</th><th>发送</th><th>成功</th><th>成功率</th></tr></thead>
                <tbody>{data.hourlyTrend.map((row) => (
                  <tr key={row.bucketStart}><td>{hour(row.bucketStart)}</td><td>{row.sendCount}</td><td>{row.successCount}</td><td>{formatRate(row.successRate)}</td></tr>
                ))}</tbody>
              </table>
            </div>
            <table data-testid="admin-operational-dashboards-dashboard-kpi-tenant-rank">
              <thead><tr><th>租户</th><th>发送</th><th>成功率</th></tr></thead>
              <tbody>{data.tenantRank.map((row) => (
                <tr key={row.tenantId}><td>{row.tenantId}</td><td>{row.sendCount}</td><td>{formatRate(row.successRate)}</td></tr>
              ))}</tbody>
            </table>
            <div data-testid="admin-operational-dashboards-dashboard-kpi-channel-health">
              NORMAL {data.channelHealth.normal} / MAINTENANCE {data.channelHealth.maintenance} / ABNORMAL {data.channelHealth.abnormal}
            </div>
          </section>

          <section className="card" data-testid="admin-operational-dashboards-dashboard-finance-warning-card">
            <h2>费用预警</h2>
            <div>活跃预警：{data.financeWarning.warningCount}</div>
            <div>新鲜度：{data.financeWarning.freshnessAt || '-'}</div>
          </section>
          <MetricSourceBlock source={data.source} />
        </>
      )}
      <ComplaintRatioPanel dimension="channel" title="每通道当月投诉占比" />
      <ComplaintRatioPanel dimension="tenant" title="每机构当月投诉占比" />
    </div>
  );
}

export function MetricSourceBlock({ source }: { source: MetricSource }) {
  return (
    <section className="card" data-testid="shared-operational-dashboards-metric-source">
      <h2>指标来源</h2>
      <div>来源：{source.registry}</div>
      <div>公式：{source.formula}</div>
      <div>版本：{source.formulaVersion}</div>
      <div>权限：{source.permissionScope}</div>
      <div>新鲜度：{source.freshnessAt || '-'}</div>
    </section>
  );
}

export function formatRate(value: number) {
  return Number(value || 0).toFixed(4);
}

function hour(value: string) {
  return value.slice(11, 16);
}
