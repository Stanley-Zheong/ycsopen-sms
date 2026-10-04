import { useQuery } from '@tanstack/react-query';
import { getComplaintAnalytics, type ComplaintAnalyticsDimension } from '@/api/complaintCaseApi';
import '@/styles/alert-engine.css';

function DimensionList({ rows }: { rows: ComplaintAnalyticsDimension[] }) {
  if (rows.length === 0) {
    return <p>暂无数据</p>;
  }
  return (
    <ul>
      {rows.map((row) => (
        <li key={row.dimension}>{row.dimension}：{row.count}</li>
      ))}
    </ul>
  );
}

export default function AdminComplaintAnalyticsPage() {
  const analytics = useQuery({ queryKey: ['complaint-analytics'], queryFn: getComplaintAnalytics, retry: false });
  const data = analytics.data;

  return (
    <section className="alert-engine-page" data-testid="admin-complaint-case-analytics-page">
      <nav aria-label="面包屑">运营管理 / 投诉分析</nav>
      <header className="alert-engine-header">
        <div>
          <h1>投诉趋势与分布</h1>
          <p className="page-description">按创建日期展示投诉量趋势，并按机构、签名、内容类型聚合，单独暴露 UNKNOWN 归因质量。</p>
        </div>
      </header>

      {analytics.isLoading && <p data-testid="admin-complaint-case-analytics-loading">正在加载投诉分析…</p>}
      {analytics.isError && <p role="alert" data-testid="admin-complaint-case-analytics-error">投诉分析加载失败。</p>}

      {data && (
        <section className="card alert-engine-rule-grid">
          <div data-testid="admin-complaint-case-analytics-trend">
            <h2>每日趋势</h2>
            {data.trend.length === 0 ? (
              <p>暂无数据</p>
            ) : (
              <ul>
                {data.trend.map((row) => <li key={row.day}>{row.day}：{row.count}</li>)}
              </ul>
            )}
          </div>
          <div data-testid="admin-complaint-case-analytics-quality">
            <h2>归因质量</h2>
            <p>总投诉 {data.totalCount}</p>
            <p>未知归因 {data.unknownAttributionCount}</p>
          </div>
          <div data-testid="admin-complaint-case-analytics-tenant">
            <h2>机构分布</h2>
            <DimensionList rows={data.byTenant} />
          </div>
          <div data-testid="admin-complaint-case-analytics-signature">
            <h2>签名分布</h2>
            <DimensionList rows={data.bySignature} />
          </div>
          <div data-testid="admin-complaint-case-analytics-content-type">
            <h2>内容类型分布</h2>
            <DimensionList rows={data.byContentType} />
          </div>
        </section>
      )}
    </section>
  );
}
