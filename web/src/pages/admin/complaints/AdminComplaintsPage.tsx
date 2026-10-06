import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { mutationErrorMessage } from '@/api/client';
import {
  acceptComplaintCase,
  closeComplaintCase,
  complaintFailureCode,
  createComplaintCase,
  getComplaintCaseDetail,
  getComplaintReferenceOptions,
  handleComplaintCase,
  listComplaintCases,
  recoverComplaintRemediation,
  remediateComplaintCase,
  type ComplaintCaseEvent,
  type ComplaintCaseRow,
  type ComplaintReferenceOption,
} from '@/api/complaintCaseApi';
import ModalDialog from '@/components/common/ModalDialog';
import { useAuthStore } from '@/store/authStore';
import '@/styles/alert-engine.css';

const emptyIntake = {
  source: '',
  summary: '',
  tenantId: '',
  channelId: '',
  signatureId: '',
  templateId: '',
  messageId: '',
  contentType: '',
  complainedMobile: '',
  requirement: '',
};

const emptyActionDraft = {
  opinion: '',
  remediation: '',
  requirement: '',
  disposalType: '',
  reviewId: '',
  reason: '',
  recoveryReviewId: '',
  resumeCondition: '',
  closeNote: '',
};

type ActionKind = 'accept' | 'handle' | 'remediate' | 'recover' | 'close';
type DisposalChoice = 'BLACKLIST_MOBILE' | 'SUSPEND_TENANT' | 'SUSPEND_SIGNATURE_OR_TEMPLATE' | 'SUSPEND_CHANNEL';

const actionCopy: Record<ActionKind, { title: string; effect: string; success: string }> = {
  accept: { title: '接单', effect: '案件将进入 PROCESSING，接单意见会写入时间线。', success: '投诉案件已接单' },
  handle: { title: '完成处理', effect: '案件将进入 PROCESSED，处理、整改和要求证据会写入时间线。', success: '投诉案件已处理' },
  remediate: { title: '资源处置', effect: '系统将处置所选的案件归因资源，并保留成功或失败证据。', success: '处置请求已完成' },
  recover: { title: '恢复处置', effect: '系统只恢复当前案件最新的失败处置记录，不会重开案件。', success: '恢复已记录' },
  close: { title: '关闭案件', effect: '案件将进入 CLOSED，关闭说明会写入时间线。', success: '投诉案件已关闭' },
};

function actionEligible(action: ActionKind, status: string, failedRecordId: number | null): boolean {
  if (action === 'accept') return status === 'PENDING';
  if (action === 'handle') return status === 'PROCESSING';
  if (action === 'remediate' || action === 'close') return status === 'PROCESSED';
  return failedRecordId != null && ['PROCESSED', 'CLOSED'].includes(status);
}

function numberOrNull(value: string): number | null {
  const parsed = Number(value);
  return Number.isFinite(parsed) && value.trim() !== '' ? parsed : null;
}

function targetSummary(row: ComplaintCaseRow): string {
  return [
    row.complainedMobile ? `mobile:${row.complainedMobile}` : 'mobile:UNKNOWN',
    row.tenantId ? `tenant:${row.tenantId}` : 'tenant:UNKNOWN',
    row.signatureId ? `signature:${row.signatureId}` : 'signature:UNKNOWN',
    row.templateId ? `template:${row.templateId}` : 'template:UNKNOWN',
    row.channelId ? `channel:${row.channelId}` : 'channel:UNKNOWN',
  ].join(' / ');
}

function targetForChoice(row: ComplaintCaseRow, choice: string): string | null {
  if (choice === 'BLACKLIST_MOBILE' && row.complainedMobile) return `mobile:${row.complainedMobile}`;
  if (choice === 'SUSPEND_TENANT' && row.tenantId) return `tenant:${row.tenantId}`;
  if (choice === 'SUSPEND_CHANNEL' && row.channelId) return `channel:${row.channelId}`;
  if (choice === 'SUSPEND_SIGNATURE_OR_TEMPLATE') {
    if (row.signatureId) return `signature:${row.signatureId}`;
    if (row.templateId) return `template:${row.templateId}`;
  }
  return null;
}

function optionLabel(option: ComplaintReferenceOption): string {
  return `${option.label} (#${option.id})`;
}

function display(value: string | number | null | undefined): string {
  return value == null || value === '' ? 'UNKNOWN' : String(value);
}

function eventEvidence(event: ComplaintCaseEvent): string {
  const parts = [
    event.evidenceText && `证据：${event.evidenceText}`,
    event.targetRef && `目标：${event.targetRef}`,
    event.reviewId && `复核：${event.reviewId}`,
    event.failureReason && `失败：${event.failureReason}`,
    event.relatedDisposalId && `处置记录：#${event.relatedDisposalId}`,
  ].filter(Boolean);
  return parts.length > 0 ? parts.join('；') : '未保留历史证据';
}

function ActionDialog({
  action,
  complaint,
  failedRecordId,
  draft,
  pending,
  error,
  stale,
  onDraftChange,
  onCancel,
  onConfirm,
}: {
  action: ActionKind;
  complaint: ComplaintCaseRow;
  failedRecordId: number | null;
  draft: typeof emptyActionDraft;
  pending: boolean;
  error: string;
  stale: boolean;
  onDraftChange: (field: keyof typeof emptyActionDraft, value: string) => void;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const submitLatched = useRef(false);
  const observedPending = useRef(pending);
  const copy = actionCopy[action];
  const targetRef = action === 'remediate' ? targetForChoice(complaint, draft.disposalType) : null;
  const eligible = actionEligible(action, complaint.status, failedRecordId);
  const valid = eligible && (action === 'accept'
    ? Boolean(draft.opinion.trim())
    : action === 'handle'
      ? Boolean(draft.opinion.trim() && draft.remediation.trim() && draft.requirement.trim())
      : action === 'remediate'
        ? Boolean(draft.disposalType && targetRef && draft.reviewId.trim() && draft.reason.trim())
        : action === 'recover'
          ? Boolean(failedRecordId && draft.recoveryReviewId.trim() && draft.resumeCondition.trim())
          : Boolean(draft.closeNote.trim()));

  const requestClose = () => {
    if (!pending && !submitLatched.current) onCancel();
  };

  useEffect(() => {
    if (pending) {
      observedPending.current = true;
    } else if (observedPending.current) {
      submitLatched.current = false;
      observedPending.current = false;
    }
  }, [pending]);

  return (
    <div
      className="action-reason-dialog-backdrop complaint-action-backdrop"
      data-testid="admin-complaint-case-complaints-action-backdrop"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) requestClose();
      }}
    >
      <ModalDialog labelledBy="complaint-action-title" onRequestClose={requestClose}>
        <form
          className="action-reason-dialog complaint-action-dialog"
          data-testid="admin-complaint-case-complaints-action-dialog"
          aria-busy={pending}
          onSubmit={(event) => {
            event.preventDefault();
            if (!valid || pending || submitLatched.current) return;
            submitLatched.current = true;
            onConfirm();
          }}
        >
          <div className="complaint-action-heading">
            <h2 id="complaint-action-title">{copy.title}</h2>
            <button
              type="button"
              className="complaint-action-close"
              aria-label="关闭动作弹窗"
              data-testid="admin-complaint-case-complaints-action-close"
              disabled={pending}
              onClick={requestClose}
            >×</button>
          </div>
          <p className="action-reason-target" data-testid="admin-complaint-case-complaints-action-target">
            案件 #{complaint.id} · 机构 {display(complaint.tenantId)} · {complaint.summary}
          </p>
          <p data-testid="admin-complaint-case-complaints-action-current-status">当前状态：{complaint.status}</p>
          <p data-testid="admin-complaint-case-complaints-action-effect">操作影响：{copy.effect}</p>

          {action === 'accept' && (
            <label>接单意见 <span aria-hidden="true">*</span>
              <textarea autoFocus data-testid="admin-complaint-case-complaints-opinion" value={draft.opinion} disabled={pending} onChange={(event) => onDraftChange('opinion', event.target.value)} />
            </label>
          )}
          {action === 'handle' && (
            <div className="complaint-action-fields">
              <label>处理意见 <span aria-hidden="true">*</span><textarea autoFocus data-testid="admin-complaint-case-complaints-opinion" value={draft.opinion} disabled={pending} onChange={(event) => onDraftChange('opinion', event.target.value)} /></label>
              <label>处置动作 <span aria-hidden="true">*</span><textarea data-testid="admin-complaint-case-complaints-remediation-note" value={draft.remediation} disabled={pending} onChange={(event) => onDraftChange('remediation', event.target.value)} /></label>
              <label>整改要求 <span aria-hidden="true">*</span><textarea data-testid="admin-complaint-case-complaints-state-requirement" value={draft.requirement} disabled={pending} onChange={(event) => onDraftChange('requirement', event.target.value)} /></label>
            </div>
          )}
          {action === 'remediate' && (
            <div className="complaint-action-fields">
              <label>处置方式 <span aria-hidden="true">*</span>
                <select autoFocus data-testid="admin-complaint-case-complaints-disposal-type" value={draft.disposalType} disabled={pending} onChange={(event) => onDraftChange('disposalType', event.target.value)}>
                  <option value="">请选择处置方式</option>
                  <option value="SUSPEND_CHANNEL">暂停通道</option>
                  <option value="SUSPEND_SIGNATURE_OR_TEMPLATE">停用签名/模板</option>
                  <option value="SUSPEND_TENANT">冻结机构</option>
                  <option value="BLACKLIST_MOBILE">号码黑名单</option>
                </select>
              </label>
              <p className="complaint-action-readonly" data-testid="admin-complaint-case-complaints-action-target-ref">处置目标：{targetRef ?? '当前选择没有可用归因目标'}</p>
              <label>授权复核 ID <span aria-hidden="true">*</span><input data-testid="admin-complaint-case-complaints-review-id" value={draft.reviewId} disabled={pending} onChange={(event) => onDraftChange('reviewId', event.target.value)} /></label>
              <label>处置原因 <span aria-hidden="true">*</span><textarea data-testid="admin-complaint-case-complaints-remediation-reason" value={draft.reason} disabled={pending} onChange={(event) => onDraftChange('reason', event.target.value)} /></label>
            </div>
          )}
          {action === 'recover' && (
            <div className="complaint-action-fields">
              <p className="complaint-action-readonly" data-testid="admin-complaint-case-complaints-recovery-record">失败处置记录：#{failedRecordId}</p>
              <label>恢复复核 ID <span aria-hidden="true">*</span><input autoFocus data-testid="admin-complaint-case-complaints-recovery-review-id" value={draft.recoveryReviewId} disabled={pending} onChange={(event) => onDraftChange('recoveryReviewId', event.target.value)} /></label>
              <label>恢复条件 <span aria-hidden="true">*</span><textarea data-testid="admin-complaint-case-complaints-recovery-condition" value={draft.resumeCondition} disabled={pending} onChange={(event) => onDraftChange('resumeCondition', event.target.value)} /></label>
            </div>
          )}
          {action === 'close' && (
            <label>关闭说明 <span aria-hidden="true">*</span>
              <textarea autoFocus data-testid="admin-complaint-case-complaints-close-note" value={draft.closeNote} disabled={pending} onChange={(event) => onDraftChange('closeNote', event.target.value)} />
            </label>
          )}

          {stale && <p role="alert" className="alert-engine-message error" data-testid="admin-complaint-case-complaints-action-stale">案件状态已变化，已刷新最新状态，请核对后重试。</p>}
          {error && !stale && <p role="alert" className="alert-engine-message error" data-testid="admin-complaint-case-complaints-action-error">{error}</p>}
          <div className="action-reason-dialog-actions" data-testid="form-actions">
            <button type="button" className="button-secondary" data-testid="admin-complaint-case-complaints-action-cancel" disabled={pending} onClick={requestClose}>取消</button>
            <button type="submit" data-testid="admin-complaint-case-complaints-action-confirm" disabled={!valid || pending}>{pending ? '提交中…' : `确认${copy.title}`}</button>
          </div>
        </form>
      </ModalDialog>
    </div>
  );
}

export default function AdminComplaintsPage() {
  const queryClient = useQueryClient();
  const createLatched = useRef(false);
  const userType = useAuthStore((state) => state.userType);
  const canMutate = userType === 'ADMIN' || userType === 'OPERATOR';
  const [draft, setDraft] = useState(emptyIntake);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [action, setAction] = useState<ActionKind | null>(null);
  const [actionDraft, setActionDraft] = useState(emptyActionDraft);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');
  const [stale, setStale] = useState(false);

  const cases = useQuery({ queryKey: ['complaint-cases'], queryFn: listComplaintCases, retry: false });
  const options = useQuery({
    queryKey: ['complaint-reference-options'],
    queryFn: getComplaintReferenceOptions,
    retry: false,
    enabled: canMutate,
  });
  const caseDetail = useQuery({
    queryKey: ['complaint-case-detail', selectedId],
    queryFn: () => getComplaintCaseDetail(selectedId!),
    retry: false,
    enabled: selectedId != null,
  });

  const complaint = caseDetail.data?.complaint ?? null;
  const failedRemediation = caseDetail.data?.remediations
    .find((item) => item.status === 'FAILED') ?? null;

  async function refresh(includeOptions = false) {
    const work = [queryClient.invalidateQueries({ queryKey: ['complaint-cases'] })];
    if (selectedId != null) work.push(queryClient.invalidateQueries({ queryKey: ['complaint-case-detail', selectedId] }));
    if (includeOptions && canMutate) work.push(queryClient.invalidateQueries({ queryKey: ['complaint-reference-options'] }));
    await Promise.all(work);
  }

  const createCase = useMutation({
    mutationFn: () => createComplaintCase({
      source: draft.source,
      summary: draft.summary.trim(),
      tenantId: numberOrNull(draft.tenantId),
      channelId: numberOrNull(draft.channelId),
      signatureId: numberOrNull(draft.signatureId),
      templateId: numberOrNull(draft.templateId),
      messageId: draft.messageId.trim(),
      contentType: draft.contentType,
      complainedMobile: draft.complainedMobile.trim(),
      requirement: draft.requirement.trim(),
    }),
    onSuccess: async (created) => {
      setMessage('投诉案件已登记');
      setError('');
      setDraft(emptyIntake);
      setSelectedId(created.id);
      await queryClient.invalidateQueries({ queryKey: ['complaint-cases'] });
    },
    onError: (failure) => {
      setError(mutationErrorMessage(failure, '投诉案件登记失败'));
      setMessage('');
    },
    onSettled: () => {
      createLatched.current = false;
    },
  });

  const command = useMutation({
    mutationFn: async ({ actionKind, row }: { actionKind: ActionKind; row: ComplaintCaseRow }) => {
      if (actionKind === 'accept') {
        return acceptComplaintCase(row.id, { status: 'PROCESSING', opinion: actionDraft.opinion.trim(), remediation: '', requirement: '' });
      }
      if (actionKind === 'handle') {
        return handleComplaintCase(row.id, { status: 'PROCESSED', opinion: actionDraft.opinion.trim(), remediation: actionDraft.remediation.trim(), requirement: actionDraft.requirement.trim() });
      }
      if (actionKind === 'close') {
        return closeComplaintCase(row.id, { status: 'CLOSED', opinion: actionDraft.closeNote.trim(), remediation: '', requirement: '' });
      }
      if (actionKind === 'remediate') {
        const targetRef = targetForChoice(row, actionDraft.disposalType);
        if (!targetRef) throw new Error('当前处置方式没有可用归因目标');
        return remediateComplaintCase(row.id, {
          disposalType: actionDraft.disposalType as DisposalChoice,
          targetRef,
          authorizedReviewId: actionDraft.reviewId.trim(),
          reason: actionDraft.reason.trim(),
        });
      }
      if (!failedRemediation) throw new Error('没有可恢复的失败处置记录');
      return recoverComplaintRemediation(row.id, {
        disposalRecordId: failedRemediation.id,
        authorizedReviewId: actionDraft.recoveryReviewId.trim(),
        resumeCondition: actionDraft.resumeCondition.trim(),
      });
    },
  });

  const setField = (field: keyof typeof emptyIntake, value: string) => {
    setDraft((current) => field === 'tenantId'
      ? { ...current, tenantId: value, signatureId: '', templateId: '' }
      : { ...current, [field]: value });
  };

  const openAction = (next: ActionKind) => {
    setActionDraft(emptyActionDraft);
    setActionError('');
    setStale(false);
    setAction(next);
  };

  const closeAction = () => {
    setAction(null);
    setActionDraft(emptyActionDraft);
    setActionError('');
    setStale(false);
  };

  const submitAction = async () => {
    if (!action || !complaint) return;
    setActionError('');
    setStale(false);
    try {
      const completed = await command.mutateAsync({ actionKind: action, row: complaint });
      const success = action === 'remediate' && 'status' in completed && completed.status === 'FAILED'
        ? '处置失败，已保留恢复记录'
        : actionCopy[action].success;
      setMessage(success);
      setError('');
      closeAction();
      await refresh();
    } catch (failure) {
      if (complaintFailureCode(failure) === 'COMPLAINT_STATE_STALE') {
        setStale(true);
        setActionError('案件状态已变化');
        await refresh();
      } else {
        setActionError(mutationErrorMessage(failure, '操作失败，请核对后重试'));
      }
    }
  };

  const signatureOptions = (options.data?.signatures ?? []).filter((item) => !draft.tenantId || item.tenantId === numberOrNull(draft.tenantId));
  const templateOptions = (options.data?.templates ?? []).filter((item) => !draft.tenantId || item.tenantId === numberOrNull(draft.tenantId));
  const intakeValid = Boolean(draft.source && draft.summary.trim());

  return (
    <section className="alert-engine-page" data-testid="admin-complaint-case-complaints-page">
      <nav aria-label="面包屑">运营管理 / 投诉管理 / 投诉闭环</nav>
      <header className="alert-engine-header">
        <div>
          <h1>投诉闭环管理</h1>
          <p className="page-description">登记真实投诉并在单案上下文中处理；缺失归因保持 UNKNOWN。</p>
        </div>
        <button type="button" onClick={() => void refresh(true)} data-testid="admin-complaint-case-complaints-refresh">刷新</button>
      </header>

      {message && <p role="status" className="alert-engine-message success" data-testid="admin-complaint-case-complaints-message">{message}</p>}
      {error && <p role="alert" className="alert-engine-message error" data-testid="admin-complaint-case-complaints-error">{error}</p>}

      {canMutate && (
        <form
          className="complaint-intake-form"
          data-testid="entity-form"
          onSubmit={(event) => {
            event.preventDefault();
            if (!intakeValid || createCase.isPending || createLatched.current) return;
            createLatched.current = true;
            createCase.mutate();
          }}
        >
          <section className="card complaint-section-card" data-testid="admin-complaint-case-complaints-intake-card">
            <h2>投诉登记</h2>
            <div className="complaint-field-grid">
              <label>来源 <span aria-hidden="true">*</span>
                <select data-testid="admin-complaint-case-complaints-source" value={draft.source} onChange={(event) => setField('source', event.target.value)}>
                  <option value="">请选择来源</option><option value="REGULATOR">监管机构</option><option value="CARRIER">运营商</option><option value="OPERATOR">平台运营</option><option value="USER_REPORT">用户举报</option>
                </select>
              </label>
              <label>摘要 <span aria-hidden="true">*</span><input data-testid="admin-complaint-case-complaints-summary" value={draft.summary} onChange={(event) => setField('summary', event.target.value)} /></label>
            </div>
          </section>

          <section className="card complaint-section-card" data-testid="admin-complaint-case-complaints-attribution-card">
            <h2>归因与要求</h2>
            <div data-testid="admin-complaint-case-complaints-reference-options">
              {options.isLoading && <p data-testid="admin-complaint-case-complaints-reference-options-state">正在加载归因选项…</p>}
              {options.isError && <p role="alert" data-testid="admin-complaint-case-complaints-reference-options-state">归因选项加载失败。 <button type="button" data-testid="admin-complaint-case-complaints-reference-options-retry" onClick={() => void options.refetch()}>重试</button></p>}
              {options.isSuccess && options.data.tenants.length + options.data.channels.length + options.data.signatures.length + options.data.templates.length === 0 && <p data-testid="admin-complaint-case-complaints-reference-options-state">暂无可选归因资源，可保留未知归因。</p>}
            </div>
            <div className="complaint-field-grid">
              <label>机构<select data-testid="admin-complaint-case-complaints-tenant-id" value={draft.tenantId} disabled={!options.isSuccess} onChange={(event) => setField('tenantId', event.target.value)}><option value="">未知机构</option>{options.data?.tenants.map((item) => <option key={item.id} value={item.id}>{optionLabel(item)}</option>)}</select></label>
              <label>通道<select data-testid="admin-complaint-case-complaints-channel-id" value={draft.channelId} disabled={!options.isSuccess} onChange={(event) => setField('channelId', event.target.value)}><option value="">未知通道</option>{options.data?.channels.map((item) => <option key={item.id} value={item.id}>{optionLabel(item)}</option>)}</select></label>
              <label>签名<select data-testid="admin-complaint-case-complaints-signature-id" value={draft.signatureId} disabled={!options.isSuccess} onChange={(event) => setField('signatureId', event.target.value)}><option value="">未知签名</option>{signatureOptions.map((item) => <option key={item.id} value={item.id}>{optionLabel(item)}</option>)}</select></label>
              <label>模板<select data-testid="admin-complaint-case-complaints-template-id" value={draft.templateId} disabled={!options.isSuccess} onChange={(event) => setField('templateId', event.target.value)}><option value="">未知模板</option>{templateOptions.map((item) => <option key={item.id} value={item.id}>{optionLabel(item)}</option>)}</select></label>
              <label>消息 ID<input data-testid="admin-complaint-case-complaints-message-id" value={draft.messageId} onChange={(event) => setField('messageId', event.target.value)} /></label>
              <label>内容类型<select data-testid="admin-complaint-case-complaints-content-type" value={draft.contentType} onChange={(event) => setField('contentType', event.target.value)}><option value="">未知类型</option><option value="VERIFY">验证码</option><option value="NOTIFY">通知</option><option value="MARKETING">营销</option></select></label>
              <label>被投诉号码<input data-testid="admin-complaint-case-complaints-mobile" value={draft.complainedMobile} onChange={(event) => setField('complainedMobile', event.target.value)} /></label>
              <label>处置要求<input data-testid="admin-complaint-case-complaints-requirement" value={draft.requirement} onChange={(event) => setField('requirement', event.target.value)} /></label>
              <p className="complaint-action-readonly" data-testid="admin-complaint-case-complaints-attribution-policy">归因质量由服务端按实际关联判定</p>
              <div className="complaint-form-actions" data-testid="form-actions"><button type="submit" data-testid="form-submit" disabled={!intakeValid || createCase.isPending}>{createCase.isPending ? '登记中…' : '登记投诉'}</button></div>
            </div>
          </section>
        </form>
      )}

      <section className="card complaint-section-card" data-testid="admin-complaint-case-complaints-list-card">
        <h2>投诉列表</h2>
        <div className="complaint-table-wrap" data-testid="admin-complaint-case-complaints-table">
          <table className="alert-engine-table complaint-table" data-testid="data-table">
            <colgroup><col className="complaint-table-id" /><col className="complaint-table-source" /><col className="complaint-table-summary" /><col className="complaint-table-quality" /><col className="complaint-table-status" /><col className="complaint-table-resource" /><col className="complaint-table-requirement" /><col className="complaint-table-actions-column" /></colgroup>
            <thead><tr><th>案件</th><th>来源</th><th>摘要</th><th>归因质量</th><th>状态</th><th>处置资源</th><th>要求</th><th>动作</th></tr></thead>
            <tbody>
              {cases.isLoading ? <tr><td colSpan={8} className="complaint-table-state" data-testid="admin-complaint-case-complaints-loading">正在加载投诉案件…</td></tr>
                : cases.isError ? <tr><td colSpan={8} className="complaint-table-state" role="alert" data-testid="admin-complaint-case-complaints-load-error">投诉案件加载失败。 <button type="button" data-testid="admin-complaint-case-complaints-list-retry" onClick={() => void cases.refetch()}>重试</button></td></tr>
                  : (cases.data ?? []).length === 0 ? <tr><td colSpan={8} className="complaint-table-state" data-testid="table-empty">暂无投诉记录</td></tr>
                    : cases.data?.map((row) => (
                      <tr key={row.id} className={selectedId === row.id ? 'complaint-row-selected' : ''} data-testid="admin-complaint-case-complaints-row">
                        <td data-testid="admin-complaint-case-complaints-row-case-id">#{row.id}</td>
                        <td><span className="complaint-table-truncate" title={row.source}>{row.source}</span></td>
                        <td><span className="complaint-table-truncate" title={row.summary}>{row.summary}</span></td>
                        <td><span className="complaint-table-truncate" title={row.attributionQuality} data-testid="admin-complaint-case-complaints-attribution-quality">{row.attributionQuality}</span></td>
                        <td><span className="complaint-table-truncate" title={row.status}>{row.status}</span></td>
                        <td data-testid="admin-complaint-case-complaints-remediation-resource"><span className="complaint-table-truncate" title={targetSummary(row)}>{targetSummary(row)}</span></td>
                        <td><span className="complaint-table-truncate" title={row.requirement ?? 'UNKNOWN'}>{row.requirement ?? 'UNKNOWN'}</span></td>
                        <td><div className="complaint-table-actions"><button type="button" data-testid="admin-complaint-case-complaints-open-case" onClick={() => setSelectedId(row.id)}>查看详情</button></div></td>
                      </tr>
                    ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="card complaint-section-card complaint-case-workspace" data-testid="admin-complaint-case-complaints-case-workspace">
        <h2>单案工作区</h2>
        {selectedId == null ? <p className="complaint-workspace-state" data-testid="admin-complaint-case-complaints-case-workspace-empty">请选择一个投诉案件查看状态、动作和完整历史。</p>
          : caseDetail.isLoading ? <p className="complaint-workspace-state" data-testid="admin-complaint-case-complaints-case-workspace-loading">正在加载案件 #{selectedId}…</p>
            : caseDetail.isError ? <p className="complaint-workspace-state" role="alert" data-testid="admin-complaint-case-complaints-case-workspace-error">案件详情加载失败。 <button type="button" data-testid="admin-complaint-case-complaints-case-workspace-retry" onClick={() => void caseDetail.refetch()}>重试</button></p>
              : complaint && (
                <>
                  <div className="complaint-case-summary">
                    <div><span>案件</span><strong data-testid="admin-complaint-case-complaints-case-id">#{complaint.id}</strong></div>
                    <div><span>机构</span><strong data-testid="admin-complaint-case-complaints-case-tenant">{display(complaint.tenantId)}</strong></div>
                    <div><span>状态</span><strong data-testid="admin-complaint-case-complaints-case-status">{complaint.status}</strong></div>
                    <div className="complaint-case-summary-wide"><span>摘要</span><strong data-testid="admin-complaint-case-complaints-case-summary">{complaint.summary}</strong></div>
                  </div>
                  {canMutate && (
                    <div className="complaint-case-actions" data-testid="admin-complaint-case-complaints-state-action">
                      <button type="button" data-testid="admin-complaint-case-complaints-accept" disabled={!actionEligible('accept', complaint.status, failedRemediation?.id ?? null) || command.isPending} onClick={() => openAction('accept')}>接单</button>
                      <button type="button" data-testid="admin-complaint-case-complaints-handle" disabled={!actionEligible('handle', complaint.status, failedRemediation?.id ?? null) || command.isPending} onClick={() => openAction('handle')}>完成处理</button>
                      <button type="button" data-testid="admin-complaint-case-complaints-remediation" disabled={!actionEligible('remediate', complaint.status, failedRemediation?.id ?? null) || command.isPending} onClick={() => openAction('remediate')}>资源处置</button>
                      <button type="button" data-testid="admin-complaint-case-complaints-remediation-recovery" disabled={!actionEligible('recover', complaint.status, failedRemediation?.id ?? null) || command.isPending} title={failedRemediation ? `恢复处置记录 #${failedRemediation.id}` : '没有可恢复的失败处置'} onClick={() => openAction('recover')}>恢复失败处置</button>
                      <button type="button" data-testid="admin-complaint-case-complaints-close" disabled={!actionEligible('close', complaint.status, failedRemediation?.id ?? null) || command.isPending} onClick={() => openAction('close')}>关闭案件</button>
                    </div>
                  )}
                  <section className="complaint-timeline" data-testid="admin-complaint-case-complaints-timeline">
                    <h3>案件时间线</h3>
                    {(caseDetail.data?.timeline ?? []).length === 0 ? <p>暂无案件历史</p> : (
                      <ol>{caseDetail.data?.timeline.map((event) => (
                        <li key={event.id} data-testid="admin-complaint-case-complaints-timeline-event">
                          <div className="complaint-timeline-heading"><strong>{event.eventType}</strong><span>{event.occurredAt}</span></div>
                          <p>操作人：{display(event.actor)} · 状态：{display(event.fromStatus)} → {display(event.toStatus)} · 结果：{event.result}</p>
                          <p>{eventEvidence(event)}</p>
                        </li>
                      ))}</ol>
                    )}
                  </section>
                </>
              )}
      </section>

      {action && complaint && (
        <ActionDialog
          action={action}
          complaint={complaint}
          failedRecordId={failedRemediation?.id ?? null}
          draft={actionDraft}
          pending={command.isPending}
          error={actionError}
          stale={stale}
          onDraftChange={(field, value) => setActionDraft((current) => ({ ...current, [field]: value }))}
          onCancel={closeAction}
          onConfirm={() => void submitAction()}
        />
      )}
    </section>
  );
}
