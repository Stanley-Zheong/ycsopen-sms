import { useQuery } from '@tanstack/react-query';
import {
  getPlatformDashboard,
  type MetricSource,
  type TodayAggregation,
  type TodayAggregationState,
} from '@/api/operationalDashboardApi';
import ComplaintRatioPanel from './ComplaintRatioPanel';

export default function DashboardPage() {
  const dashboard = useQuery({ queryKey: ['operational-dashboard-platform'], queryFn: getPlatformDashboard, retry: false });
  const data = dashboard.data;
  const refreshButton = (
    <button
      data-testid="admin-operational-dashboards-dashboard-realtime-refresh"
      type="button"
      disabled={dashboard.isFetching}
      onClick={() => dashboard.refetch()}
    >
      {dashboard.isFetching ? '刷新中' : '手动刷新'}
    </button>
  );
  const aggregation = data?.todayAggregation ?? null;
  const aggregateValuesAvailable = aggregation?.state === 'FRESH'
    || aggregation?.state === 'STALE' && aggregation.aggregateRowCount > 0;
  const todayMessages = aggregateValuesAvailable ? data?.realtime.todayMessages ?? null : null;
  const realtimeSuccessRate = aggregateValuesAvailable ? data?.realtime.successRate ?? null : null;
  const comparisonMessages = aggregateValuesAvailable ? data?.realtime.comparisonMessages ?? null : null;
  const todaySend = aggregateValuesAvailable ? data?.kpi.todaySend ?? null : null;
  const kpiSuccessRate = aggregateValuesAvailable ? data?.kpi.successRate ?? null : null;
  const todayRevenue = aggregateValuesAvailable ? data?.kpi.todayRevenue ?? null : null;
  return (
    <main data-testid="admin-dashboard-page">
      <h1>关键指标概览</h1>
      <DashboardDataStatus loading={dashboard.isLoading} error={dashboard.isError && !data} aggregation={aggregation} />
      {dashboard.isError && data && (
        <p className="dashboard-aggregation-status error" role="alert">刷新失败，当前仍显示上一次加载的数据。</p>
      )}
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
              <MetricCard testId="admin-operational-dashboards-dashboard-realtime-today-messages-value" label="今日消息" value={metricValue(todayMessages)} />
              <MetricCard testId="admin-operational-dashboards-dashboard-realtime-success-rate-value" label="成功率" value={formatPercent(realtimeSuccessRate)} />
              <MetricCard testId="admin-operational-dashboards-dashboard-realtime-comparison-value" label="对比消息" value={metricValue(comparisonMessages)} />
              <MetricCard label="活跃机构" value={data.realtime.activeTenants} />
            </dl>
            {aggregateValuesAvailable ? (
              <TrendChart
                testId="admin-operational-dashboards-dashboard-realtime-chart"
                rows={data.hourlyTrend}
              />
            ) : (
              <UnavailableData testId="admin-operational-dashboards-dashboard-realtime-chart" message="今日趋势暂不可用。" />
            )}
          </section>

          <section className="card" data-testid="admin-operational-dashboards-dashboard-kpi-page">
            <h2>运营 KPI</h2>
            <div className="dashboard-kpi-visuals">
              <GaugeCard testId="admin-operational-dashboards-dashboard-kpi-today-send-value" label="今日发送" value={todaySend} max={Math.max(todaySend ?? 0, comparisonMessages ?? 0, 1)} />
              <GaugeCard label="活跃机构" value={data.kpi.activeTenants} max={Math.max(data.realtime.totalUsers, data.kpi.activeTenants, 1)} />
              <GaugeCard testId="admin-operational-dashboards-dashboard-kpi-success-rate-value" label="成功率" value={kpiSuccessRate === null ? null : Math.round(kpiSuccessRate * 100)} max={100} suffix="%" />
              <GaugeCard testId="admin-operational-dashboards-dashboard-kpi-revenue-value" label="收入" value={todayRevenue} max={Math.max(todayRevenue ?? 0, 1)} />
            </div>
            <div className="dashboard-table-alt" data-testid="admin-operational-dashboards-dashboard-realtime-send-trend">
              {aggregateValuesAvailable ? (
                <table data-testid="admin-operational-dashboards-dashboard-kpi-hourly-trend">
                  <thead><tr><th>小时</th><th>发送</th><th>成功</th><th>成功率</th></tr></thead>
                  <tbody>
                    {data.hourlyTrend.length === 0 ? (
                      <tr data-testid="table-empty"><td colSpan={4}>今日暂无趋势明细。</td></tr>
                    ) : data.hourlyTrend.map((row) => (
                      <tr key={row.bucketStart}><td>{hour(row.bucketStart)}</td><td>{row.sendCount}</td><td>{row.successCount}</td><td>{formatRate(row.successRate)}</td></tr>
                    ))}
                  </tbody>
                </table>
              ) : <p className="dashboard-unavailable">今日趋势暂不可用。</p>}
            </div>
            {aggregateValuesAvailable ? (
              <BarChart
                title="机构排行"
                testId="admin-operational-dashboards-dashboard-kpi-tenant-rank"
                emptyMessage="今日暂无机构排行数据。"
                bars={data.tenantRank.map((row) => ({
                  label: `租户 ${row.tenantId}`,
                  value: row.sendCount,
                  caption: `成功率 ${formatPercent(row.successRate)}`,
                }))}
              />
            ) : (
              <UnavailableData testId="admin-operational-dashboards-dashboard-kpi-tenant-rank" message="机构排行暂不可用。" title="机构排行" />
            )}
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
                ...(todayRevenue === null ? [] : [{ label: '可用余额', value: Math.max(todayRevenue, 1), caption: `收入 ${todayRevenue}` }]),
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

function DashboardDataStatus({ loading, error, aggregation }: { loading: boolean; error: boolean; aggregation: TodayAggregation | null }) {
  if (loading) {
    return <section className="dashboard-aggregation-status" data-testid="admin-operational-dashboards-dashboard-data-status" data-state="LOADING" role="status">正在加载运营仪表盘…</section>;
  }
  if (error || !aggregation) {
    return <section className="dashboard-aggregation-status error" data-testid="admin-operational-dashboards-dashboard-data-status" data-state="ERROR" role="alert">运营仪表盘加载失败，可重试。</section>;
  }
  const copy: Record<TodayAggregationState, { title: string; detail: string }> = {
    NOT_REFRESHED: { title: '统计尚未刷新', detail: '当前业务日的统计管道尚未完成，请稍后手动刷新页面。' },
    EMPTY: { title: '今日无消息数据', detail: '统计已刷新，本业务日没有消息或拒绝请求。' },
    STALE: { title: '数据已过期', detail: '当前显示上一次刷新结果，请根据时间信息谨慎使用。' },
    FRESH: { title: '数据已刷新', detail: '今日统计与已发现的源数据同步。' },
  };
  const stateCopy = aggregation.state === 'STALE' && aggregation.aggregateRowCount === 0
    ? { title: '数据已过期', detail: '最近一次刷新为空结果且已过期，当前不展示消息统计值。' }
    : copy[aggregation.state];
  return (
    <section
      className={`dashboard-aggregation-status ${aggregation.state.toLowerCase()}`}
      data-testid="admin-operational-dashboards-dashboard-data-status"
      data-state={aggregation.state}
      role="status"
      aria-live="polite"
    >
      <strong>{stateCopy.title}</strong>
      <span>{stateCopy.detail}</span>
      <span data-testid="admin-operational-dashboards-dashboard-aggregation-business-date">
        业务日期：{aggregation.businessDate} / {aggregation.businessTimeZone}
      </span>
      <span data-testid="admin-operational-dashboards-dashboard-aggregation-freshness">
        来源：{aggregation.sourceRegistry} / 刷新时间：{aggregation.refreshedAt ?? '尚无成功刷新'} / 源数据变化：{aggregation.sourceChangedAt ?? '尚无源数据变化时间'}
      </span>
    </section>
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

export function formatPercent(value: number | null) {
  if (value === null) return '—';
  return `${(Number(value || 0) * 100).toFixed(2)}%`;
}

function metricValue(value: number | null) {
  return value === null ? '—' : value;
}

function MetricCard({ label, value, testId }: { label: string; value: string | number; testId?: string }) {
  return (
    <div className="dashboard-metric-card">
      <dt>{label}</dt>
      <dd data-testid={testId}>{value}</dd>
    </div>
  );
}

function TrendChart({ rows, testId }: { rows: Array<{ bucketStart: string; sendCount: number; successCount: number }>; testId: string }) {
  const max = Math.max(...rows.flatMap((row) => [row.sendCount, row.successCount]), 1);
  return (
    <div className="dashboard-trend-chart" data-testid={testId}>
      {rows.length === 0 ? <p className="dashboard-unavailable">今日暂无趋势明细。</p> : rows.map((row) => (
        <div className="dashboard-trend-column" key={row.bucketStart}>
          <span className="dashboard-trend-bar send" style={{ height: `${Math.max(8, row.sendCount / max * 100)}%` }} />
          <span className="dashboard-trend-bar success" style={{ height: `${Math.max(8, row.successCount / max * 100)}%` }} />
          <small>{hour(row.bucketStart)}</small>
        </div>
      ))}
    </div>
  );
}

function GaugeCard({ label, value, max, suffix = '', testId }: { label: string; value: number | null; max: number; suffix?: string; testId?: string }) {
  const percent = value === null ? 0 : Math.max(0, Math.min(100, max === 0 ? 0 : value / max * 100));
  return (
    <div className="dashboard-gauge-card">
      <span>{label}</span>
      <strong data-testid={testId}>{value === null ? '—' : `${value}${suffix}`}</strong>
      <div className="dashboard-gauge-track"><span style={{ width: `${percent}%` }} /></div>
    </div>
  );
}

function BarChart({ title, testId, bars, emptyMessage }: { title: string; testId: string; bars: Array<{ label: string; value: number; caption: string }>; emptyMessage?: string }) {
  const max = Math.max(...bars.map((bar) => bar.value), 1);
  return (
    <div className="dashboard-bar-chart" data-testid={testId}>
      <h3>{title}</h3>
      {bars.length === 0 && emptyMessage ? <p className="dashboard-unavailable">{emptyMessage}</p> : bars.map((bar) => (
        <div className="dashboard-bar-row" key={bar.label}>
          <span>{bar.label}</span>
          <div className="dashboard-bar-track"><span style={{ width: `${Math.max(4, bar.value / max * 100)}%` }} /></div>
          <small>{bar.caption}</small>
        </div>
      ))}
    </div>
  );
}

function UnavailableData({ testId, message, title }: { testId: string; message: string; title?: string }) {
  return (
    <div className="dashboard-unavailable" data-testid={testId}>
      {title && <h3>{title}</h3>}
      <p>{message}</p>
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
