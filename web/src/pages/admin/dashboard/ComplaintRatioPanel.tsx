import { useMemo, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { fetchComplaintRatio, fetchComplaintRatioCases, pauseComplaintRatioTarget } from '@/api/dashboard';
import type { ComplaintRatioCase, ComplaintRatioItem } from '@/types/api';
import { formatRatioAsPerMille } from '@lib/format';

type Dimension = 'channel' | 'tenant';

interface ComplaintRatioPanelProps {
  dimension: Dimension;
  title: string;
}

interface DrilldownState {
  dimension: Dimension;
  row: ComplaintRatioItem;
}

interface InterventionState {
  row: ComplaintRatioItem;
  reason: string;
}

const DEFAULT_PAUSE_REASON = '投诉占比达到阈值，按运营复核执行暂停';

function defaultMonth() {
  return new Date().toISOString().slice(0, 7);
}

/** Phase45 complaint-ratio dashboard: source-backed ratio, data-quality, drill-down and exact intervention controls. */
export default function ComplaintRatioPanel({ dimension, title }: ComplaintRatioPanelProps) {
  const [month, setMonth] = useState(defaultMonth);
  const [showAll, setShowAll] = useState(false);
  const [drilldown, setDrilldown] = useState<DrilldownState | null>(null);
  const [intervention, setIntervention] = useState<InterventionState | null>(null);
  const query = useQuery({
    queryKey: ['complaint-ratio', dimension, month, showAll],
    queryFn: () => fetchComplaintRatio(dimension, month, { topN: 10, all: showAll }),
  });
  const drilldownQuery = useQuery({
    queryKey: ['complaint-ratio-drilldown', drilldown?.dimension, drilldown?.row.dimensionId, month],
    queryFn: () => fetchComplaintRatioCases(drilldown!.dimension, drilldown!.row.dimensionId, month),
    enabled: drilldown !== null,
  });
  const pauseMutation = useMutation({
    mutationFn: ({ row, reason }: { row: ComplaintRatioItem; reason: string }) =>
      pauseComplaintRatioTarget(dimension, row.dimensionId, month, reason.trim()),
    onSuccess: () => {
      setIntervention(null);
      query.refetch();
    },
  });

  const rows = query.data ?? [];
  const breachedRows = useMemo(() => rows.filter((row) => thresholdResult(row) === 'BREACHED'), [rows]);
  const confirmReason = intervention?.reason.trim() ?? '';
  const thresholdSummary = rows[0]
    ? (
        <>
          阈值：{thresholdLabel(rows[0].thresholdValue)}
          {' '}版本：{rows[0].thresholdConfigVersion ?? 'unknown'}
          {' '}当前显示超阈值：{breachedRows.length}
        </>
      )
    : '阈值：暂无数据 版本：暂无数据 当前显示超阈值：0';
  const tableContent = (
    <>
      <thead>
        <tr>
          <th>排名</th>
          <th>{dimension === 'channel' ? '通道' : '机构'}</th>
          <th>发送量</th>
          <th>投诉量</th>
          <th>投诉占比</th>
          <th>数据质量</th>
          <th>新鲜度</th>
          <th>状态</th>
          <th>动作</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row.dimensionId} className={thresholdResult(row) === 'BREACHED' ? 'over-threshold' : undefined}>
            <td>{row.rank ?? '-'}</td>
            <td>{row.dimensionName ?? row.dimensionId}</td>
            <td>{row.sendCount.toLocaleString()}</td>
            <td>{row.complaintCount.toLocaleString()}</td>
            <td>{formatRatioAsPerMille(row.ratio)}</td>
            <td>{qualityLabel(row.dataQuality)}</td>
            <td>{freshnessLabel(row.freshnessPolicy)} {row.calculatedAt ? row.calculatedAt.slice(0, 16).replace('T', ' ') : '-'}</td>
            <td>{statusLabel(row)}</td>
            <td>
              <button type="button" onClick={() => setDrilldown({ dimension, row })}>钻取</button>
              <button
                type="button"
                disabled={!row.interventionAvailable || pauseMutation.isPending}
                onClick={() => setIntervention({ row, reason: DEFAULT_PAUSE_REASON })}
              >
                {dimension === 'channel' ? '暂停通道' : '暂停机构'}
              </button>
            </td>
          </tr>
        ))}
      </tbody>
    </>
  );

  return (
    <section className="card">
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16, alignItems: 'flex-start' }}>
        <div>
          <h3>{title}</h3>
          {dimension === 'channel' ? (
            <p data-testid="admin-complaint-ratio-dashboard-complaint-ratio-threshold">
              {thresholdSummary}
            </p>
          ) : (
            <p data-testid="admin-complaint-ratio-dashboard-complaint-ratio-threshold-tenant">
              {thresholdSummary}
            </p>
          )}
        </div>
        {dimension === 'channel' ? (
          <div data-testid="admin-complaint-ratio-dashboard-complaint-ratio-period">
            <PeriodControls title={title} month={month} showAll={showAll} fetching={query.isFetching} onMonth={setMonth} onToggleAll={() => setShowAll((value) => !value)} onRefresh={() => query.refetch()} />
          </div>
        ) : (
          <div data-testid="admin-complaint-ratio-dashboard-complaint-ratio-period-tenant">
            <PeriodControls title={title} month={month} showAll={showAll} fetching={query.isFetching} onMonth={setMonth} onToggleAll={() => setShowAll((value) => !value)} onRefresh={() => query.refetch()} />
          </div>
        )}
      </div>
      {query.isLoading && <p>加载中…</p>}
      {query.isError && <p role="alert">加载失败，请稍后重试（网络异常，见 PRD 5.15 节异常流规范）。</p>}
      {!query.isLoading && !query.isError && rows.length === 0 && <p style={{ color: '#888' }}>暂无数据</p>}
      {rows.length > 0 && dimension === 'channel' && (
        <table className="ratio-table" data-testid="admin-complaint-ratio-dashboard-complaint-ratio-channel">
          {tableContent}
        </table>
      )}
      {rows.length > 0 && dimension === 'tenant' && (
        <table className="ratio-table" data-testid="admin-complaint-ratio-dashboard-complaint-ratio-tenant">
          {tableContent}
        </table>
      )}
      {pauseMutation.data && (
        <p role="status">
          干预完成：{pauseMutation.data.dimensionType}:{pauseMutation.data.dimensionId} {pauseMutation.data.status}
          {' '}证据：{pauseMutation.data.sourceKey}
        </p>
      )}
      {pauseMutation.isError && <p role="alert">干预失败：请检查权限、网络、数据质量或目标状态后重试。</p>}
      {drilldown && (
        <DrilldownDialog
          title={title}
          row={drilldown.row}
          cases={drilldownQuery.data ?? []}
          loading={drilldownQuery.isLoading}
          onClose={() => setDrilldown(null)}
        />
      )}
      {intervention && (
        <InterventionConfirmDialog
          dimension={dimension}
          row={intervention.row}
          reason={intervention.reason}
          pending={pauseMutation.isPending}
          onReasonChange={(reason) => setIntervention({ row: intervention.row, reason })}
          onCancel={() => setIntervention(null)}
          onConfirm={() => {
            if (confirmReason.length > 0) {
              pauseMutation.mutate({ row: intervention.row, reason: intervention.reason });
            }
          }}
        />
      )}
    </section>
  );
}

function PeriodControls({
  title,
  month,
  showAll,
  fetching,
  onMonth,
  onToggleAll,
  onRefresh,
}: {
  title: string;
  month: string;
  showAll: boolean;
  fetching: boolean;
  onMonth: (value: string) => void;
  onToggleAll: () => void;
  onRefresh: () => void;
}) {
  return (
    <>
      <label>
        月份
        <input aria-label={`${title}月份`} type="month" value={month} onChange={(event) => onMonth(event.target.value)} />
      </label>
      <button type="button" onClick={onToggleAll}>
        {showAll ? '显示 Top 10' : '显示全部'}
      </button>
      <button type="button" onClick={onRefresh}>
        {fetching ? '刷新中' : '刷新'}
      </button>
    </>
  );
}

function DrilldownDialog({
  title,
  row,
  cases,
  loading,
  onClose,
}: {
  title: string;
  row: ComplaintRatioItem;
  cases: ComplaintRatioCase[];
  loading: boolean;
  onClose: () => void;
}) {
  return (
    <div role="dialog" aria-modal="true" data-testid="admin-complaint-ratio-dashboard-complaint-ratio-drilldown" className="card">
      <h3>{title}钻取：{row.dimensionName ?? row.dimensionId}</h3>
      {loading ? <p>投诉明细加载中…</p> : (
        <table>
          <thead><tr><th>投诉</th><th>消息</th><th>归因</th><th>状态</th><th>时间</th></tr></thead>
          <tbody>{cases.map((item) => (
            <tr key={item.id}>
              <td>{item.summary ?? item.source}</td>
              <td>{item.messageId ?? '-'}</td>
              <td>{item.attributionQuality}</td>
              <td>{item.status}</td>
              <td>{item.createdAt.slice(0, 16).replace('T', ' ')}</td>
            </tr>
          ))}</tbody>
        </table>
      )}
      <button type="button" onClick={onClose}>关闭</button>
    </div>
  );
}

function InterventionConfirmDialog({
  dimension,
  row,
  reason,
  pending,
  onReasonChange,
  onCancel,
  onConfirm,
}: {
  dimension: Dimension;
  row: ComplaintRatioItem;
  reason: string;
  pending: boolean;
  onReasonChange: (value: string) => void;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const actionLabel = dimension === 'channel' ? '暂停通道' : '暂停机构';
  const targetLabel = `${row.dimensionName ?? row.dimensionId}`;
  const reasonReady = reason.trim().length > 0;

  return (
    <div role="dialog" aria-modal="true" data-testid="admin-complaint-ratio-dashboard-complaint-ratio-intervention-confirm" className="card">
      <h3>确认{actionLabel}</h3>
      <p>
        目标：{targetLabel}；投诉占比 {formatRatioAsPerMille(row.ratio)}，阈值 {thresholdLabel(row.thresholdValue)}。
        该操作会影响发送能力，需确认并记录原因。
      </p>
      <label>
        暂停原因
        <textarea
          data-testid="admin-complaint-ratio-dashboard-complaint-ratio-intervention-reason"
          maxLength={255}
          value={reason}
          onChange={(event) => onReasonChange(event.target.value)}
        />
      </label>
      <p>{reason.trim().length}/255</p>
      {!reasonReady && <p role="alert">暂停原因不能为空。</p>}
      <button type="button" onClick={onCancel} disabled={pending}>取消</button>
      <button type="button" onClick={onConfirm} disabled={pending || !reasonReady}>
        {pending ? '提交中' : `确认${actionLabel}`}
      </button>
    </div>
  );
}

function thresholdResult(row: ComplaintRatioItem) {
  return row.thresholdResult ?? (row.overThreshold ? 'BREACHED' : 'NORMAL');
}

function thresholdLabel(value?: number) {
  return typeof value === 'number' ? formatRatioAsPerMille(value) : '未知';
}

function statusLabel(row: ComplaintRatioItem) {
  if (thresholdResult(row) === 'BREACHED') return '超阈值';
  if (thresholdResult(row) === 'UNKNOWN') return '待确认';
  return '正常';
}

function qualityLabel(value?: string) {
  if (value === 'COMPLETE') return '完整';
  if (value === 'ZERO_DENOMINATOR') return '零发送';
  return '未知';
}

function freshnessLabel(value?: string) {
  if (value === 'CURRENT_MONTH_HOURLY') return '当月小时刷新';
  if (value === 'T_PLUS_1_DAILY') return '历史月 T+1';
  return '新鲜度未知';
}
