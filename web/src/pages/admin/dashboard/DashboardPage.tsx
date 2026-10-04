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
    <main data-testid="admin-dashboard-page">
      <h1>关键指标概览</h1>
      {dashboard.isLoading && <p>正在加载运营仪表盘…</p>}
      {dashboard.isError && <p role="alert">运营仪表盘加载失败，可重试。</p>}
      {!data && refreshButton}
      {data && (
        <>
          <section className="card" data-testid="admin-operational-dashboards-dashboard-realtime-page">
            <div className="dashboard-section-heading">
              <h2>实时概览</h2>
              {refreshButton}
            </div>
            <dl className="dashboard-metric-grid">
              <MetricCard label="总用户" value={data.realtime.totalUsers} />
              <MetricCard label="今日消息" value={data.realtime.todayMessages} />
              <MetricCard label="成功率" value={formatPercent(data.realtime.successRate)} />
              <MetricCard label="活跃机构" value={data.realtime.activeTenants} />
            </dl>
            <TrendChart
              testId="admin-operational-dashboards-dashboard-realtime-chart"
              rows={data.hourlyTrend}
            />
          </section>

          <section className="card" data-testid="admin-operational-dashboards-dashboard-kpi-page">
            <h2>运营 KPI</h2>
            <div className="dashboard-kpi-visuals">
              <GaugeCard label="今日发送" value={data.kpi.todaySend} max={Math.max(data.kpi.todaySend, data.realtime.comparisonMessages, 1)} />
              <GaugeCard label="活跃机构" value={data.kpi.activeTenants} max={Math.max(data.realtime.totalUsers, data.kpi.activeTenants, 1)} />
              <GaugeCard label="成功率" value={Math.round(data.kpi.successRate * 100)} max={100} suffix="%" />
              <GaugeCard label="收入" value={data.kpi.todayRevenue} max={Math.max(data.kpi.todayRevenue, 1)} />
            </div>
            <div className="dashboard-table-alt" data-testid="admin-operational-dashboards-dashboard-realtime-send-trend">
              <table data-testid="admin-operational-dashboards-dashboard-kpi-hourly-trend">
                <thead><tr><th>小时</th><th>发送</th><th>成功</th><th>成功率</th></tr></thead>
                <tbody>{data.hourlyTrend.map((row) => (
                  <tr key={row.bucketStart}><td>{hour(row.bucketStart)}</td><td>{row.sendCount}</td><td>{row.successCount}</td><td>{formatRate(row.successRate)}</td></tr>
                ))}</tbody>
              </table>
            </div>
            <BarChart
              title="机构排行"
              testId="admin-operational-dashboards-dashboard-kpi-tenant-rank"
              bars={data.tenantRank.map((row) => ({
                label: `租户 ${row.tenantId}`,
                value: row.sendCount,
                caption: `成功率 ${formatPercent(row.successRate)}`,
              }))}
            />
            <div className="dashboard-health-chart" data-testid="admin-operational-dashboards-dashboard-kpi-channel-health">
              <StatusBar label="正常" code="NORMAL" value={data.channelHealth.normal} total={channelHealthTotal(data.channelHealth)} tone="ok" />
              <StatusBar label="维护" code="MAINTENANCE" value={data.channelHealth.maintenance} total={channelHealthTotal(data.channelHealth)} tone="warn" />
              <StatusBar label="异常" code="ABNORMAL" value={data.channelHealth.abnormal} total={channelHealthTotal(data.channelHealth)} tone="danger" />
            </div>
          </section>

          <section className="card" data-testid="admin-operational-dashboards-dashboard-finance-warning-card">
            <h2>费用预警</h2>
            <BarChart
              title="活跃预警"
              testId="admin-operational-dashboards-dashboard-finance-warning-chart"
              bars={[
                { label: '活跃预警', value: data.financeWarning.warningCount, caption: `${data.financeWarning.warningCount} 条` },
                { label: '可用余额', value: Math.max(data.kpi.todayRevenue, 1), caption: `收入 ${data.kpi.todayRevenue}` },
              ]}
            />
            <p className="dashboard-source-note">新鲜度：{data.financeWarning.freshnessAt || '-'}</p>
          </section>
          <MetricSourceBlock source={data.source} />
        </>
      )}
      <ComplaintRatioPanel dimension="channel" title="每通道当月投诉占比" />
      <ComplaintRatioPanel dimension="tenant" title="每机构当月投诉占比" />
    </main>
  );
}

export function MetricSourceBlock({ source }: { source: MetricSource }) {
  return (
    <section className="card" data-testid="shared-operational-dashboards-metric-source">
      <h2>指标来源</h2>
      <div className="dashboard-source-grid">
        <MetricCard label="来源" value={source.registry} />
        <MetricCard label="公式" value={source.formula} />
        <MetricCard label="版本" value={source.formulaVersion} />
        <MetricCard label="权限" value={source.permissionScope} />
        <MetricCard label="新鲜度" value={source.freshnessAt || '-'} />
      </div>
    </section>
  );
}

export function formatRate(value: number) {
  return Number(value || 0).toFixed(4);
}

export function formatPercent(value: number) {
  return `${(Number(value || 0) * 100).toFixed(2)}%`;
}

function MetricCard({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="dashboard-metric-card">
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}

function TrendChart({ rows, testId }: { rows: Array<{ bucketStart: string; sendCount: number; successCount: number }>; testId: string }) {
  const max = Math.max(...rows.flatMap((row) => [row.sendCount, row.successCount]), 1);
  return (
    <div className="dashboard-trend-chart" data-testid={testId}>
      {rows.map((row) => (
        <div className="dashboard-trend-column" key={row.bucketStart}>
          <span className="dashboard-trend-bar send" style={{ height: `${Math.max(8, row.sendCount / max * 100)}%` }} />
          <span className="dashboard-trend-bar success" style={{ height: `${Math.max(8, row.successCount / max * 100)}%` }} />
          <small>{hour(row.bucketStart)}</small>
        </div>
      ))}
    </div>
  );
}

function GaugeCard({ label, value, max, suffix = '' }: { label: string; value: number; max: number; suffix?: string }) {
  const percent = Math.max(0, Math.min(100, max === 0 ? 0 : value / max * 100));
  return (
    <div className="dashboard-gauge-card">
      <span>{label}</span>
      <strong>{value}{suffix}</strong>
      <div className="dashboard-gauge-track"><span style={{ width: `${percent}%` }} /></div>
    </div>
  );
}

function BarChart({ title, testId, bars }: { title: string; testId: string; bars: Array<{ label: string; value: number; caption: string }> }) {
  const max = Math.max(...bars.map((bar) => bar.value), 1);
  return (
    <div className="dashboard-bar-chart" data-testid={testId}>
      <h3>{title}</h3>
      {bars.map((bar) => (
        <div className="dashboard-bar-row" key={bar.label}>
          <span>{bar.label}</span>
          <div className="dashboard-bar-track"><span style={{ width: `${Math.max(4, bar.value / max * 100)}%` }} /></div>
          <small>{bar.caption}</small>
        </div>
      ))}
    </div>
  );
}

function StatusBar({ label, code, value, total, tone }: { label: string; code: string; value: number; total: number; tone: 'ok' | 'warn' | 'danger' }) {
  return (
    <div className={`dashboard-status-bar ${tone}`}>
      <span>{label}<span className="visually-hidden"> {code} {value}</span></span>
      <strong>{value}</strong>
      <div><span style={{ width: `${Math.max(4, value / Math.max(total, 1) * 100)}%` }} /></div>
    </div>
  );
}

function channelHealthTotal(value: { normal: number; maintenance: number; abnormal: number }) {
  return value.normal + value.maintenance + value.abnormal;
}

function hour(value: string) {
  return value.slice(11, 16);
}
