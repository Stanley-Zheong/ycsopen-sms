import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  listFinancialDrilldown,
  listFinancialSummaries,
  type FinancialAnalyticsFilter,
} from '@/api/financialSourceAnalyticsApi';
import { QueryField, QueryPanel } from '@/components/common/QueryPanel';
import '@/styles/financial-source-analytics.css';

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

function initialDraft() {
  return {
    startDate: firstDayOfMonth(),
    endDate: today(),
    tenantId: '42',
    channelId: '7',
  };
}

function filterFromDraft(draft: ReturnType<typeof initialDraft>): FinancialAnalyticsFilter {
  return {
    startDate: draft.startDate,
    endDate: draft.endDate,
    tenantId: numberOrNull(draft.tenantId),
    channelId: numberOrNull(draft.channelId),
  };
}

export default function AdminFinancialAnalyticsPage() {
  const [draft, setDraft] = useState(initialDraft);
  const [filter, setFilter] = useState<FinancialAnalyticsFilter>(() => filterFromDraft(initialDraft()));
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
    setFilter(filterFromDraft(draft));
  };

  const reset = () => {
    const initial = initialDraft();
    setDraft(initial);
    setFilter(filterFromDraft(initial));
  };

  const rows = summaries.data ?? [];
  const financeStatus = summaries.isFetching
    ? '正在加载财务汇总…'
    : summaries.isError
      ? '财务汇总加载失败。'
      : rows.length === 0
        ? '暂无财务汇总。'
        : `已加载 ${rows.length} 条财务汇总。`;
  const channelStatus = summaries.isFetching
    ? '正在加载通道统计…'
    : summaries.isError
      ? '通道统计加载失败。'
      : rows.length === 0
        ? '暂无通道统计。'
        : `已加载 ${rows.length} 条通道统计。`;

  return (
    <div className="financial-analytics-page" data-testid="admin-finance-overview-page">
      <nav aria-label="面包屑">财务中心 / 财务总览</nav>
      <h1 data-testid="admin-finance-overview-title">财务总览</h1>
      <QueryPanel
        className="financial-query-panel"
        onSubmit={apply}
        onReset={reset}
        initiallyExpanded
        submitLabel="查询"
        submitLegacyTestId="admin-financial-source-financial-analytics-apply"
        additionalActions={(
          <button
            className="button-secondary"
            data-testid="admin-financial-source-financial-analytics-drilldown"
            type="button"
            onClick={() => setShowDrilldown(true)}
          >
            查看来源
          </button>
        )}
      >
        <QueryField name="start-date" label="开始日期">
          <input data-testid="admin-financial-source-financial-analytics-start-date" type="date" value={draft.startDate} onChange={(e) => setDraft({ ...draft, startDate: e.target.value })} />
        </QueryField>
        <QueryField name="end-date" label="结束日期">
          <input data-testid="admin-financial-source-financial-analytics-end-date" type="date" value={draft.endDate} onChange={(e) => setDraft({ ...draft, endDate: e.target.value })} />
        </QueryField>
        <QueryField name="tenant-id" label="机构 ID">
          <input data-testid="admin-financial-source-financial-analytics-tenant-filter" inputMode="numeric" value={draft.tenantId} onChange={(e) => setDraft({ ...draft, tenantId: e.target.value })} />
        </QueryField>
        <QueryField name="channel-id" label="通道 ID">
          <input data-testid="admin-financial-source-financial-analytics-channel-filter" inputMode="numeric" value={draft.channelId} onChange={(e) => setDraft({ ...draft, channelId: e.target.value })} />
        </QueryField>
      </QueryPanel>

      <section className="card" data-testid="admin-financial-source-financial-analytics-page">
        <h2>成本 / 收入 / 毛利</h2>
        <p data-testid="admin-financial-source-financial-analytics-status" role={summaries.isError ? 'alert' : 'status'} aria-live={summaries.isError ? 'assertive' : 'polite'}>{financeStatus}</p>
        <div className="financial-table-region" data-testid="data-table">
          <table className="financial-data-table" data-testid="admin-financial-source-financial-analytics-table" aria-label="财务汇总">
            <thead><tr><th title="机构">机构</th><th title="通道">通道</th><th title="源记录">源记录</th><th title="计费条数">计费条数</th><th title="成本">成本</th><th title="收入">收入</th><th title="毛利">毛利</th><th title="价格版本">价格版本</th><th title="刷新时间">刷新</th></tr></thead>
            <tbody>
              {summaries.isFetching ? (
                <tr><td className="financial-table-state" data-testid="table-loading" colSpan={9}>正在加载财务汇总…</td></tr>
              ) : summaries.isError ? (
                <tr><td className="financial-table-state" data-testid="table-error" colSpan={9}>财务汇总加载失败。<button data-testid="admin-financial-source-financial-analytics-retry" type="button" onClick={() => void summaries.refetch()}>重试</button></td></tr>
              ) : rows.length === 0 ? (
                <tr><td className="financial-table-state" data-testid="table-empty" colSpan={9}>暂无财务汇总。</td></tr>
              ) : rows.map((row) => {
                const price = `${row.priceBookVersion} / ${row.unitPriceMil} mil`;
                return (
                  <tr key={`${row.tenantId}-${row.channelId}-${row.priceBookVersion}`} data-testid="admin-financial-source-financial-analytics-row">
                    <td>{row.tenantId}</td>
                    <td>{row.channelId ?? '-'}</td>
                    <td>{row.sourceCount}</td>
                    <td>{row.billableCount}</td>
                    <td>{moneyMil(row.providerCostMil)}</td>
                    <td>{moneyMil(row.revenueMil)}</td>
                    <td>{moneyMil(row.profitMil)}</td>
                    <td className="financial-data-table__truncate" title={price}>{price}</td>
                    <td className="financial-data-table__truncate" title={row.freshnessAt ?? '-'}>{row.freshnessAt ?? '-'}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </section>

      <section className="card" data-testid="admin-financial-source-channel-statistics-page">
        <h2>通道财务统计</h2>
        <p data-testid="admin-financial-source-channel-statistics-status" role={summaries.isError ? 'alert' : 'status'} aria-live={summaries.isError ? 'assertive' : 'polite'}>{channelStatus}</p>
        <div className="financial-table-region" data-testid="data-table">
          <table className="financial-data-table" data-testid="admin-financial-source-channel-statistics-table" aria-label="通道财务统计">
            <thead><tr><th title="通道">通道</th><th title="发送源">发送源</th><th title="最终成功">最终成功</th><th title="成本">成本</th><th title="收入">收入</th><th title="毛利">毛利</th></tr></thead>
            <tbody>
              {summaries.isFetching ? (
                <tr><td className="financial-table-state" data-testid="table-loading" colSpan={6}>正在加载通道统计…</td></tr>
              ) : summaries.isError ? (
                <tr><td className="financial-table-state" data-testid="table-error" colSpan={6}>通道统计加载失败。<button data-testid="admin-financial-source-channel-statistics-retry" type="button" onClick={() => void summaries.refetch()}>重试</button></td></tr>
              ) : rows.length === 0 ? (
                <tr><td className="financial-table-state" data-testid="table-empty" colSpan={6}>暂无通道统计。</td></tr>
              ) : rows.map((row) => (
                <tr key={`channel-${row.tenantId}-${row.channelId}`} data-testid="admin-financial-source-channel-statistics-row">
                  <td>{row.channelId ?? '-'}</td>
                  <td>{row.sourceCount}</td>
                  <td>{row.billableCount}</td>
                  <td>{moneyMil(row.providerCostMil)}</td>
                  <td>{moneyMil(row.revenueMil)}</td>
                  <td>{moneyMil(row.profitMil)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {showDrilldown && (
        <section className="card" data-testid="admin-financial-source-financial-analytics-drilldown-panel">
          <h2>来源明细</h2>
          <div className="financial-table-region">
            <table className="financial-data-table" data-testid="admin-financial-source-financial-analytics-drilldown-table" aria-label="财务来源明细">
              <thead><tr><th title="消息 ID">消息ID</th><th title="最终状态">最终状态</th><th title="成本">成本</th><th title="收入">收入</th><th title="毛利">毛利</th><th title="公式">公式</th><th title="价格版本">价格版本</th><th title="刷新时间">刷新</th></tr></thead>
              <tbody>
                {sources.isFetching ? (
                  <tr><td className="financial-table-state" data-testid="table-loading" colSpan={8}>正在加载来源明细…</td></tr>
                ) : sources.isError ? (
                  <tr><td className="financial-table-state" data-testid="table-error" colSpan={8}>来源明细加载失败。</td></tr>
                ) : (sources.data ?? []).length === 0 ? (
                  <tr><td className="financial-table-state" data-testid="table-empty" colSpan={8}>暂无来源明细。</td></tr>
                ) : (sources.data ?? []).map((row) => {
                  const formula = `${row.formulaVersion}: ${row.formula}`;
                  return (
                    <tr key={row.taskId} data-testid="admin-financial-source-financial-analytics-drilldown-row">
                      <td className="financial-data-table__truncate" title={row.messageId}>{row.messageId}</td>
                      <td>{row.finalStatus}</td>
                      <td>{moneyMil(row.providerCostMil)}</td>
                      <td>{moneyMil(row.revenueMil)}</td>
                      <td>{moneyMil(row.profitMil)}</td>
                      <td className="financial-data-table__truncate" data-testid="admin-financial-source-financial-analytics-drilldown-formula" title={row.formula}>{formula}</td>
                      <td className="financial-data-table__truncate" title={row.priceBookVersion}>{row.priceBookVersion}</td>
                      <td className="financial-data-table__truncate" title={row.freshnessAt ?? '-'}>{row.freshnessAt ?? '-'}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  );
}
