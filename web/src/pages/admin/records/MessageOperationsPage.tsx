import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  appealMessage,
  bulkErrorAction,
  correctReceipt,
  listErrorGroups,
  listReceipts,
  listSends,
  listSubmissions,
  replayReceipt,
  requestMessageExport,
  resendMessage,
  type OperationFilter,
} from '@/api/messageOperationsApi';
import { mutationErrorMessage } from '@/api/client';
import '@/styles/message-operations.css';

type Section = 'submissions' | 'sends' | 'receipts' | 'errors';

const SECTION_LABELS: Record<Section, string> = {
  submissions: '提交详情',
  sends: '发送详情',
  receipts: '回执详情',
  errors: '错误详情',
};

function newActionId(prefix: string) {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return `${prefix}-${crypto.randomUUID()}`;
  }
  return `${prefix}-${Date.now()}`;
}

export default function MessageOperationsPage({ initialSection = 'submissions' }: { initialSection?: Section }) {
  const queryClient = useQueryClient();
  const [section, setSection] = useState<Section>(initialSection);
  const [tenantId, setTenantId] = useState('42');
  const [messageId, setMessageId] = useState('');
  const [status, setStatus] = useState('');
  const [errorCode, setErrorCode] = useState('');
  const [reason, setReason] = useState('运营复核确认');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  const filter: OperationFilter = useMemo(() => ({ tenantId, messageId, status, errorCode }), [tenantId, messageId, status, errorCode]);
  const queryOptions = { retry: false };
  const submissions = useQuery({ queryKey: ['message-ops-submissions', filter], queryFn: () => listSubmissions(filter), ...queryOptions });
  const sends = useQuery({ queryKey: ['message-ops-sends', filter], queryFn: () => listSends(filter), ...queryOptions });
  const receipts = useQuery({ queryKey: ['message-ops-receipts', filter], queryFn: () => listReceipts(filter), ...queryOptions });
  const errors = useQuery({ queryKey: ['message-ops-errors', filter], queryFn: () => listErrorGroups(filter), ...queryOptions });

  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: ['message-ops-submissions'] });
    await queryClient.invalidateQueries({ queryKey: ['message-ops-sends'] });
    await queryClient.invalidateQueries({ queryKey: ['message-ops-receipts'] });
    await queryClient.invalidateQueries({ queryKey: ['message-ops-errors'] });
  };
  const fail = (failure: unknown, fallback: string) => {
    setError(mutationErrorMessage(failure, fallback));
    setMessage('');
  };
  const ok = async (text: string) => {
    setMessage(text);
    setError('');
    await refresh();
  };

  const resend = useMutation({
    mutationFn: (target: string) => resendMessage(target, newActionId('RESEND'), reason),
    onSuccess: (result) => ok(`重发已处理：${result.resultCode}`),
    onError: (failure) => fail(failure, '重发失败'),
  });
  const appeal = useMutation({
    mutationFn: (target: string) => appealMessage(target, newActionId('APPEAL'), reason),
    onSuccess: (result) => ok(`申诉已登记：${result.resultCode}`),
    onError: (failure) => fail(failure, '申诉失败'),
  });
  const correct = useMutation({
    mutationFn: (receiptId: number) => correctReceipt(receiptId, newActionId('CORRECT'), reason, 'DELIVERED', '', 'ST20260909'),
    onSuccess: (result) => ok(`回执纠正已应用：${result.resultCode}`),
    onError: (failure) => fail(failure, '回执纠正失败'),
  });
  const replay = useMutation({
    mutationFn: (receiptId: number) => replayReceipt(receiptId, newActionId('REPLAY'), reason),
    onSuccess: (result) => ok(`回执重放已处理：${result.resultCode}`),
    onError: (failure) => fail(failure, '回执重放失败'),
  });
  const bulkRetry = useMutation({
    mutationFn: () => bulkErrorAction(newActionId('BULK'), 'BULK_RETRY', errorCode || errors.data?.[0]?.normalizedCode || 'UNKNOWN',
      (sends.data ?? []).filter((row) => row.sendStatus === 'FAILED').map((row) => row.messageId), reason),
    onSuccess: (result) => ok(`批量重试完成：成功 ${result.completed}，失败 ${result.failed}`),
    onError: (failure) => fail(failure, '批量重试失败'),
  });
  const markProblem = useMutation({
    mutationFn: () => bulkErrorAction(newActionId('PROBLEM'), 'MARK_PROBLEM', errorCode || errors.data?.[0]?.normalizedCode || 'UNKNOWN',
      (sends.data ?? []).filter((row) => row.sendStatus === 'FAILED').map((row) => row.messageId), reason),
    onSuccess: (result) => ok(`问题标记完成：成功 ${result.completed}，失败 ${result.failed}`),
    onError: (failure) => fail(failure, '问题标记失败'),
  });
  const exportRequest = useMutation({
    mutationFn: () => requestMessageExport(filter, newActionId('EXPORT'), reason),
    onSuccess: (result) => ok(`导出请求已登记：${result.resultMessage}`),
    onError: (failure) => fail(failure, '导出请求失败'),
  });

  return (
    <section className="message-operations-page" data-testid="admin-message-receipt-operations-page">
      <nav aria-label="面包屑">数据详单 / {SECTION_LABELS[section]}</nav>
      {section === 'submissions' && <p className="message-operations-section-marker" data-testid="admin-message-receipt-submission-details-page">提交详情页面</p>}
      {section === 'sends' && <p className="message-operations-section-marker" data-testid="admin-message-receipt-send-details-page">发送详情页面</p>}
      {section === 'receipts' && <p className="message-operations-section-marker" data-testid="admin-message-receipt-receipt-details-page">回执详情页面</p>}
      {section === 'errors' && <p className="message-operations-section-marker" data-testid="admin-message-receipt-error-details-page">错误详情页面</p>}
      <header className="message-operations-header">
        <div>
          <h1>消息、回执与错误运营</h1>
          <p className="page-description">按租户、状态、消息和错误码追踪提交、发送、回执、错误聚合，并执行有原因、有幂等键的运营动作。</p>
        </div>
        <button type="button" data-testid="admin-message-receipt-export-request" onClick={() => exportRequest.mutate()}>请求导出</button>
      </header>

      {message && <p role="status" className="message-operations-alert success" data-testid="admin-message-receipt-operation-message">{message}</p>}
      {error && <p role="alert" className="message-operations-alert error" data-testid="admin-message-receipt-operation-error">{error}</p>}

      <section className="card message-operations-filters">
        <label>租户ID<input data-testid="admin-message-receipt-filter-tenant" value={tenantId} onChange={(event) => setTenantId(event.target.value)} /></label>
        <label>消息ID<input data-testid="admin-message-receipt-filter-message" value={messageId} onChange={(event) => setMessageId(event.target.value)} /></label>
        <label>状态<input data-testid="admin-message-receipt-filter-status" value={status} onChange={(event) => setStatus(event.target.value)} /></label>
        <label>错误码<input data-testid="admin-message-receipt-filter-error-code" value={errorCode} onChange={(event) => setErrorCode(event.target.value)} /></label>
        <label>动作原因<input data-testid="admin-message-receipt-action-reason" value={reason} onChange={(event) => setReason(event.target.value)} /></label>
      </section>

      <div className="message-operations-tabs">
        {Object.entries(SECTION_LABELS).map(([key, label]) => (
          <button key={key} type="button" data-testid={`admin-message-receipt-tab-${key}`} className={section === key ? 'active' : ''} onClick={() => setSection(key as Section)}>{label}</button>
        ))}
      </div>

      {section === 'submissions' && (
        <section className="card" data-testid="admin-message-receipt-submission-details-trace">
          <h2>提交详情链路</h2>
          <table className="message-operations-table">
            <thead><tr><th>提交ID</th><th>租户</th><th>业务流水</th><th>消息ID</th><th>提交状态</th><th>发送状态</th><th>模板/签名</th><th>错误</th></tr></thead>
            <tbody>{(submissions.data ?? []).map((row) => (
              <tr key={row.submissionId} data-testid="admin-message-receipt-submission-details-row">
                <td>{row.submissionId}</td><td>{row.tenantId}</td><td>{row.submitId}</td><td>{row.messageId}</td><td>{row.submissionStatus}</td><td>{row.sendStatus}</td><td>{row.templateId}/{row.signatureId}</td><td>{row.errorCode || '-'}</td>
              </tr>
            ))}</tbody>
          </table>
        </section>
      )}

      {section === 'sends' && (
        <section className="card">
          <h2>发送详情</h2>
          <table className="message-operations-table" data-testid="admin-message-receipt-send-details-table">
            <thead><tr><th>消息ID</th><th>租户</th><th>手机号</th><th>内容摘要</th><th>状态</th><th>通道</th><th>运营商/地区</th><th>错误</th><th>动作</th></tr></thead>
            <tbody>{(sends.data ?? []).map((row) => (
              <tr key={row.taskId} data-testid="admin-message-receipt-send-details-row">
                <td>{row.messageId}</td><td>{row.tenantId}</td><td>{row.maskedMobile}</td><td>{row.contentSummary}</td><td>{row.sendStatus}</td><td>{row.channelId}</td><td>{row.carrier}/{row.province}/{row.city}</td><td>{row.errorCode || '-'}</td>
                <td>
                  <button type="button" data-testid="admin-message-receipt-send-details-resend" disabled={row.sendStatus !== 'FAILED'} onClick={() => resend.mutate(row.messageId)}>重发</button>
                  <button type="button" data-testid="admin-message-receipt-send-details-appeal" disabled={row.sendStatus !== 'FAILED'} onClick={() => appeal.mutate(row.messageId)}>申诉</button>
                </td>
              </tr>
            ))}</tbody>
          </table>
        </section>
      )}

      {section === 'receipts' && (
        <section className="card">
          <h2>回执详情</h2>
          <table className="message-operations-table" data-testid="admin-message-receipt-receipt-details-table">
            <thead><tr><th>回执ID</th><th>消息ID</th><th>手机号</th><th>状态</th><th>错误码</th><th>通道</th><th>原始摘要</th><th>动作</th></tr></thead>
            <tbody>{(receipts.data ?? []).map((row) => (
              <tr key={row.receiptId} data-testid="admin-message-receipt-receipt-details-row">
                <td>{row.receiptId}</td><td>{row.messageId}</td><td>{row.maskedMobile}</td><td>{row.receiptStatus}</td><td>{row.errorCode || '-'}</td><td>{row.channelId}</td><td>{row.rawPayloadSummary}</td>
                <td>
                  <button type="button" data-testid="admin-message-receipt-receipt-correct" onClick={() => correct.mutate(row.receiptId)}>纠正为送达</button>
                  <button type="button" data-testid="admin-message-receipt-receipt-replay" onClick={() => replay.mutate(row.receiptId)}>重放</button>
                </td>
              </tr>
            ))}</tbody>
          </table>
        </section>
      )}

      {section === 'errors' && (
        <section className="card">
          <h2>错误详情</h2>
          <div className="message-operations-actions">
            <button type="button" data-testid="admin-message-receipt-error-details-bulk-retry" onClick={() => bulkRetry.mutate()}>批量重试</button>
            <button type="button" data-testid="admin-message-receipt-error-details-mark-problem" onClick={() => markProblem.mutate()}>标记问题</button>
          </div>
          <table className="message-operations-table" data-testid="admin-message-receipt-error-details-table">
            <thead><tr><th>归一化错误码</th><th>分类</th><th>级别</th><th>可重试</th><th>消息数</th><th>租户数</th><th>通道数</th><th>首次/最近</th></tr></thead>
            <tbody>{(errors.data ?? []).map((row) => (
              <tr key={row.normalizedCode} data-testid="admin-message-receipt-error-details-row">
                <td>{row.normalizedCode}</td><td>{row.platformCategory}</td><td>{row.severity}</td><td>{String(row.retryable)}</td><td>{row.totalCount}</td><td>{row.tenantCount}</td><td>{row.channelCount}</td><td>{row.firstSeenAt} / {row.lastSeenAt}</td>
              </tr>
            ))}</tbody>
          </table>
        </section>
      )}
    </section>
  );
}
