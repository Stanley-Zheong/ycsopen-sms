import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  approveTenantTermination,
  effectTenantTermination,
  getTenantTermination,
  listTenantTerminations,
  listTerminationParticipants,
  parseClearance,
  refreshTerminationClearance,
  requestTenantTermination,
  type TerminationDetail,
  type TerminationRequestView,
} from '@/api/tenantTerminationApi';
import { mutationErrorMessage } from '@/api/client';
import '@/styles/tenant-termination.css';

function statusLabel(status: string): string {
  return ({
    BLOCKED_CLEARANCE: '清算阻塞',
    PENDING_ADMIN_APPROVAL: '待管理员审批',
    APPROVED: '已审批待生效',
    EFFECTIVE: '已终止生效',
    REJECTED: '已拒绝',
  } as Record<string, string>)[status] ?? status;
}

function reasonLabel(reason: string): string {
  return ({
    VOLUNTARY: '自愿终止',
    SEVERE_VIOLATION: '严重违规',
    LONG_TERM_ARREARS: '长期欠费',
    EXPIRED_CREDENTIALS: '资质到期未续',
  } as Record<string, string>)[reason] ?? reason;
}

function displayTime(value: string | null | undefined): string {
  if (!value) return '-';
  return new Date(value).toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' });
}

export default function AdminTenantTerminationPage() {
  const queryClient = useQueryClient();
  const [tenantId, setTenantId] = useState('42');
  const [status, setStatus] = useState('');
  const [reason, setReason] = useState('VOLUNTARY');
  const [requestEvidence, setRequestEvidence] = useState('termination-ticket:T-4901');
  const [opinion, setOpinion] = useState('finance and operation clearance accepted');
  const [selected, setSelected] = useState<TerminationDetail | null>(null);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const terminations = useQuery({
    queryKey: ['tenant-terminations', tenantId, status],
    queryFn: () => listTenantTerminations({ tenantId, status }),
    retry: false,
  });
  const inventory = useQuery({
    queryKey: ['tenant-termination-participant-inventory'],
    queryFn: listTerminationParticipants,
    retry: false,
  });

  const onSuccess = async (detail: TerminationDetail, text: string) => {
    setSelected(detail);
    setMessage(text);
    setError('');
    await queryClient.invalidateQueries({ queryKey: ['tenant-terminations'] });
    await queryClient.invalidateQueries({ queryKey: ['tenant-termination-participant-inventory'] });
  };
  const onError = (failure: unknown, fallback: string) => {
    setMessage('');
    setError(mutationErrorMessage(failure, fallback));
  };
  const request = useMutation({
    mutationFn: () => requestTenantTermination({ tenantId: Number(tenantId), reason, requestEvidence }),
    onSuccess: (detail) => void onSuccess(detail, '终止请求已创建'),
    onError: (failure) => onError(failure, '终止请求创建失败'),
  });
  const loadDetail = useMutation({
    mutationFn: (row: TerminationRequestView) => getTenantTermination(row.id),
    onSuccess: (detail) => void onSuccess(detail, '终止详情已加载'),
    onError: (failure) => onError(failure, '终止详情加载失败'),
  });
  const refresh = useMutation({
    mutationFn: () => refreshTerminationClearance(activeRequest().id),
    onSuccess: (detail) => void onSuccess(detail, '清算检查已刷新'),
    onError: (failure) => onError(failure, '清算检查刷新失败'),
  });
  const approve = useMutation({
    mutationFn: () => approveTenantTermination(activeRequest().id, opinion),
    onSuccess: (detail) => void onSuccess(detail, '终止请求已审批'),
    onError: (failure) => onError(failure, '终止审批失败'),
  });
  const effect = useMutation({
    mutationFn: () => effectTenantTermination(activeRequest().id),
    onSuccess: (detail) => void onSuccess(detail, '终止已生效，入口与资源已收敛'),
    onError: (failure) => onError(failure, '终止生效失败'),
  });
  const rows = terminations.data ?? [];
  const active = selected?.request ?? rows[0] ?? null;
  const clearance = parseClearance(selected?.request ?? active);
  const participants = selected?.participants ?? [];
  const audits = selected?.audits ?? [];

  function activeRequest(): TerminationRequestView {
    if (!active) throw new Error('没有可操作的终止请求');
    return active;
  }

  return (
    <section className="tenant-termination-page" data-testid="admin-tenant-cooperation-tenant-termination-page">
      <nav aria-label="面包屑">平台管理 / 机构合作终止</nav>
      <header className="tenant-termination-header">
        <div>
          <h1>机构合作终止</h1>
          <p className="page-description">申请、清算阻塞、管理员审批、生效收敛、历史留存统一在一个终止请求中记录。</p>
        </div>
      </header>

      {message && <p role="status" className="tenant-termination-alert success" data-testid="admin-tenant-cooperation-tenant-termination-message">{message}</p>}
      {error && <p role="alert" className="tenant-termination-alert error" data-testid="admin-tenant-cooperation-tenant-termination-error">{error}</p>}

      <section className="tenant-termination-grid">
        <section className="card tenant-termination-card" data-testid="admin-tenant-cooperation-tenant-termination-request">
          <h2>终止申请</h2>
          <label>机构ID
            <input data-testid="admin-tenant-cooperation-tenant-termination-tenant-id" value={tenantId} onChange={(event) => setTenantId(event.target.value)} />
          </label>
          <label>终止原因
            <select data-testid="admin-tenant-cooperation-tenant-termination-reason" value={reason} onChange={(event) => setReason(event.target.value)}>
              <option value="VOLUNTARY">自愿终止</option>
              <option value="SEVERE_VIOLATION">严重违规</option>
              <option value="LONG_TERM_ARREARS">长期欠费</option>
              <option value="EXPIRED_CREDENTIALS">资质到期未续</option>
            </select>
          </label>
          <label>申请依据
            <textarea data-testid="admin-tenant-cooperation-tenant-termination-evidence" value={requestEvidence} onChange={(event) => setRequestEvidence(event.target.value)} />
          </label>
          <button type="button" data-testid="admin-tenant-cooperation-tenant-termination-submit" onClick={() => request.mutate()} disabled={request.isPending}>
            创建终止请求
          </button>
        </section>

        <section className="card tenant-termination-card" data-testid="admin-tenant-cooperation-tenant-termination-clearance">
          <h2>清算检查</h2>
          <p data-testid="admin-tenant-cooperation-tenant-termination-clearance-status">
            状态：{clearance?.clearancePassed ? '通过' : '阻塞'} / 生命周期：{clearance?.lifecycleStatus ?? '-'} / 计费：{clearance?.billingMode ?? '-'}
          </p>
          <ul>
            {(clearance?.items ?? []).map((item) => (
              <li key={item.code} data-testid="admin-tenant-cooperation-tenant-termination-clearance-item">
                {item.code}：{item.passed ? '通过' : '阻塞'}，数量/金额 {item.amountOrCount}，{item.evidence}
              </li>
            ))}
          </ul>
          <button type="button" data-testid="admin-tenant-cooperation-tenant-termination-refresh-clearance" onClick={() => refresh.mutate()} disabled={!active || refresh.isPending}>
            刷新清算
          </button>
        </section>

        <section className="card tenant-termination-card" data-testid="admin-tenant-cooperation-tenant-termination-approval">
          <h2>审批与生效</h2>
          <p data-testid="admin-tenant-cooperation-tenant-termination-active-status">当前：{active ? statusLabel(active.requestStatus) : '-'}</p>
          <label>审批意见
            <input data-testid="admin-tenant-cooperation-tenant-termination-approval-opinion" value={opinion} onChange={(event) => setOpinion(event.target.value)} />
          </label>
          <div className="tenant-termination-actions">
            <button type="button" data-testid="admin-tenant-cooperation-tenant-termination-approve" onClick={() => approve.mutate()} disabled={!active || approve.isPending}>
              管理员审批
            </button>
            <button type="button" data-testid="admin-tenant-cooperation-tenant-termination-effect" onClick={() => effect.mutate()} disabled={!active || effect.isPending}>
              终止生效
            </button>
          </div>
        </section>
      </section>

      <section className="card tenant-termination-card">
        <h2>终止请求列表</h2>
        <label>状态过滤
          <select data-testid="admin-tenant-cooperation-tenant-termination-status-filter" value={status} onChange={(event) => setStatus(event.target.value)}>
            <option value="">全部</option>
            <option value="BLOCKED_CLEARANCE">清算阻塞</option>
            <option value="PENDING_ADMIN_APPROVAL">待审批</option>
            <option value="APPROVED">已审批</option>
            <option value="EFFECTIVE">已生效</option>
          </select>
        </label>
        <table className="tenant-termination-table" data-testid="admin-tenant-cooperation-tenant-termination-table">
          <thead>
            <tr>
              <th>请求ID</th><th>机构</th><th>原因</th><th>状态</th><th>申请人</th><th>审批人</th><th>生效时间</th><th>动作</th>
            </tr>
          </thead>
          <tbody>{rows.map((row) => (
            <tr key={row.id} data-testid="admin-tenant-cooperation-tenant-termination-row">
              <td>{row.id}</td>
              <td>{row.tenantId}</td>
              <td>{reasonLabel(row.reason)}</td>
              <td data-testid="admin-tenant-cooperation-tenant-termination-row-status">{statusLabel(row.requestStatus)}</td>
              <td>{row.requestedBy}</td>
              <td>{row.approvedBy ?? '-'}</td>
              <td>{displayTime(row.effectiveAt)}</td>
              <td><button type="button" data-testid="admin-tenant-cooperation-tenant-termination-detail" onClick={() => loadDetail.mutate(row)}>查看证据</button></td>
            </tr>
          ))}</tbody>
        </table>
      </section>

      <section className="card tenant-termination-card">
        <h2>参与方清单</h2>
        <table className="tenant-termination-table" data-testid="admin-tenant-cooperation-tenant-termination-participants">
          <thead>
            <tr><th>参与方</th><th>状态</th><th>阻塞/保留数量</th><th>证据</th></tr>
          </thead>
          <tbody>
            {(participants.length ? participants : (inventory.data ?? []).map((item) => ({
              id: 0,
              requestId: 0,
              tenantId: Number(tenantId),
              participantCode: item.code,
              participantName: item.name,
              participantState: 'INVENTORY',
              blockerCount: 0,
              evidenceJson: item.rule,
            }))).map((item) => (
              <tr key={item.participantCode} data-testid="admin-tenant-cooperation-tenant-termination-participant-row">
                <td>{item.participantCode} / {item.participantName}</td>
                <td>{item.participantState}</td>
                <td>{item.blockerCount}</td>
                <td>{item.evidenceJson}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section className="card tenant-termination-card" data-testid="admin-tenant-cooperation-tenant-termination-timeline">
        <h2>时间线与留存证据</h2>
        <ol>
          {(audits.length ? audits : active ? [{
            id: active.id,
            action: active.requestStatus,
            actor: active.requestedBy,
            resultStatus: active.requestStatus,
            createdAt: active.requestedAt,
            evidenceJson: active.requestEvidence,
          }] : []).map((item) => (
            <li key={`${item.id}-${item.action}`} data-testid="admin-tenant-cooperation-tenant-termination-timeline-item">
              {displayTime(item.createdAt)} / {item.action} / {item.actor} / {item.resultStatus} / {item.evidenceJson}
            </li>
          ))}
        </ol>
      </section>
    </section>
  );
}
