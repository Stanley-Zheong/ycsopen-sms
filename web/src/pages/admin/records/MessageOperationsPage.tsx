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
import ActionReasonDialog from '@/components/common/ActionReasonDialog';
import { QueryField, QueryPanel } from '@/components/common/QueryPanel';
import '@/styles/message-operations.css';

type Section = 'submissions' | 'sends' | 'receipts' | 'errors';

const SECTION_LABELS: Record<Section, string> = {
  submissions: '提交详情',
  sends: '发送详情',
  receipts: '回执详情',
  errors: '错误详情',
};

const DEFAULT_FILTER: OperationFilter = { tenantId: '42', messageId: '', status: '', errorCode: '' };

type PendingAction =
  | { kind: 'export'; section: Section; filter: OperationFilter }
  | { kind: 'resend' | 'appeal'; messageId: string }
  | { kind: 'correct' | 'replay'; receiptId: number; messageId: string }
  | { kind: 'bulk-retry' | 'mark-problem'; errorCode: string; messageIds: string[] };

const ACTION_LABELS: Record<PendingAction['kind'], string> = {
  export: '请求安全异步导出',
  resend: '重发消息',
  appeal: '提交申诉',
  correct: '纠正回执为送达',
  replay: '重放回执',
  'bulk-retry': '批量重试',
  'mark-problem': '标记问题',
};

function newActionId(prefix: string) {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return `${prefix}-${crypto.randomUUID()}`;
  }
  return `${prefix}-${Date.now()}`;
}

function actionTarget(action: PendingAction): string {
  switch (action.kind) {
    case 'export': {
      const scope = [
        action.filter.tenantId ? `机构 ${action.filter.tenantId}` : '',
        action.filter.messageId ? `消息 ${action.filter.messageId}` : '',
        action.filter.status ? `状态 ${action.filter.status}` : '',
        action.filter.errorCode ? `错误码 ${action.filter.errorCode}` : '',
      ].filter(Boolean);
      return `${SECTION_LABELS[action.section]}当前筛选结果 · ${scope.length ? scope.join(' · ') : '全部记录'}`;
    }
    case 'resend':
    case 'appeal': return `消息 ${action.messageId}`;
    case 'correct':
    case 'replay': return `回执 #${action.receiptId} · 消息 ${action.messageId}`;
    case 'bulk-retry':
    case 'mark-problem': return `错误码 ${action.errorCode} · ${action.messageIds.length} 条失败消息`;
  }
}

function actionConsequence(action: PendingAction): string {
  switch (action.kind) {
    case 'export': return '确认后将按当前已应用筛选条件登记安全异步导出请求。';
    case 'resend': return '确认后将为该失败消息创建一次新的发送尝试。';
    case 'appeal': return '确认后将为该失败消息登记运营申诉记录。';
    case 'correct': return '确认后将保留原始回执，并新增一条送达纠正记录。';
    case 'replay': return '确认后将按保存的回执数据重新执行处理。';
    case 'bulk-retry': return '确认后将重试当前错误组中的失败消息，并分别记录处理结果。';
    case 'mark-problem': return '确认后将把当前错误组中的失败消息标记为问题记录。';
  }
}

export default function MessageOperationsPage({ initialSection = 'submissions' }: { initialSection?: Section }) {
  const queryClient = useQueryClient();
  const [section, setSection] = useState<Section>(initialSection);
  const [draftFilter, setDraftFilter] = useState<OperationFilter>(DEFAULT_FILTER);
  const [appliedFilter, setAppliedFilter] = useState<OperationFilter>(DEFAULT_FILTER);
  const [pendingAction, setPendingAction] = useState<PendingAction | null>(null);
  const [reason, setReason] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  const filter: OperationFilter = useMemo(() => ({ ...appliedFilter }), [appliedFilter]);
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
    setPendingAction(null);
    setReason('');
    setMessage(text);
    setError('');
    await refresh();
  };

  const resend = useMutation({
    mutationFn: ({ messageId, actionReason }: { messageId: string; actionReason: string }) => resendMessage(messageId, newActionId('RESEND'), actionReason),
    onSuccess: (result) => ok(`重发已处理：${result.resultCode}`),
    onError: (failure) => fail(failure, '重发失败'),
  });
  const appeal = useMutation({
    mutationFn: ({ messageId, actionReason }: { messageId: string; actionReason: string }) => appealMessage(messageId, newActionId('APPEAL'), actionReason),
    onSuccess: (result) => ok(`申诉已登记：${result.resultCode}`),
    onError: (failure) => fail(failure, '申诉失败'),
  });
  const correct = useMutation({
    mutationFn: ({ receiptId, actionReason }: { receiptId: number; actionReason: string }) => correctReceipt(receiptId, newActionId('CORRECT'), actionReason, 'DELIVERED', '', 'ST20260909'),
    onSuccess: (result) => ok(`回执纠正已应用：${result.resultCode}`),
    onError: (failure) => fail(failure, '回执纠正失败'),
  });
  const replay = useMutation({
    mutationFn: ({ receiptId, actionReason }: { receiptId: number; actionReason: string }) => replayReceipt(receiptId, newActionId('REPLAY'), actionReason),
    onSuccess: (result) => ok(`回执重放已处理：${result.resultCode}`),
    onError: (failure) => fail(failure, '回执重放失败'),
  });
  const bulkRetry = useMutation({
    mutationFn: ({ errorCode, messageIds, actionReason }: { errorCode: string; messageIds: string[]; actionReason: string }) => bulkErrorAction(newActionId('BULK'), 'BULK_RETRY', errorCode, messageIds, actionReason),
    onSuccess: (result) => ok(`批量重试完成：成功 ${result.completed}，失败 ${result.failed}`),
    onError: (failure) => fail(failure, '批量重试失败'),
  });
  const markProblem = useMutation({
    mutationFn: ({ errorCode, messageIds, actionReason }: { errorCode: string; messageIds: string[]; actionReason: string }) => bulkErrorAction(newActionId('PROBLEM'), 'MARK_PROBLEM', errorCode, messageIds, actionReason),
    onSuccess: (result) => ok(`问题标记完成：成功 ${result.completed}，失败 ${result.failed}`),
    onError: (failure) => fail(failure, '问题标记失败'),
  });
  const exportRequest = useMutation({
    mutationFn: ({ actionFilter, actionReason, actionSection }: { actionFilter: OperationFilter; actionReason: string; actionSection: Section }) => requestMessageExport(actionFilter, newActionId('EXPORT'), actionReason,
      actionSection === 'sends' ? 'SEND_DETAIL' : actionSection === 'receipts' ? 'RECEIPT_DETAIL' : 'MESSAGE_OPERATIONS'),
    onSuccess: (result) => ok(`导出请求已登记：${result.resultMessage}`),
    onError: (failure) => fail(failure, '导出请求失败'),
  });

  const openAction = (action: PendingAction) => {
    setPendingAction(action);
    setReason('');
    setError('');
  };
  const openBulkAction = (kind: 'bulk-retry' | 'mark-problem') => openAction({
    kind,
    errorCode: filter.errorCode || errors.data?.[0]?.normalizedCode || 'UNKNOWN',
    messageIds: (sends.data ?? []).filter((row) => row.sendStatus === 'FAILED').map((row) => row.messageId),
  });
  const closeAction = () => {
    setPendingAction(null);
    setReason('');
  };
  const confirmAction = () => {
    if (!pendingAction) return;
    const actionReason = reason.trim();
    if (pendingAction.kind === 'export') exportRequest.mutate({ actionFilter: pendingAction.filter, actionReason, actionSection: pendingAction.section });
    if (pendingAction.kind === 'resend') resend.mutate({ messageId: pendingAction.messageId, actionReason });
    if (pendingAction.kind === 'appeal') appeal.mutate({ messageId: pendingAction.messageId, actionReason });
    if (pendingAction.kind === 'correct') correct.mutate({ receiptId: pendingAction.receiptId, actionReason });
    if (pendingAction.kind === 'replay') replay.mutate({ receiptId: pendingAction.receiptId, actionReason });
    if (pendingAction.kind === 'bulk-retry') bulkRetry.mutate({ ...pendingAction, actionReason });
    if (pendingAction.kind === 'mark-problem') markProblem.mutate({ ...pendingAction, actionReason });
  };
  const actionPending = resend.isPending || appeal.isPending || correct.isPending || replay.isPending
    || bulkRetry.isPending || markProblem.isPending || exportRequest.isPending;

  return (
    <section className="message-operations-page" data-testid="admin-message-receipt-operations-page">
      <nav aria-label="面包屑">数据详单 / 详单总览 / {SECTION_LABELS[section]}</nav>
      <h1>详单总览</h1>
      {section === 'submissions' && <p className="message-operations-section-marker" data-testid="admin-message-receipt-submission-details-page">提交详情页面</p>}
      {section === 'sends' && <p className="message-operations-section-marker" data-testid="admin-message-receipt-send-details-page">发送详情页面</p>}
      {section === 'receipts' && <p className="message-operations-section-marker" data-testid="admin-message-receipt-receipt-details-page">回执详情页面</p>}
      {section === 'errors' && <p className="message-operations-section-marker" data-testid="admin-message-receipt-error-details-page">错误详情页面</p>}
      <header className="message-operations-header">
        <div>
          <h1>消息、回执与错误运营</h1>
          <p className="page-description">按租户、状态、消息和错误码追踪提交、发送、回执、错误聚合，并执行有原因、有幂等键的运营动作。</p>
        </div>
        {section === 'sends' && <button type="button" data-testid="admin-secure-async-send-details-export" onClick={() => openAction({ kind: 'export', section, filter: { ...filter } })}>请求安全异步导出</button>}
        {section === 'receipts' && <button type="button" data-testid="admin-secure-async-receipt-export" onClick={() => openAction({ kind: 'export', section, filter: { ...filter } })}>请求安全异步导出</button>}
        {section !== 'sends' && section !== 'receipts' && <button type="button" data-testid="admin-message-receipt-export-request" onClick={() => openAction({ kind: 'export', section, filter: { ...filter } })}>请求安全异步导出</button>}
      </header>

      {message && <p role="status" className="message-operations-alert success" data-testid="admin-message-receipt-operation-message">{message}</p>}
      {error && <p role="alert" className="message-operations-alert error" data-testid="admin-message-receipt-operation-error">{error}</p>}

      <QueryPanel
        legacyPanelTestId="admin-message-receipt-query-panel"
        onSubmit={() => setAppliedFilter({ ...draftFilter })}
        onReset={() => {
          setDraftFilter(DEFAULT_FILTER);
          setAppliedFilter(DEFAULT_FILTER);
        }}
        result={(<>
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
                  <button type="button" data-testid="admin-message-receipt-send-details-resend" disabled={row.sendStatus !== 'FAILED'} onClick={() => openAction({ kind: 'resend', messageId: row.messageId })}>重发</button>
                  <button type="button" data-testid="admin-message-receipt-send-details-appeal" disabled={row.sendStatus !== 'FAILED'} onClick={() => openAction({ kind: 'appeal', messageId: row.messageId })}>申诉</button>
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
                  <button type="button" data-testid="admin-message-receipt-receipt-correct" onClick={() => openAction({ kind: 'correct', receiptId: row.receiptId, messageId: row.messageId })}>纠正为送达</button>
                  <button type="button" data-testid="admin-message-receipt-receipt-replay" onClick={() => openAction({ kind: 'replay', receiptId: row.receiptId, messageId: row.messageId })}>重放</button>
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
            <button type="button" data-testid="admin-message-receipt-error-details-bulk-retry" onClick={() => openBulkAction('bulk-retry')}>批量重试</button>
            <button type="button" data-testid="admin-message-receipt-error-details-mark-problem" onClick={() => openBulkAction('mark-problem')}>标记问题</button>
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
        </>)}
      >
        <QueryField name="tenant-id" label="租户ID">
          <input data-testid="admin-message-receipt-filter-tenant" value={draftFilter.tenantId ?? ''} onChange={(event) => setDraftFilter((current) => ({ ...current, tenantId: event.target.value }))} />
        </QueryField>
        <QueryField name="message-id" label="消息ID">
          <input data-testid="admin-message-receipt-filter-message" value={draftFilter.messageId ?? ''} onChange={(event) => setDraftFilter((current) => ({ ...current, messageId: event.target.value }))} />
        </QueryField>
        <QueryField name="status" label="状态">
          <input data-testid="admin-message-receipt-filter-status" value={draftFilter.status ?? ''} onChange={(event) => setDraftFilter((current) => ({ ...current, status: event.target.value }))} />
        </QueryField>
        <QueryField name="error-code" label="错误码">
          <input data-testid="admin-message-receipt-filter-error-code" value={draftFilter.errorCode ?? ''} onChange={(event) => setDraftFilter((current) => ({ ...current, errorCode: event.target.value }))} />
        </QueryField>
      </QueryPanel>

      {pendingAction && (
        <ActionReasonDialog
          idPrefix="admin-message-receipt-action"
          title={`确认${ACTION_LABELS[pendingAction.kind]}`}
          target={actionTarget(pendingAction)}
          consequence={actionConsequence(pendingAction)}
          reasonLabel={`${ACTION_LABELS[pendingAction.kind]}原因`}
          reasonTestId="admin-message-receipt-action-reason"
          reason={reason}
          placeholder="请填写本次操作的复核依据"
          confirmLabel={`确认${ACTION_LABELS[pendingAction.kind]}`}
          pending={actionPending}
          onReasonChange={setReason}
          onCancel={closeAction}
          onConfirm={confirmAction}
        />
      )}
    </section>
  );
}
