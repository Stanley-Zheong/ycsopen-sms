import { useQuery } from '@tanstack/react-query';
import { listTenantFeeWarningEpisodes } from '@/api/feeWarningCreditApi';
import '@/styles/alert-engine.css';

export default function TenantFeeWarningPage() {
  const episodes = useQuery({ queryKey: ['tenant-fee-warning-episodes'], queryFn: listTenantFeeWarningEpisodes, retry: false });
  const rows = episodes.data ?? [];
  return (
    <section className="alert-engine-page" data-testid="tenant-fee-warning-overview-page">
      <nav aria-label="面包屑">账户管理 / 余额与费用预警</nav>
      <header className="alert-engine-header">
        <div>
          <h1>余额与费用预警</h1>
          <p className="page-description">展示本机构费用预警来源、处理动作和通知投递状态。</p>
        </div>
      </header>
      {episodes.isLoading && <p data-testid="tenant-fee-warning-loading">正在加载费用预警…</p>}
      {episodes.isError && <p role="alert" data-testid="tenant-fee-warning-error">费用预警加载失败。</p>}
      <section className="card" data-testid="tenant-fee-warning-overview-low-balance-warning">
        <h2>当前预警</h2>
        {rows.length === 0 && <p>暂无活跃费用预警。</p>}
        <table className="alert-engine-table">
          <thead><tr><th>指标</th><th>来源金额</th><th>阈值</th><th>动作</th><th>审批</th><th>来源</th></tr></thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.id} data-testid="tenant-fee-warning-overview-row">
                <td>{row.metricType}</td>
                <td>{row.sourceAmountMil}</td>
                <td>{row.thresholdValue}</td>
                <td>{row.action}</td>
                <td>{row.approvalState}</td>
                <td>{row.sourceSnapshot}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
      <section className="card" data-testid="tenant-fee-warning-overview-delivery-evidence">
        <h2>通知投递证据</h2>
        {rows.map((row) => <p key={row.id}>{row.deliveryState} · alert #{row.alertRecordId ?? '-'}</p>)}
      </section>
    </section>
  );
}
