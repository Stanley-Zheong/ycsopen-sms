import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  listWebhookFailures,
  pauseWebhookFailure,
  replayWebhookFailure,
  resumeWebhookFailure,
  type WebhookFailureRow,
} from '@/api/webhookDeliveryApi';
import { mutationErrorMessage } from '@/api/client';
import ActionReasonDialog from '@/components/common/ActionReasonDialog';
import { QueryField, QueryPanel } from '@/components/common/QueryPanel';
import '@/styles/webhook-delivery.css';

const DEFAULT_FILTER = { tenantId: '', state: 'PUSH_FAILED' };
type PendingAction = { kind: 'replay' | 'pause' | 'resume'; row: WebhookFailureRow };

const ACTION_LABELS: Record<PendingAction['kind'], string> = {
  replay: '重放推送',
  pause: '暂停推送',
  resume: '恢复推送',
};

export default function AdminPushFailuresPage() {
  const queryClient = useQueryClient();
  const [draftFilter, setDraftFilter] = useState(DEFAULT_FILTER);
  const [appliedFilter, setAppliedFilter] = useState(DEFAULT_FILTER);
  const [pendingAction, setPendingAction] = useState<PendingAction | null>(null);
  const [reason, setReason] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const filter = useMemo(() => ({ ...appliedFilter }), [appliedFilter]);
  const failures = useQuery({ queryKey: ['webhook-failures', filter], queryFn: () => listWebhookFailures(filter), retry: false });

  const refresh = async () => queryClient.invalidateQueries({ queryKey: ['webhook-failures'] });
  const ok = async (text: string) => {
    setPendingAction(null);
    setReason('');
    setMessage(text);
    setError('');
    await refresh();
  };
  const fail = (failure: unknown, fallback: string) => {
    setError(mutationErrorMessage(failure, fallback));
    setMessage('');
  };

  const replay = useMutation({
    mutationFn: ({ row, actionReason }: { row: WebhookFailureRow; actionReason: string }) => replayWebhookFailure(row.eventId, actionReason),
    onSuccess: (result) => ok(`重放完成：${result.state}`),
    onError: (failure) => fail(failure, '重放失败'),
  });
  const pause = useMutation({
    mutationFn: ({ row, actionReason }: { row: WebhookFailureRow; actionReason: string }) => pauseWebhookFailure(row.eventId, actionReason),
    onSuccess: (result) => ok(`暂停完成：${result.state}`),
    onError: (failure) => fail(failure, '暂停失败'),
  });
  const resume = useMutation({
    mutationFn: ({ row, actionReason }: { row: WebhookFailureRow; actionReason: string }) => resumeWebhookFailure(row.eventId, actionReason),
    onSuccess: (result) => ok(`恢复完成：${result.state}`),
    onError: (failure) => fail(failure, '恢复失败'),
  });
  const openAction = (kind: PendingAction['kind'], row: WebhookFailureRow) => {
    setPendingAction({ kind, row });
    setReason('');
    setError('');
  };
  const closeAction = () => {
    setPendingAction(null);
    setReason('');
  };
  const confirmAction = () => {
    if (!pendingAction) return;
    const actionReason = reason.trim();
    if (pendingAction.kind === 'replay') replay.mutate({ row: pendingAction.row, actionReason });
    if (pendingAction.kind === 'pause') pause.mutate({ row: pendingAction.row, actionReason });
    if (pendingAction.kind === 'resume') resume.mutate({ row: pendingAction.row, actionReason });
  };

  return (
    <section className="webhook-delivery-page" data-testid="admin-webhook-delivery-push-failures-page">
      <nav aria-label="面包屑">告警管理 / 推送失败</nav>
      <header className="webhook-delivery-header">
        <div>
          <h1>Webhook 推送失败治理</h1>
          <p className="page-description">查看状态/上行推送失败，按租户隔离失败记录，并执行重放、暂停、恢复。</p>
        </div>
      </header>

      {message && <p role="status" className="webhook-delivery-alert success" data-testid="admin-webhook-delivery-operation-message">{message}</p>}
      {error && <p role="alert" className="webhook-delivery-alert error" data-testid="admin-webhook-delivery-operation-error">{error}</p>}

      <QueryPanel
        legacyPanelTestId="admin-webhook-delivery-push-failures-policy"
        onSubmit={() => setAppliedFilter({ ...draftFilter })}
        onReset={() => {
          setDraftFilter(DEFAULT_FILTER);
          setAppliedFilter(DEFAULT_FILTER);
        }}
        onRefresh={() => void refresh()}
        refreshLegacyTestId="admin-webhook-delivery-push-failures-refresh"
        result={(
          <section className="card">
            {failures.isLoading && <p>正在加载推送失败记录…</p>}
            {failures.isError && <p role="alert">推送失败记录加载失败。</p>}
            {!failures.isLoading && !failures.isError && (failures.data ?? []).length === 0 && <p>暂无推送失败记录。</p>}
            <table className="webhook-delivery-table">
              <thead>
                <tr><th>事件</th><th>租户</th><th>类型</th><th>目的地</th><th>状态</th><th>策略</th><th>下次重试</th><th>操作</th></tr>
              </thead>
              <tbody>
                {(failures.data ?? []).map((row) => (
                  <tr key={row.eventId} data-testid="admin-webhook-delivery-push-failures-row">
                    <td>{row.logicalId}</td>
                    <td>{row.tenantId}</td>
                    <td>{row.eventType}</td>
                    <td>{row.destinationUrl}</td>
                    <td>{row.state}</td>
                    <td>{row.attemptCount}/{row.maxAttempts}</td>
                    <td>{row.nextAttemptAt ?? '-'}</td>
                    <td>
                      <button type="button" data-testid="admin-webhook-delivery-push-failures-replay" onClick={() => openAction('replay', row)}>重放</button>
                      <button type="button" data-testid="admin-webhook-delivery-push-failures-pause" onClick={() => openAction('pause', row)}>暂停</button>
                      <button type="button" data-testid="admin-webhook-delivery-push-failures-resume" onClick={() => openAction('resume', row)}>恢复</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
        )}
      >
        <QueryField name="tenant-id" label="租户ID">
          <input data-testid="admin-webhook-delivery-filter-tenant" value={draftFilter.tenantId} onChange={(event) => setDraftFilter((current) => ({ ...current, tenantId: event.target.value }))} />
        </QueryField>
        <QueryField name="state" label="状态">
          <select data-testid="admin-webhook-delivery-filter-state" value={draftFilter.state} onChange={(event) => setDraftFilter((current) => ({ ...current, state: event.target.value }))}>
            <option value="PUSH_FAILED">PUSH_FAILED</option>
            <option value="PAUSED">PAUSED</option>
            <option value="RETRY">RETRY</option>
          </select>
        </QueryField>
      </QueryPanel>

      {pendingAction && (
        <ActionReasonDialog
          idPrefix="admin-webhook-delivery-action"
          title={`确认${ACTION_LABELS[pendingAction.kind]}`}
          target={`推送事件 ${pendingAction.row.logicalId} · 机构 ${pendingAction.row.tenantId}`}
          consequence={pendingAction.kind === 'replay'
            ? '确认后将按保存的目的地重新投递该事件。'
            : pendingAction.kind === 'pause'
              ? '确认后将暂停该事件的后续自动投递。'
              : '确认后将恢复该事件的投递调度。'}
          reasonLabel={`${ACTION_LABELS[pendingAction.kind]}原因`}
          reasonTestId="admin-webhook-delivery-action-reason"
          reason={reason}
          placeholder="请填写本次操作的复核依据"
          confirmLabel={`确认${ACTION_LABELS[pendingAction.kind]}`}
          pending={replay.isPending || pause.isPending || resume.isPending}
          onReasonChange={setReason}
          onCancel={closeAction}
          onConfirm={confirmAction}
        />
      )}
    </section>
  );
}
