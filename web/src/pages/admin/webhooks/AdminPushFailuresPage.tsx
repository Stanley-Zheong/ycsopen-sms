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
import '@/styles/webhook-delivery.css';

export default function AdminPushFailuresPage() {
  const queryClient = useQueryClient();
  const [tenantId, setTenantId] = useState('');
  const [state, setState] = useState('PUSH_FAILED');
  const [reason, setReason] = useState('运营复核后处理');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const filter = useMemo(() => ({ tenantId, state }), [tenantId, state]);
  const failures = useQuery({ queryKey: ['webhook-failures', filter], queryFn: () => listWebhookFailures(filter), retry: false });

  const refresh = async () => queryClient.invalidateQueries({ queryKey: ['webhook-failures'] });
  const ok = async (text: string) => {
    setMessage(text);
    setError('');
    await refresh();
  };
  const fail = (failure: unknown, fallback: string) => {
    setError(mutationErrorMessage(failure, fallback));
    setMessage('');
  };

  const replay = useMutation({
    mutationFn: (row: WebhookFailureRow) => replayWebhookFailure(row.eventId, reason),
    onSuccess: (result) => ok(`重放完成：${result.state}`),
    onError: (failure) => fail(failure, '重放失败'),
  });
  const pause = useMutation({
    mutationFn: (row: WebhookFailureRow) => pauseWebhookFailure(row.eventId, reason),
    onSuccess: (result) => ok(`暂停完成：${result.state}`),
    onError: (failure) => fail(failure, '暂停失败'),
  });
  const resume = useMutation({
    mutationFn: (row: WebhookFailureRow) => resumeWebhookFailure(row.eventId, reason),
    onSuccess: (result) => ok(`恢复完成：${result.state}`),
    onError: (failure) => fail(failure, '恢复失败'),
  });

  return (
    <section className="webhook-delivery-page" data-testid="admin-webhook-delivery-push-failures-page">
      <nav aria-label="面包屑">告警管理 / 推送失败</nav>
      <header className="webhook-delivery-header">
        <div>
          <h1>Webhook 推送失败治理</h1>
          <p className="page-description">查看状态/上行推送失败，按租户隔离失败记录，并执行重放、暂停、恢复。</p>
        </div>
        <button type="button" data-testid="admin-webhook-delivery-push-failures-refresh" onClick={() => void refresh()}>刷新</button>
      </header>

      {message && <p role="status" className="webhook-delivery-alert success" data-testid="admin-webhook-delivery-operation-message">{message}</p>}
      {error && <p role="alert" className="webhook-delivery-alert error" data-testid="admin-webhook-delivery-operation-error">{error}</p>}

      <section className="card webhook-delivery-filters" data-testid="admin-webhook-delivery-push-failures-policy">
        <label>租户ID<input data-testid="admin-webhook-delivery-filter-tenant" value={tenantId} onChange={(event) => setTenantId(event.target.value)} /></label>
        <label>状态<select data-testid="admin-webhook-delivery-filter-state" value={state} onChange={(event) => setState(event.target.value)}><option value="PUSH_FAILED">PUSH_FAILED</option><option value="PAUSED">PAUSED</option><option value="RETRY">RETRY</option></select></label>
        <label>操作原因<input data-testid="admin-webhook-delivery-action-reason" value={reason} onChange={(event) => setReason(event.target.value)} /></label>
      </section>

      <section className="card">
        {failures.isLoading && <p>正在加载推送失败记录…</p>}
        {failures.isError && <p role="alert">推送失败记录加载失败。</p>}
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
                  <button type="button" data-testid="admin-webhook-delivery-push-failures-replay" onClick={() => replay.mutate(row)}>重放</button>
                  <button type="button" data-testid="admin-webhook-delivery-push-failures-pause" onClick={() => pause.mutate(row)}>暂停</button>
                  <button type="button" data-testid="admin-webhook-delivery-push-failures-resume" onClick={() => resume.mutate(row)}>恢复</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </section>
  );
}
