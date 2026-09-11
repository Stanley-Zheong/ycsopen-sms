import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  listFinancialDrilldown,
  listFinancialSummaries,
  type FinancialAnalyticsFilter,
} from '@/api/financialSourceAnalyticsApi';

function today() {
  return shanghaiDateParts().join('-');
}

function firstDayOfMonth() {
  const [year, month] = shanghaiDateParts();
  return `${year}-${month}-01`;
}

function shanghaiDateParts() {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date());
  const value = (type: 'year' | 'month' | 'day') => parts.find((part) => part.type === type)?.value ?? '';
  return [value('year'), value('month'), value('day')];
}

function moneyMil(value: number) {
  return (value / 1000).toFixed(3);
}

function numberOrNull(value: string): number | null {
  const trimmed = value.trim();
  return trimmed ? Number(trimmed) : null;
}

export default function AdminFinancialAnalyticsPage() {
  const [draft, setDraft] = useState({
    startDate: firstDayOfMonth(),
    endDate: today(),
    tenantId: '42',
    channelId: '7',
  });
  const [filter, setFilter] = useState<FinancialAnalyticsFilter>({
    startDate: draft.startDate,
    endDate: draft.endDate,
    tenantId: 42,
    channelId: 7,
  });
  const [showDrilldown, setShowDrilldown] = useState(false);

  const summaries = useQuery({
    queryKey: ['financial-source-analytics', filter],
    queryFn: () => listFinancialSummaries(filter),
    retry: false,
  });
  const sources = useQuery({
    queryKey: ['financial-source-drilldown', filter, showDrilldown],
    queryFn: () => listFinancialDrilldown(filter),
    enabled: showDrilldown,
    retry: false,
  });

  const apply = () => {
    setFilter({
      startDate: draft.startDate,
      endDate: draft.endDate,
      tenantId: numberOrNull(draft.tenantId),
      channelId: numberOrNull(draft.channelId),
    });
  };

  return (
    <div>
      <h1>财务来源分析</h1>
      <section className="card" data-testid="admin-financial-source-financial-analytics-page">
        <h2>成本 / 收入 / 毛利</h2>
        <div className="form-grid">
          <label>开始日期<input data-testid="admin-financial-source-financial-analytics-start-date" type="date" value={draft.startDate} onChange={(e) => setDraft({ ...draft, startDate: e.target.value })} /></label>
          <label>结束日期<input data-testid="admin-financial-source-financial-analytics-end-date" type="date" value={draft.endDate} onChange={(e) => setDraft({ ...draft, endDate: e.target.value })} /></label>
          <label>机构ID<input data-testid="admin-financial-source-financial-analytics-tenant-filter" value={draft.tenantId} onChange={(e) => setDraft({ ...draft, tenantId: e.target.value })} /></label>
          <label>通道ID<input data-testid="admin-financial-source-financial-analytics-channel-filter" value={draft.channelId} onChange={(e) => setDraft({ ...draft, channelId: e.target.value })} /></label>
          <button data-testid="admin-financial-source-financial-analytics-apply" type="button" onClick={apply}>查询</button>
          <button data-testid="admin-financial-source-financial-analytics-drilldown" type="button" onClick={() => setShowDrilldown(true)}>查看来源</button>
        </div>
        <table data-testid="admin-financial-source-financial-analytics-table">
          <thead><tr><th>机构</th><th>通道</th><th>源记录</th><th>计费条数</th><th>成本</th><th>收入</th><th>毛利</th><th>价格版本</th><th>刷新</th></tr></thead>
          <tbody>{(summaries.data ?? []).map((row) => (
            <tr key={`${row.tenantId}-${row.channelId}-${row.priceBookVersion}`} data-testid="admin-financial-source-financial-analytics-row">
              <td>{row.tenantId}</td>
              <td>{row.channelId ?? '-'}</td>
              <td>{row.sourceCount}</td>
              <td>{row.billableCount}</td>
              <td>{moneyMil(row.providerCostMil)}</td>
              <td>{moneyMil(row.revenueMil)}</td>
              <td>{moneyMil(row.profitMil)}</td>
              <td>{row.priceBookVersion} / {row.unitPriceMil} mil</td>
              <td>{row.freshnessAt ?? '-'}</td>
            </tr>
          ))}</tbody>
        </table>
      </section>

      <section className="card" data-testid="admin-financial-source-channel-statistics-page">
        <h2>通道财务统计</h2>
        <table data-testid="admin-financial-source-channel-statistics-table">
          <thead><tr><th>通道</th><th>发送源</th><th>最终成功</th><th>成本</th><th>收入</th><th>毛利</th></tr></thead>
          <tbody>{(summaries.data ?? []).map((row) => (
            <tr key={`channel-${row.tenantId}-${row.channelId}`} data-testid="admin-financial-source-channel-statistics-row">
              <td>{row.channelId ?? '-'}</td>
              <td>{row.sourceCount}</td>
              <td>{row.billableCount}</td>
              <td>{moneyMil(row.providerCostMil)}</td>
              <td>{moneyMil(row.revenueMil)}</td>
              <td>{moneyMil(row.profitMil)}</td>
            </tr>
          ))}</tbody>
        </table>
      </section>

      {showDrilldown && (
        <section className="card" data-testid="admin-financial-source-financial-analytics-drilldown-panel">
          <h2>来源明细</h2>
          <table data-testid="admin-financial-source-financial-analytics-drilldown-table">
            <thead><tr><th>消息ID</th><th>最终状态</th><th>成本</th><th>收入</th><th>毛利</th><th>公式</th><th>价格版本</th><th>刷新</th></tr></thead>
            <tbody>{(sources.data ?? []).map((row) => (
              <tr key={row.taskId} data-testid="admin-financial-source-financial-analytics-drilldown-row">
                <td>{row.messageId}</td>
                <td>{row.finalStatus}</td>
                <td>{moneyMil(row.providerCostMil)}</td>
                <td>{moneyMil(row.revenueMil)}</td>
                <td>{moneyMil(row.profitMil)}</td>
                <td>{row.formulaVersion}: {row.formula}</td>
                <td>{row.priceBookVersion}</td>
                <td>{row.freshnessAt ?? '-'}</td>
              </tr>
            ))}</tbody>
          </table>
        </section>
      )}
    </div>
  );
}
