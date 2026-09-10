import { useQuery } from '@tanstack/react-query';
import { getResourceStatistics } from '@/api/operationalDashboardApi';
import { MetricSourceBlock } from '@/pages/admin/dashboard/DashboardPage';

export default function TenantTemplateStatisticsPage() {
  const stats = useQuery({ queryKey: ['tenant-template-statistics'], queryFn: () => getResourceStatistics(), retry: false });
  const data = stats.data;
  return (
    <section className="card" data-testid="tenant-operational-dashboards-templates-statistics-page">
      <h1>模板统计</h1>
      {stats.isLoading && <p>正在加载模板统计…</p>}
      {stats.isError && <p role="alert">模板统计加载失败。</p>}
      {data && (
        <>
          <table>
            <thead><tr><th>签名</th><th>模板</th><th>提交</th><th>成功</th><th>拒绝</th><th>新鲜度</th></tr></thead>
            <tbody>{data.resources.map((row) => (
              <tr key={`${row.signatureId}-${row.templateId}`}>
                <td>{row.signatureId ?? '-'}</td><td>{row.templateId ?? '-'}</td><td>{row.submitCount}</td><td>{row.successCount}</td><td>{row.rejectedCount}</td><td>{row.freshnessAt || '-'}</td>
              </tr>
            ))}</tbody>
          </table>
          <MetricSourceBlock source={data.source} />
        </>
      )}
    </section>
  );
}
