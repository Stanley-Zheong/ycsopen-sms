import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { mutationErrorMessage } from '@/api/client';
import {
  acceptComplaintCase,
  closeComplaintCase,
  createComplaintCase,
  handleComplaintCase,
  listComplaintCases,
  recoverComplaintRemediation,
  remediateComplaintCase,
  type ComplaintCaseRow,
} from '@/api/complaintCaseApi';
import '@/styles/alert-engine.css';

const defaultDraft = {
  source: 'REGULATOR',
  summary: '监管投诉：营销短信扰民',
  tenantId: '7',
  channelId: '11',
  signatureId: '8',
  templateId: '9',
  messageId: 'MSG-1',
  contentType: 'MARKETING',
  complainedMobile: '13800138000',
  attributionQuality: 'COMPLETE',
  requirement: '24小时内反馈',
};

const defaultStateDraft = {
  opinion: '投诉属实',
  remediation: '暂停通道',
  requirement: '24小时内完成整改并反馈监管',
  closeNote: '复核关闭',
  actor: 'operator',
};

const defaultRemediationDraft = {
  disposalType: 'AUTO',
  actor: 'operator',
  authorizedReviewId: 'review-41-1',
  reason: '投诉集中',
};

const defaultRecoveryDraft = {
  authorizedReviewId: 'review-41-recovery',
  actor: 'operator',
  resumeCondition: '授权复核通过后恢复',
};

type DisposalChoice = 'AUTO' | 'BLACKLIST_MOBILE' | 'SUSPEND_TENANT' | 'SUSPEND_SIGNATURE_OR_TEMPLATE' | 'SUSPEND_CHANNEL';

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

function remediationTarget(row: ComplaintCaseRow): string {
  if (row.channelId) {
    return `channel:${row.channelId}`;
  }
  if (row.signatureId) {
    return `signature:${row.signatureId}`;
  }
  if (row.templateId) {
    return `template:${row.templateId}`;
  }
  if (row.tenantId) {
    return `tenant:${row.tenantId}`;
  }
  return row.complainedMobile ? `mobile:${row.complainedMobile}` : 'mobile:UNKNOWN';
}

function remediationTypeForTarget(target: string): Exclude<DisposalChoice, 'AUTO'> {
  if (target.startsWith('channel:')) {
    return 'SUSPEND_CHANNEL';
  }
  if (target.startsWith('signature:') || target.startsWith('template:')) {
    return 'SUSPEND_SIGNATURE_OR_TEMPLATE';
  }
  if (target.startsWith('tenant:')) {
    return 'SUSPEND_TENANT';
  }
  return 'BLACKLIST_MOBILE';
}

function targetForChoice(row: ComplaintCaseRow, choice: DisposalChoice): string {
  if (choice === 'BLACKLIST_MOBILE') {
    return row.complainedMobile ? `mobile:${row.complainedMobile}` : 'mobile:UNKNOWN';
  }
  if (choice === 'SUSPEND_TENANT') {
    return row.tenantId ? `tenant:${row.tenantId}` : 'tenant:UNKNOWN';
  }
  if (choice === 'SUSPEND_SIGNATURE_OR_TEMPLATE') {
    if (row.signatureId) {
      return `signature:${row.signatureId}`;
    }
    return row.templateId ? `template:${row.templateId}` : 'signature:UNKNOWN';
  }
  if (choice === 'SUSPEND_CHANNEL') {
    return row.channelId ? `channel:${row.channelId}` : 'channel:UNKNOWN';
  }
  return remediationTarget(row);
}

export default function AdminComplaintsPage() {
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState(defaultDraft);
  const [stateDraft, setStateDraft] = useState(defaultStateDraft);
  const [remediationDraft, setRemediationDraft] = useState(defaultRemediationDraft);
  const [recoveryDraft, setRecoveryDraft] = useState(defaultRecoveryDraft);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [disposalIdsByCase, setDisposalIdsByCase] = useState<Record<number, number>>({});
  const cases = useQuery({ queryKey: ['complaint-cases'], queryFn: listComplaintCases, retry: false });

  async function refresh() {
    await queryClient.invalidateQueries({ queryKey: ['complaint-cases'] });
  }

  function fail(failure: unknown, fallback: string) {
    setError(mutationErrorMessage(failure, fallback));
    setMessage('');
  }

  const createCase = useMutation({
    mutationFn: () => createComplaintCase({
      source: draft.source,
      summary: draft.summary,
      tenantId: numberOrNull(draft.tenantId),
      channelId: numberOrNull(draft.channelId),
      signatureId: numberOrNull(draft.signatureId),
      templateId: numberOrNull(draft.templateId),
      messageId: draft.messageId,
      contentType: draft.contentType,
      complainedMobile: draft.complainedMobile,
      attributionQuality: draft.attributionQuality,
      requirement: draft.requirement,
    }),
    onSuccess: () => refresh().then(() => setMessage('投诉案件已登记')),
    onError: (failure) => fail(failure, '投诉案件登记失败'),
  });

  function statePayload(status: string, opinion = stateDraft.opinion) {
    return {
      status,
      opinion,
      remediation: stateDraft.remediation,
      requirement: stateDraft.requirement,
      actor: stateDraft.actor,
    };
  }

  const acceptCase = useMutation({
    mutationFn: (row: ComplaintCaseRow) => acceptComplaintCase(row.id, statePayload('PROCESSING')),
    onSuccess: () => refresh().then(() => setMessage('投诉案件已接单')),
    onError: (failure) => fail(failure, '投诉案件接单失败'),
  });

  const handleCase = useMutation({
    mutationFn: (row: ComplaintCaseRow) => handleComplaintCase(row.id, statePayload('PROCESSED')),
    onSuccess: () => refresh().then(() => setMessage('投诉案件已处理')),
    onError: (failure) => fail(failure, '投诉案件处理失败'),
  });

  const closeCase = useMutation({
    mutationFn: (row: ComplaintCaseRow) => closeComplaintCase(row.id, statePayload('CLOSED', stateDraft.closeNote)),
    onSuccess: () => refresh().then(() => setMessage('投诉案件已关闭')),
    onError: (failure) => fail(failure, '投诉案件关闭失败'),
  });

  const remediateCase = useMutation({
    mutationFn: (row: ComplaintCaseRow) => {
      const targetRef = targetForChoice(row, remediationDraft.disposalType as DisposalChoice);
      return remediateComplaintCase(row.id, {
        ...remediationDraft,
        disposalType: remediationDraft.disposalType === 'AUTO'
          ? remediationTypeForTarget(targetRef)
          : remediationDraft.disposalType,
        targetRef,
      });
    },
    onSuccess: (row) => refresh().then(() => {
      setDisposalIdsByCase((current) => ({ ...current, [row.complaintId]: row.id }));
      setMessage('处置已记录');
    }),
    onError: (failure) => fail(failure, '处置记录失败'),
  });

  const recoverCase = useMutation({
    mutationFn: (row: ComplaintCaseRow) => {
      const disposalRecordId = disposalIdsByCase[row.id];
      if (disposalRecordId == null) {
        throw new Error('没有可恢复的处置记录');
      }
      return recoverComplaintRemediation(row.id, { ...recoveryDraft, disposalRecordId });
    },
    onSuccess: () => refresh().then(() => setMessage('恢复已记录')),
    onError: (failure) => fail(failure, '恢复记录失败'),
  });

  const setField = (field: keyof typeof defaultDraft, value: string) => setDraft((current) => ({ ...current, [field]: value }));
  const setStateField = (field: keyof typeof defaultStateDraft, value: string) => setStateDraft((current) => ({ ...current, [field]: value }));
  const setRemediationField = (field: keyof typeof defaultRemediationDraft, value: string) => setRemediationDraft((current) => ({ ...current, [field]: value }));
  const setRecoveryField = (field: keyof typeof defaultRecoveryDraft, value: string) => setRecoveryDraft((current) => ({ ...current, [field]: value }));

  return (
    <section className="alert-engine-page" data-testid="admin-complaint-case-complaints-page">
      <nav aria-label="面包屑">运营管理 / 投诉管理 / 投诉闭环</nav>
      <header className="alert-engine-header">
        <div>
          <h1>投诉闭环管理</h1>
          <p className="page-description">登记投诉来源、保留可用归因链接；缺失链路显示 UNKNOWN，不补造数据。</p>
        </div>
        <button type="button" onClick={() => void refresh()} data-testid="admin-complaint-case-complaints-refresh">刷新</button>
      </header>

      {message && <p role="status" className="alert-engine-message success" data-testid="admin-complaint-case-complaints-message">{message}</p>}
      {error && <p role="alert" className="alert-engine-message error" data-testid="admin-complaint-case-complaints-error">{error}</p>}

      <form
        className="complaint-intake-form"
        data-testid="entity-form"
        onSubmit={(event) => {
          event.preventDefault();
          createCase.mutate();
        }}
      >
        <section className="card complaint-section-card" data-testid="admin-complaint-case-complaints-intake-card">
          <h2>投诉登记</h2>
          <div className="complaint-field-grid">
            <label>来源<input data-testid="admin-complaint-case-complaints-source" value={draft.source} onChange={(event) => setField('source', event.target.value)} /></label>
            <label>摘要<input data-testid="admin-complaint-case-complaints-summary" value={draft.summary} onChange={(event) => setField('summary', event.target.value)} /></label>
            <label>机构 ID<input data-testid="admin-complaint-case-complaints-tenant-id" value={draft.tenantId} onChange={(event) => setField('tenantId', event.target.value)} /></label>
            <label>通道 ID<input data-testid="admin-complaint-case-complaints-channel-id" value={draft.channelId} onChange={(event) => setField('channelId', event.target.value)} /></label>
            <label>签名 ID<input data-testid="admin-complaint-case-complaints-signature-id" value={draft.signatureId} onChange={(event) => setField('signatureId', event.target.value)} /></label>
            <label>模板 ID<input data-testid="admin-complaint-case-complaints-template-id" value={draft.templateId} onChange={(event) => setField('templateId', event.target.value)} /></label>
          </div>
        </section>

        <section className="card complaint-section-card" data-testid="admin-complaint-case-complaints-attribution-card">
          <h2>归因与要求</h2>
          <div className="complaint-field-grid">
            <label>消息 ID<input data-testid="admin-complaint-case-complaints-message-id" value={draft.messageId} onChange={(event) => setField('messageId', event.target.value)} /></label>
            <label>内容类型<input data-testid="admin-complaint-case-complaints-content-type" value={draft.contentType} onChange={(event) => setField('contentType', event.target.value)} /></label>
            <label>被投诉号码<input data-testid="admin-complaint-case-complaints-mobile" value={draft.complainedMobile} onChange={(event) => setField('complainedMobile', event.target.value)} /></label>
            <label>归因质量<select data-testid="admin-complaint-case-complaints-attribution-draft" value={draft.attributionQuality} onChange={(event) => setField('attributionQuality', event.target.value)}>
              <option value="COMPLETE">COMPLETE</option>
              <option value="UNKNOWN">UNKNOWN</option>
            </select></label>
            <label>处置要求<input data-testid="admin-complaint-case-complaints-requirement" value={draft.requirement} onChange={(event) => setField('requirement', event.target.value)} /></label>
            <div className="complaint-form-actions" data-testid="form-actions">
              <button type="submit" data-testid="form-submit">登记投诉</button>
            </div>
          </div>
        </section>
      </form>

      <section className="card complaint-section-card" data-testid="admin-complaint-case-complaints-evidence-card">
        <h2>处理证据</h2>
        <div className="complaint-field-grid">
          <label>处理意见<input data-testid="admin-complaint-case-complaints-opinion" value={stateDraft.opinion} onChange={(event) => setStateField('opinion', event.target.value)} /></label>
          <label>处置动作<input data-testid="admin-complaint-case-complaints-remediation-note" value={stateDraft.remediation} onChange={(event) => setStateField('remediation', event.target.value)} /></label>
          <label>整改要求<input data-testid="admin-complaint-case-complaints-state-requirement" value={stateDraft.requirement} onChange={(event) => setStateField('requirement', event.target.value)} /></label>
          <label>关闭说明<input data-testid="admin-complaint-case-complaints-close-note" value={stateDraft.closeNote} onChange={(event) => setStateField('closeNote', event.target.value)} /></label>
          <label>处置方式<select data-testid="admin-complaint-case-complaints-disposal-type" value={remediationDraft.disposalType} onChange={(event) => setRemediationField('disposalType', event.target.value)}>
            <option value="AUTO">自动匹配可用归因</option>
            <option value="SUSPEND_CHANNEL">暂停通道</option>
            <option value="SUSPEND_SIGNATURE_OR_TEMPLATE">停用签名/模板</option>
            <option value="SUSPEND_TENANT">冻结机构</option>
            <option value="BLACKLIST_MOBILE">号码黑名单</option>
          </select></label>
          <label>授权复核 ID<input data-testid="admin-complaint-case-complaints-review-id" value={remediationDraft.authorizedReviewId} onChange={(event) => setRemediationField('authorizedReviewId', event.target.value)} /></label>
          <label>处置原因<input data-testid="admin-complaint-case-complaints-remediation-reason" value={remediationDraft.reason} onChange={(event) => setRemediationField('reason', event.target.value)} /></label>
          <label>恢复复核 ID<input data-testid="admin-complaint-case-complaints-recovery-review-id" value={recoveryDraft.authorizedReviewId} onChange={(event) => setRecoveryField('authorizedReviewId', event.target.value)} /></label>
          <label>恢复条件<input data-testid="admin-complaint-case-complaints-recovery-condition" value={recoveryDraft.resumeCondition} onChange={(event) => setRecoveryField('resumeCondition', event.target.value)} /></label>
        </div>
      </section>

      <section className="card complaint-section-card" data-testid="admin-complaint-case-complaints-list-card">
        <h2>投诉列表</h2>
        <div className="complaint-table-wrap" data-testid="admin-complaint-case-complaints-table">
          <table className="alert-engine-table complaint-table" data-testid="data-table">
            <colgroup>
              <col className="complaint-table-source" />
              <col className="complaint-table-summary" />
              <col className="complaint-table-quality" />
              <col className="complaint-table-status" />
              <col className="complaint-table-resource" />
              <col className="complaint-table-requirement" />
              <col className="complaint-table-actions-column" />
            </colgroup>
            <thead>
              <tr><th>来源</th><th>摘要</th><th>归因质量</th><th>状态</th><th>处置资源</th><th>要求</th><th>动作</th></tr>
            </thead>
            <tbody>
              {cases.isLoading ? (
                <tr>
                  <td colSpan={7} className="complaint-table-state" data-testid="admin-complaint-case-complaints-loading">正在加载投诉案件…</td>
                </tr>
              ) : cases.isError ? (
                <tr>
                  <td colSpan={7} className="complaint-table-state" role="alert" data-testid="admin-complaint-case-complaints-load-error">投诉案件加载失败。</td>
                </tr>
              ) : (cases.data ?? []).length === 0 ? (
                <tr>
                  <td colSpan={7} className="complaint-table-state" data-testid="table-empty">暂无投诉记录</td>
                </tr>
              ) : (cases.data ?? []).map((row) => {
                const resourceSummary = targetSummary(row);
                const requirement = row.requirement ?? 'UNKNOWN';
                return (
                  <tr key={row.id} data-testid="admin-complaint-case-complaints-row">
                    <td><span className="complaint-table-truncate" title={row.source}>{row.source}</span></td>
                    <td><span className="complaint-table-truncate" title={row.summary}>{row.summary}</span></td>
                    <td data-testid="admin-complaint-case-complaints-attribution-quality">
                      <span className="complaint-table-truncate" title={row.attributionQuality}>{row.attributionQuality}</span>
                    </td>
                    <td data-testid="admin-complaint-case-complaints-state-action">
                      <span className="complaint-table-truncate" title={row.status}>{row.status}</span>
                    </td>
                    <td data-testid="admin-complaint-case-complaints-remediation-resource">
                      <span className="complaint-table-truncate" title={resourceSummary}>{resourceSummary}</span>
                    </td>
                    <td><span className="complaint-table-truncate" title={requirement}>{requirement}</span></td>
                    <td>
                      <div className="complaint-table-actions">
                        <button type="button" data-testid="admin-complaint-case-complaints-accept" onClick={() => acceptCase.mutate(row)}>接单</button>
                        <button type="button" data-testid="admin-complaint-case-complaints-handle" onClick={() => handleCase.mutate(row)}>处理</button>
                        <button type="button" data-testid="admin-complaint-case-complaints-remediation" onClick={() => remediateCase.mutate(row)}>资源处置</button>
                        <button type="button" data-testid="admin-complaint-case-complaints-remediation-recovery" disabled={disposalIdsByCase[row.id] == null} onClick={() => recoverCase.mutate(row)}>恢复</button>
                        <button type="button" data-testid="admin-complaint-case-complaints-close" onClick={() => closeCase.mutate(row)}>关闭</button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </section>
    </section>
  );
}
