import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import ModalDialog from '@/components/common/ModalDialog';
import {
  endChannelMaintenance,
  listChannelHealthMonitor,
  pauseChannel,
  recordChannelObservation,
  startChannelMaintenance,
  type ChannelHealthMonitorRow,
  type ChannelPauseRequest,
} from '@/api/channelHealthApi';
import { mutationErrorMessage } from '@/api/client';
import { isPlatformRole, useAuthStore } from '@/store/authStore';
import '@/styles/channel-health.css';

type DialogMode = 'pause' | 'maintenance-start' | 'maintenance-end';

interface ActionDialog {
  mode: DialogMode;
  row: ChannelHealthMonitorRow;
  trigger: ChannelPauseRequest['trigger'];
  reason: string;
}

const actionLabels: Record<DialogMode, string> = {
  pause: '暂停通道',
  'maintenance-start': '进入维护',
  'maintenance-end': '结束维护',
};

function displayRate(value: string | number | null) {
  if (value == null) return '未采样';
  const numeric = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(numeric) ? `${(numeric * 100).toFixed(2)}%` : String(value);
}

function displayTime(value: string | null) {
  return value ? new Date(value).toLocaleString() : '未记录';
}

function canEndMaintenance(row: ChannelHealthMonitorRow) {
  return row.status === 'MAINTENANCE' && Boolean(row.reasonCode && row.reasonCode !== 'NO_SAMPLE');
}

export default function ChannelHealthPage() {
  const queryClient = useQueryClient();
  const userType = useAuthStore((state) => state.userType);
  const canRead = userType === 'ADMIN' || userType === 'OPERATOR';
  const [dialog, setDialog] = useState<ActionDialog | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const monitor = useQuery({
    queryKey: ['channel-health-monitor'],
    queryFn: listChannelHealthMonitor,
    retry: false,
    enabled: isPlatformRole(userType) && canRead,
  });
  const rows = useMemo(() => monitor.data ?? [], [monitor.data]);

  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: ['channel-health-monitor'] });
  };

  const healthSample = useMutation({
    mutationFn: (row: ChannelHealthMonitorRow) => recordChannelObservation(row.channelId, {
      connected: true,
      timeoutRate: '0.0000',
      failureRate: '0.0000',
      averageLatencyMs: Math.max(1, row.averageLatencyMs || 80),
      reasonCode: 'MANUAL_VALIDATION',
    }),
    onSuccess: async () => {
      setMessage('健康校验已记录。');
      setError(null);
      await refresh();
    },
    onError: (failure) => setError(mutationErrorMessage(failure, '健康校验记录失败')),
  });

  const runAction = useMutation({
    mutationFn: async () => {
      if (!dialog) throw new Error('动作不存在');
      const request: ChannelPauseRequest = {
        trigger: dialog.trigger,
        reason: dialog.reason.trim(),
      };
      if (!request.reason) throw new Error('原因必填');
      if (dialog.mode === 'pause') return pauseChannel(dialog.row.channelId, request);
      if (dialog.mode === 'maintenance-start') return startChannelMaintenance(dialog.row.channelId, request);
      return endChannelMaintenance(dialog.row.channelId, request);
    },
    onSuccess: async () => {
      setDialog(null);
      setMessage('通道状态已更新。');
      setError(null);
      await refresh();
    },
    onError: (failure) => setError(failure instanceof Error ? failure.message : mutationErrorMessage(failure, '通道状态更新失败')),
  });

  const openDialog = (mode: DialogMode, row: ChannelHealthMonitorRow) => {
    setDialog({ mode, row, trigger: mode === 'pause' ? 'MANUAL' : 'HEALTH', reason: '' });
    setError(null);
  };

  if (!isPlatformRole(userType) || !canRead) {
    return <p role="alert" data-testid="admin-channel-health-channel-monitor-access-denied">无权查看通道健康。</p>;
  }

  return (
    <section data-testid="admin-channel-health-channel-monitor-page">
      <nav aria-label="面包屑">通道管理 / 健康监控</nav>
      <header className="channel-health-header">
        <div>
          <h1>通道健康监控</h1>
          <p className="page-description">记录通道健康样本、暂停异常通道，并控制计划维护状态。</p>
        </div>
        <button type="button" className="button-secondary" data-testid="admin-channel-health-channel-monitor-refresh" onClick={() => void refresh()} disabled={monitor.isFetching}>刷新</button>
      </header>

      {message && <p role="status" className="channel-health-alert success">{message}</p>}
      {error && <div role="alert" className="channel-health-alert error">{error}</div>}

      <section className="card channel-health-table">
        {monitor.isLoading && <p>正在加载通道健康数据…</p>}
        {monitor.isError && <p role="alert">通道健康数据加载失败。<button type="button" onClick={() => void refresh()}>重试</button></p>}
        {!monitor.isLoading && !monitor.isError && rows.length === 0 && <p>暂无通道健康数据。</p>}
        {rows.length > 0 && (
          <table className="ratio-table">
            <thead>
              <tr>
                <th>通道</th>
                <th>协议/运营商</th>
                <th>状态</th>
                <th>健康</th>
                <th>超时率</th>
                <th>失败率</th>
                <th>延迟</th>
                <th>候选</th>
                <th>事件</th>
                <th>暂停信息</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.channelId} data-testid="admin-channel-health-channel-monitor-row" data-business-key={row.channelId}>
                  <td>{row.channelName}</td>
                  <td>{row.protocol} / {row.operator}</td>
                  <td>{row.status}</td>
                  <td>
                    <span data-testid="admin-channel-health-channel-monitor-health-state" className={`channel-health-badge ${row.healthState.toLowerCase()}`}>
                      {row.healthState}
                    </span>
                    <div className="channel-health-muted">{row.reasonCode ?? '无异常'}</div>
                  </td>
                  <td>{displayRate(row.timeoutRate)}</td>
                  <td>{displayRate(row.failureRate)}</td>
                  <td>{row.averageLatencyMs}ms</td>
                  <td>{row.candidateEligible ? '可候选' : row.candidateReasonCode}</td>
                  <td>{row.eventCount}</td>
                  <td>{row.pauseReason ? `${row.pauseReason} / ${row.pausedBy ?? 'unknown'} / ${displayTime(row.pausedAt)}` : '无'}</td>
                  <td>
                    <div className="channel-health-row-actions">
                      <button type="button" data-testid="admin-channel-health-channel-monitor-sample" className="button-secondary" onClick={() => healthSample.mutate(row)} disabled={healthSample.isPending}>记录健康</button>
                      <button type="button" data-testid="admin-channel-health-channel-monitor-pause" onClick={() => openDialog('pause', row)} disabled={row.status === 'PAUSED'}>暂停</button>
                      <button type="button" data-testid="admin-channel-health-channel-monitor-maintenance" onClick={() => openDialog('maintenance-start', row)} disabled={row.status !== 'NORMAL'}>维护</button>
                      <button type="button" data-testid="admin-channel-health-channel-monitor-maintenance-end" onClick={() => openDialog('maintenance-end', row)} disabled={!canEndMaintenance(row)}>结束维护</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {dialog && (
        <ModalDialog labelledBy="channel-health-action-title" onRequestClose={() => setDialog(null)}>
          <h2 id="channel-health-action-title">{actionLabels[dialog.mode]}：{dialog.row.channelName}</h2>
          <div className="channel-health-form">
            <label>触发来源
              <select data-testid="admin-channel-health-channel-monitor-action-trigger" value={dialog.trigger} onChange={(event) => setDialog({ ...dialog, trigger: event.target.value as ChannelPauseRequest['trigger'] })}>
                <option value="MANUAL">MANUAL</option>
                <option value="HEALTH">HEALTH</option>
                <option value="COMPLAINT">COMPLAINT</option>
                <option value="RATIO">RATIO</option>
              </select>
            </label>
            <p data-testid="admin-channel-health-channel-monitor-action-actor">操作人由当前登录账号自动记录。</p>
            <label className="channel-health-wide">原因
              <textarea data-testid="admin-channel-health-channel-monitor-action-reason" value={dialog.reason} onChange={(event) => setDialog({ ...dialog, reason: event.target.value })} />
            </label>
          </div>
          <div className="channel-health-dialog-actions">
            <button type="button" className="button-secondary" onClick={() => setDialog(null)}>取消</button>
            <button type="button" data-testid="admin-channel-health-channel-monitor-action-submit" onClick={() => runAction.mutate()} disabled={runAction.isPending}>确认</button>
          </div>
        </ModalDialog>
      )}
    </section>
  );
}
