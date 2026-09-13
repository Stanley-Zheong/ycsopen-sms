import { useQuery } from '@tanstack/react-query';
import { getResourceStatistics } from '@/api/operationalDashboardApi';

export default function AdminStatisticsOverviewPage() {
  const stats = useQuery({ queryKey: ['admin-statistics-overview'], queryFn: () => getResourceStatistics(), retry: false });
  const rows = stats.data?.resources ?? [];
  return (
    <section data-testid="admin-statistics-overview-page">
      <nav aria-label="面包屑">数据统计 / 统计总览</nav>
      <header><h1>统计总览</h1><p className="page-description">查看机构、签名和模板的提交、成功及拒绝统计。</p></header>
      {stats.isLoading && <p role="status">正在加载统计数据…</p>}
      {stats.isError && <p role="alert">统计数据加载失败。</p>}
      <section className="card">
        <table className="ratio-table" data-testid="admin-statistics-overview-table">
          <thead><tr><th>机构</th><th>签名</th><th>模板</th><th>提交数</th><th>成功数</th><th>拒绝数</th><th>数据更新时间</th></tr></thead>
          <tbody>{rows.map((row) => <tr key={`${row.tenantId}-${row.signatureId}-${row.templateId}`} data-testid="admin-statistics-overview-row"><td>{row.tenantId}</td><td>{row.signatureId ?? '-'}</td><td>{row.templateId ?? '-'}</td><td>{row.submitCount}</td><td>{row.successCount}</td><td>{row.rejectedCount}</td><td>{row.freshnessAt ?? '-'}</td></tr>)}</tbody>
        </table>
        {!stats.isLoading && rows.length === 0 && <p>暂无统计数据。</p>}
      </section>
    </section>
  );
}
