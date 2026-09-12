import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  getAdminUplink,
  listAdminUplinks,
  listUplinkPushMonitor,
  pauseUplinkPushEvent,
  replayAdminUplink,
  replayUplinkPushEvent,
  resumeUplinkPushEvent,
  type UplinkPushMonitorRow,
  type UplinkRecord,
} from '@/api/uplinkNormalizationApi';
import { mutationErrorMessage } from '@/api/client';
import { QueryField, QueryPanel } from '@/components/common/QueryPanel';
import '@/styles/uplink-normalization.css';

const DEFAULT_REASON = '运营复核后处理';
const EMPTY_UPLINK_FILTERS = { tenantId: '', phoneNumber: '', keyword: '', carrier: '', pushState: '', startTime: '', endTime: '' };
const EMPTY_MONITOR_FILTERS = { tenantId: '', state: '', destination: '' };

export default function AdminUplinksPage() {
  const queryClient = useQueryClient();
  const [draftFilters, setDraftFilters] = useState(EMPTY_UPLINK_FILTERS);
  const [filters, setFilters] = useState(draftFilters);
  const [draftMonitorFilters, setDraftMonitorFilters] = useState(EMPTY_MONITOR_FILTERS);
  const [monitorFilters, setMonitorFilters] = useState(draftMonitorFilters);
  const [selected, setSelected] = useState<UplinkRecord | null>(null);
  const [uplinkReplayReason, setUplinkReplayReason] = useState(DEFAULT_REASON);
  const [pushActionReason, setPushActionReason] = useState(DEFAULT_REASON);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  const uplinkFilter = useMemo(() => filters, [filters]);
  const monitorFilter = useMemo(() => monitorFilters, [monitorFilters]);
  const uplinks = useQuery({ queryKey: ['admin-uplinks', uplinkFilter], queryFn: () => listAdminUplinks(uplinkFilter), retry: false });
  const monitor = useQuery({ queryKey: ['uplink-push-monitor', monitorFilter], queryFn: () => listUplinkPushMonitor(monitorFilter), retry: false });

  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: ['admin-uplinks'] });
    await queryClient.invalidateQueries({ queryKey: ['uplink-push-monitor'] });
  };
  const ok = async (text: string) => {
    setMessage(text);
    setError('');
    await refresh();
  };
  const fail = (failure: unknown, fallback: string) => {
    setError(mutationErrorMessage(failure, fallback));
    setMessage('');
  };

  const detail = useMutation({
    mutationFn: (row: UplinkRecord) => getAdminUplink(row.id),
    onSuccess: (row) => {
      setSelected(row);
      setError('');
    },
    onError: (failure) => fail(failure, '上行详情加载失败'),
  });
  const replay = useMutation({
    mutationFn: (row: UplinkRecord) => replayAdminUplink(row.id, uplinkReplayReason),
    onSuccess: (result) => ok(`上行重放完成：${result.state}`),
    onError: (failure) => fail(failure, '上行重放失败'),
  });
  const replayPush = useMutation({
    mutationFn: (row: UplinkPushMonitorRow) => replayUplinkPushEvent(row.eventId, pushActionReason),
    onSuccess: (result) => ok(`推送重放完成：${result.state}`),
    onError: (failure) => fail(failure, '推送重放失败'),
  });
  const pausePush = useMutation({
    mutationFn: (row: UplinkPushMonitorRow) => pauseUplinkPushEvent(row.eventId, pushActionReason),
    onSuccess: (result) => ok(`目的地暂停完成：${result.state}`),
    onError: (failure) => fail(failure, '目的地暂停失败'),
  });
  const resumePush = useMutation({
    mutationFn: (row: UplinkPushMonitorRow) => resumeUplinkPushEvent(row.eventId, pushActionReason),
    onSuccess: (result) => ok(`目的地恢复完成：${result.state}`),
    onError: (failure) => fail(failure, '目的地恢复失败'),
  });

  const setFilter = (key: keyof typeof draftFilters, value: string) => setDraftFilters((current) => ({ ...current, [key]: value }));
  const setMonitorFilter = (key: keyof typeof draftMonitorFilters, value: string) => setDraftMonitorFilters((current) => ({ ...current, [key]: value }));
  const applyFilters = () => setFilters(draftFilters);
  const applyMonitorFilters = () => setMonitorFilters(draftMonitorFilters);

  return (
    <section className="uplink-page" data-testid="admin-uplink-normalization-uplinks-page">
      <nav aria-label="面包屑">运营管理 / 上行数据</nav>
      <header className="uplink-header">
        <div>
          <h1>上行数据归一化</h1>
          <p className="page-description">HTTP 与 CMPP 上行统一归档，按租户、号码、关键词、运营商和推送状态查询，并复用通用推送通道重放。</p>
        </div>
        <button type="button" onClick={() => void refresh()} data-testid="admin-uplink-normalization-refresh">刷新</button>
      </header>

      {message && <p role="status" className="uplink-alert success" data-testid="admin-uplink-normalization-operation-message">{message}</p>}
      {error && <p role="alert" className="uplink-alert error" data-testid="admin-uplink-normalization-operation-error">{error}</p>}

      <section className="uplink-summary-grid">
        <article className="card" data-testid="admin-uplink-normalization-uplinks-card-total">
          <span>当前结果</span>
          <strong>{uplinks.data?.length ?? 0}</strong>
        </article>
        <article className="card">
          <span>推送异常</span>
          <strong>{monitor.data?.filter((row) => row.state !== 'DELIVERED').length ?? 0}</strong>
        </article>
      </section>

      <QueryPanel
        className="uplink-query-panel"
        submitLegacyTestId="admin-uplink-normalization-uplinks-search"
        resetLegacyTestId="admin-uplink-normalization-uplinks-reset"
        onSubmit={applyFilters}
        onReset={() => { setDraftFilters(EMPTY_UPLINK_FILTERS); setFilters(EMPTY_UPLINK_FILTERS); }}
        result={<>
          <header className="uplink-subheader">
            <h2>上行明细</h2>
            <label>重放原因<input data-testid="admin-uplink-normalization-uplink-replay-reason" value={uplinkReplayReason} onChange={(event) => setUplinkReplayReason(event.target.value)} /></label>
          </header>
          {uplinks.isLoading && <p>正在加载上行记录…</p>}
          {uplinks.isError && <p role="alert">上行记录加载失败。</p>}
          <table className="uplink-table" data-testid="admin-uplink-normalization-uplinks-table">
          <thead>
            <tr><th>租户</th><th>来源</th><th>手机号</th><th>内容关键词</th><th>状态</th><th>运营商</th><th>目的地</th><th>位置</th><th>通道</th><th>接收时间</th><th>操作</th></tr>
          </thead>
          <tbody>
            {(uplinks.data ?? []).map((row) => (
              <tr key={row.id} data-testid="admin-uplink-normalization-uplinks-row">
                <td>{row.tenantId}</td>
                <td>{row.sourceProtocol}/{row.sourceConnector}</td>
                <td>{row.phoneMasked}</td>
                <td>{row.contentKeyword ?? '-'}</td>
                <td>{row.state}/{row.pushState}</td>
                <td>{row.carrier ?? '-'}</td>
                <td>{row.destination ?? '-'}</td>
                <td>{row.province ?? '-'}/{row.city ?? '-'}</td>
                <td>{row.channelId ?? '-'}</td>
                <td>{row.receiveTime ?? '-'}</td>
                <td>
                  <button type="button" data-testid="admin-uplink-normalization-uplink-detail" onClick={() => detail.mutate(row)}>详情</button>
                  <button type="button" data-testid="admin-uplink-normalization-uplink-replay" onClick={() => replay.mutate(row)}>重放</button>
                </td>
              </tr>
            ))}
          </tbody>
          </table>
        </>}
      >
        <QueryField name="tenant-id" label="租户ID"><input data-testid="admin-uplink-normalization-uplinks-filter-tenant" value={draftFilters.tenantId} onChange={(event) => setFilter('tenantId', event.target.value)} /></QueryField>
        <QueryField name="phone-number" label="手机号"><input data-testid="admin-uplink-normalization-uplinks-filter-number" value={draftFilters.phoneNumber} onChange={(event) => setFilter('phoneNumber', event.target.value)} /></QueryField>
        <QueryField name="keyword" label="关键词"><input data-testid="admin-uplink-normalization-uplinks-filter-keyword" value={draftFilters.keyword} onChange={(event) => setFilter('keyword', event.target.value)} /></QueryField>
        <QueryField name="carrier" label="运营商"><input data-testid="admin-uplink-normalization-uplinks-filter-carrier" value={draftFilters.carrier} onChange={(event) => setFilter('carrier', event.target.value)} /></QueryField>
        <QueryField name="push-state" label="推送状态"><select data-testid="admin-uplink-normalization-uplinks-filter-push-state" value={draftFilters.pushState} onChange={(event) => setFilter('pushState', event.target.value)}><option value="">全部</option><option value="PENDING">PENDING</option><option value="DELIVERED">DELIVERED</option><option value="PUSH_FAILED">PUSH_FAILED</option><option value="PAUSED">PAUSED</option><option value="NOT_CONFIGURED">NOT_CONFIGURED</option></select></QueryField>
        <QueryField name="start-time" label="开始时间"><input data-testid="admin-uplink-normalization-uplinks-filter-period-start" type="datetime-local" value={draftFilters.startTime} onChange={(event) => setFilter('startTime', event.target.value)} /></QueryField>
        <QueryField name="end-time" label="结束时间"><input data-testid="admin-uplink-normalization-uplinks-filter-period-end" type="datetime-local" value={draftFilters.endTime} onChange={(event) => setFilter('endTime', event.target.value)} /></QueryField>
      </QueryPanel>

      {selected && (
        <aside className="uplink-detail" data-testid="admin-uplink-normalization-detail-drawer">
          <button type="button" aria-label="关闭详情" onClick={() => setSelected(null)}>×</button>
          <h2>上行详情 #{selected.id}</h2>
          <dl>
            <dt>租户</dt><dd>{selected.tenantId}</dd>
            <dt>消息</dt><dd>{selected.messageId ?? '-'}</dd>
            <dt>内容</dt><dd>{selected.content}</dd>
            <dt>签名/产品</dt><dd>{selected.signatureId ?? '-'}/{selected.productCode ?? '-'}</dd>
            <dt>推送事件</dt><dd>{selected.pushEventId ?? '-'}</dd>
          </dl>
        </aside>
      )}

      <section className="card" data-testid="admin-uplink-normalization-push-monitor-page">
        <header className="uplink-subheader">
          <div>
            <h2>上行推送监控</h2>
            <p>从真实 webhook_delivery_events / attempts 读取成功、失败、重试与延迟证据。</p>
          </div>
          <label>操作原因<input data-testid="admin-uplink-normalization-push-action-reason" value={pushActionReason} onChange={(event) => setPushActionReason(event.target.value)} /></label>
        </header>
        <QueryPanel
          submitLegacyTestId="admin-uplink-normalization-push-search"
          onSubmit={applyMonitorFilters}
          onReset={() => { setDraftMonitorFilters(EMPTY_MONITOR_FILTERS); setMonitorFilters(EMPTY_MONITOR_FILTERS); }}
          result={<table className="uplink-table" data-testid="admin-uplink-normalization-push-monitor-table">
          <thead>
            <tr><th>事件</th><th>租户</th><th>目的地</th><th>状态</th><th>尝试</th><th>延迟ms</th><th>更新时间</th><th>操作</th></tr>
          </thead>
          <tbody>
            {(monitor.data ?? []).map((row) => (
              <tr key={row.eventId} data-testid="admin-uplink-normalization-push-monitor-row">
                <td>{row.logicalId}</td>
                <td>{row.tenantId}</td>
                <td>{row.destinationUrl}</td>
                <td>{row.state}</td>
                <td>{row.attemptCount}/{row.maxAttempts}（{row.attemptRows}）</td>
                <td>{row.latencyMs}</td>
                <td>{row.updatedAt ?? '-'}</td>
                <td data-testid="admin-uplink-normalization-uplink-push-destination-action">
                  <button type="button" data-testid="admin-uplink-normalization-push-replay" onClick={() => replayPush.mutate(row)}>重放</button>
                  <button type="button" data-testid="admin-uplink-normalization-push-pause" onClick={() => pausePush.mutate(row)}>暂停</button>
                  <button type="button" data-testid="admin-uplink-normalization-push-resume" onClick={() => resumePush.mutate(row)}>恢复</button>
                </td>
              </tr>
            ))}
          </tbody>
          </table>}
        >
          <QueryField name="tenant-id" label="租户ID"><input data-testid="admin-uplink-normalization-push-filter-tenant" value={draftMonitorFilters.tenantId} onChange={(event) => setMonitorFilter('tenantId', event.target.value)} /></QueryField>
          <QueryField name="state" label="状态"><select data-testid="admin-uplink-normalization-push-filter-state" value={draftMonitorFilters.state} onChange={(event) => setMonitorFilter('state', event.target.value)}><option value="">全部</option><option value="PUSH_FAILED">PUSH_FAILED</option><option value="PAUSED">PAUSED</option><option value="RETRY">RETRY</option><option value="DELIVERED">DELIVERED</option></select></QueryField>
          <QueryField name="destination" label="目的地"><input data-testid="admin-uplink-normalization-push-filter-destination" value={draftMonitorFilters.destination} onChange={(event) => setMonitorFilter('destination', event.target.value)} /></QueryField>
        </QueryPanel>
      </section>
    </section>
  );
}
