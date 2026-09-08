import { useEffect, useMemo, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import ModalDialog from '@/components/common/ModalDialog';
import { useIdentityAccess } from '@/pages/admin/identity/useIdentityAccess';
import { protectedQueryKey } from '@/store/authStore';
import {
  classifyQualificationFailure,
  decideTenant,
  getAdminTenant,
  getTenantEvidence,
  getTenantEvents,
  inspectTenant,
  listAdminTenants,
  TENANT_PERMISSIONS,
  updateTenantOperatingStatus,
  updateTenantProfile,
  type AdminTenantReview,
  type OperatingStatus,
  type QualificationEvent,
  type VerificationStatus,
} from '@/api/tenantQualificationApi';
import '@/styles/tenant-qualification.css';

const PAGE_SIZE = 10;
const VERIFICATION_LABELS: Record<VerificationStatus, string> = {
  UNVERIFIED: '未认证', PENDING: '待审核', VERIFIED: '已认证', REJECTED: '已驳回', SUPPLEMENT_REQUIRED: '需补充材料',
};
const OPERATING_LABELS: Record<OperatingStatus, string> = {
  NORMAL: '正常', DISABLED: '已禁用', ARREARS_FROZEN: '欠费冻结',
};

function displayTime(value: string | null): string {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' }) : '—';
}

function failureCopy(error: unknown, fallback: string): string {
  const result = classifyQualificationFailure(error);
  if (result.kind === 'stale') return '数据已被其他操作员更新。当前输入已保留，请刷新数据后重试。';
  if (result.kind === 'provider') return '外部检查服务暂不可用，申请保持原状态，请稍后重试。';
  if (result.kind === 'denied') return '无权执行此操作。';
  return `${fallback}${result.traceId ? `（追踪编号：${result.traceId}）` : ''}`;
}

function reviewFactsFingerprint(tenant: AdminTenantReview): string {
  return JSON.stringify({
    fullName: tenant.fullName,
    unifiedSocialCreditCode: tenant.unifiedSocialCreditCode,
    legalRepresentativeName: tenant.legalRepresentativeName,
    registeredCapital: tenant.registeredCapital,
    businessScope: tenant.businessScope,
    registeredAddress: tenant.registeredAddress,
    businessAddress: tenant.businessAddress,
    licenseValidUntil: tenant.licenseValidUntil,
    trademarkUse: tenant.trademarkUse,
    inspectionStatus: tenant.inspectionStatus,
    inspectedCompanyName: tenant.inspectedCompanyName,
    inspectedCreditCode: tenant.inspectedCreditCode,
    inspectionCompletedAt: tenant.inspectionCompletedAt,
  });
}

export default function TenantListPage() {
  const access = useIdentityAccess();
  const canRead = access.can(TENANT_PERMISSIONS.read);
  const canReview = access.can(TENANT_PERMISSIONS.review);
  const canEdit = access.can(TENANT_PERMISSIONS.update);
  const canStatus = access.can(TENANT_PERMISSIONS.statusUpdate);
  const canEvidence = access.can(TENANT_PERMISSIONS.evidenceRead);
  const queryClient = useQueryClient();
  const tenants = useQuery({ queryKey: protectedQueryKey('admin-tenants'), queryFn: listAdminTenants, enabled: canRead, retry: false });
  const [keyword, setKeyword] = useState('');
  const [verification, setVerification] = useState<VerificationStatus | ''>('');
  const [operating, setOperating] = useState<OperatingStatus | ''>('');
  const [applied, setApplied] = useState({ keyword: '', verification: '' as VerificationStatus | '', operating: '' as OperatingStatus | '' });
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<AdminTenantReview | null>(null);
  const [reviewLoading, setReviewLoading] = useState(false);
  const [reviewError, setReviewError] = useState('');
  const [humanConfirmed, setHumanConfirmed] = useState(false);
  const [events, setEvents] = useState<QualificationEvent[]>([]);
  const [decision, setDecision] = useState<'APPROVE' | 'REJECT' | 'SUPPLEMENT_REQUIRED' | null>(null);
  const [decisionReason, setDecisionReason] = useState('');
  const [decisionError, setDecisionError] = useState('');
  const [decisionPending, setDecisionPending] = useState(false);
  const [editing, setEditing] = useState<AdminTenantReview | null>(null);
  const [editValues, setEditValues] = useState({ customerLevel: '1', bizManager: '', industry: '', reason: '' });
  const [editError, setEditError] = useState('');
  const [statusTenant, setStatusTenant] = useState<AdminTenantReview | null>(null);
  const [statusTarget, setStatusTarget] = useState<OperatingStatus>('NORMAL');
  const [statusReason, setStatusReason] = useState('');
  const [statusError, setStatusError] = useState('');
  const [mutationPending, setMutationPending] = useState(false);
  const [liveStatus, setLiveStatus] = useState('');
  const [evidenceUrl, setEvidenceUrl] = useState<string | null>(null);
  const [evidenceError, setEvidenceError] = useState('');
  const [evidenceOpen, setEvidenceOpen] = useState(false);
  const [evidenceLoading, setEvidenceLoading] = useState(false);
  const [evidenceKind, setEvidenceKind] = useState<'BUSINESS_LICENSE' | 'REPRESENTATIVE_ID_FRONT' | 'REPRESENTATIVE_ID_BACK'>('BUSINESS_LICENSE');

  useEffect(() => () => {
    if (evidenceUrl && typeof URL.revokeObjectURL === 'function') URL.revokeObjectURL(evidenceUrl);
  }, [evidenceUrl]);

  const filtered = useMemo(() => (tenants.data ?? []).filter((tenant) => {
    const query = applied.keyword.trim().toLocaleLowerCase('zh-CN');
    return (!query || [tenant.tenantNo, tenant.shortName, tenant.fullName]
      .some((value) => value.toLocaleLowerCase('zh-CN').includes(query)))
      && (!applied.verification || tenant.verificationStatus === applied.verification)
      && (!applied.operating || tenant.operatingStatus === applied.operating);
  }), [applied, tenants.data]);
  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const rows = filtered.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
  const tenantFailureKind = tenants.error ? classifyQualificationFailure(tenants.error).kind : null;

  useEffect(() => {
    if (page >= pageCount) setPage(pageCount - 1);
  }, [page, pageCount]);

  async function openReview(tenantId: number) {
    setReviewLoading(true); setReviewError(''); setHumanConfirmed(false);
    try {
      const [detail, history] = await Promise.all([getAdminTenant(tenantId), getTenantEvents(tenantId)]);
      setSelected(detail); setEvents(history ?? []);
    } catch (failure) { setReviewError(failureCopy(failure, '审核资料加载失败，请重试。')); }
    finally { setReviewLoading(false); }
  }

  async function inspect() {
    if (!selected) return;
    setReviewError(''); setMutationPending(true);
    try { setSelected(await inspectTenant(selected.tenantId, selected.qualificationRevision)); }
    catch (failure) { setReviewError(failureCopy(failure, 'OCR 辅助检查失败，请重试。')); }
    finally { setMutationPending(false); }
  }

  async function showEvidence(kind: 'BUSINESS_LICENSE' | 'REPRESENTATIVE_ID_FRONT' | 'REPRESENTATIVE_ID_BACK') {
    if (!selected || evidenceLoading) return;
    setEvidenceOpen(true);
    setEvidenceLoading(true);
    setEvidenceError('');
    setEvidenceUrl(null);
    try {
      setEvidenceKind(kind);
      const blob = await getTenantEvidence(selected.tenantId, kind);
      setEvidenceUrl(typeof URL.createObjectURL === 'function' ? URL.createObjectURL(blob) : 'about:blank');
    } catch (failure) {
      setEvidenceError(failureCopy(failure, '证明材料加载失败。'));
    } finally { setEvidenceLoading(false); }
  }

  function closeEvidence() {
    if (evidenceUrl && typeof URL.revokeObjectURL === 'function') URL.revokeObjectURL(evidenceUrl);
    setEvidenceUrl(null);
    setEvidenceError('');
    setEvidenceOpen(false);
  }

  async function submitDecision() {
    if (!selected || !decision || !decisionReason.trim()) return;
    setDecisionPending(true); setDecisionError('');
    try {
      const updated = await decideTenant(selected.tenantId, {
        expectedRevision: selected.qualificationRevision, decision, reason: decisionReason.trim(),
        humanConfirmed: decision === 'APPROVE' && humanConfirmed,
      });
      setSelected(updated); setDecision(null); setDecisionReason('');
      setLiveStatus(`机构 ${updated.tenantNo} 的审核决定已保存。`);
      await queryClient.invalidateQueries({ queryKey: protectedQueryKey('admin-tenants') });
    } catch (failure) {
      const kind = classifyQualificationFailure(failure).kind;
      if (kind === 'stale') {
        try {
          const latest = await getAdminTenant(selected.tenantId);
          const factsChanged = reviewFactsFingerprint(latest) !== reviewFactsFingerprint(selected);
          setSelected(latest);
          if (factsChanged) setHumanConfirmed(false);
          setDecisionError(factsChanged
            ? '数据已被其他操作员更新，且审核事实已变化。当前原因已保留；请取消决定并重新核对后确认。'
            : '数据已被其他操作员更新。已加载最新版本，当前原因已保留，请重试。');
        } catch (refreshFailure) {
          setDecisionError(failureCopy(refreshFailure, '数据已更新，但最新审核资料加载失败，请稍后重试。'));
        }
      } else setDecisionError(failureCopy(failure, '审核决定保存失败，请重试。'));
    }
    finally { setDecisionPending(false); }
  }

  function openEdit(tenant: AdminTenantReview) {
    setEditing(tenant); setEditError('');
    setEditValues({ customerLevel: String(tenant.customerLevel ?? 1), bizManager: tenant.bizManager ?? '', industry: tenant.industry ?? '', reason: '' });
  }

  async function saveEdit() {
    if (!editing || !editValues.reason.trim()) return;
    setMutationPending(true); setEditError('');
    try {
      await updateTenantProfile(editing.tenantId, {
        expectedRevision: editing.qualificationRevision, shortName: editing.shortName,
        contactName: editing.contactName, businessAddress: editing.businessAddress,
        customerLevel: Number(editValues.customerLevel), bizManager: editValues.bizManager.trim(),
        industry: editValues.industry.trim(), reason: editValues.reason.trim(),
      });
      setEditing(null); setLiveStatus(`机构 ${editing.tenantNo} 的非认证资料已更新。`);
      await queryClient.invalidateQueries({ queryKey: protectedQueryKey('admin-tenants') });
    } catch (failure) {
      const kind = classifyQualificationFailure(failure).kind;
      if (kind === 'stale') {
        try {
          setEditing(await getAdminTenant(editing.tenantId));
          setEditError('数据已被其他操作员更新。已加载最新版本，当前输入已保留，请重试。');
        } catch (refreshFailure) {
          setEditError(failureCopy(refreshFailure, '数据已更新，但最新机构资料加载失败，请稍后重试。'));
        }
      } else setEditError(failureCopy(failure, '机构资料保存失败，请重试。'));
    }
    finally { setMutationPending(false); }
  }

  function openStatus(tenant: AdminTenantReview) {
    setStatusTenant(tenant); setStatusTarget(tenant.operatingStatus === 'NORMAL' ? 'DISABLED' : 'NORMAL');
    setStatusReason(''); setStatusError('');
  }

  async function saveStatus() {
    if (!statusTenant || statusTenant.accountRevision === null || !statusReason.trim() || statusTarget === statusTenant.operatingStatus) return;
    setMutationPending(true); setStatusError('');
    try {
      await updateTenantOperatingStatus(statusTenant.tenantId, {
        expectedRevision: statusTenant.accountRevision, target: statusTarget, reason: statusReason.trim(),
      });
      setStatusTenant(null); setLiveStatus(`机构 ${statusTenant.tenantNo} 的账户状态已更新。`);
      await queryClient.invalidateQueries({ queryKey: protectedQueryKey('admin-tenants') });
    } catch (failure) {
      const kind = classifyQualificationFailure(failure).kind;
      if (kind === 'stale') {
        try {
          setStatusTenant(await getAdminTenant(statusTenant.tenantId));
          setStatusError('数据已被其他操作员更新。已加载最新版本，当前选择和原因已保留，请重试。');
        } catch (refreshFailure) {
          setStatusError(failureCopy(refreshFailure, '数据已更新，但最新账户状态加载失败，请稍后重试。'));
        }
      } else setStatusError(failureCopy(failure, '账户状态更新失败，请重试。'));
    }
    finally { setMutationPending(false); }
  }

  if (access.isLoading) return <p data-testid="admin-tenant-qualification-tenants-loading">正在检查机构管理权限…</p>;
  if (access.isError || !canRead || tenantFailureKind === 'denied') return <p data-testid="admin-tenant-qualification-tenants-access-denied" className="qualification-alert error" role="alert">无权查看机构管理数据。</p>;

  return (
    <section data-testid="admin-tenant-qualification-tenants-page">
      <header className="qualification-page-header"><div><h1 data-testid="admin-tenant-qualification-tenants-heading">机构管理</h1><p className="page-description">审核机构资质并维护业务信息和账户运行状态。</p></div></header>
      <section className="card qualification-filter-grid" aria-label="机构筛选">
        <label htmlFor="tenant-keyword">关键字<input id="tenant-keyword" data-testid="admin-tenant-qualification-tenants-keyword" value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="机构编号、简称或全称" /></label>
        <label htmlFor="tenant-verification">认证状态<select id="tenant-verification" data-testid="admin-tenant-qualification-tenants-verification-status" value={verification} onChange={(event) => setVerification(event.target.value as VerificationStatus | '')}><option value="">全部</option>{Object.entries(VERIFICATION_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
        <label htmlFor="tenant-operating">运行状态<select id="tenant-operating" data-testid="admin-tenant-qualification-tenants-operating-status" value={operating} onChange={(event) => setOperating(event.target.value as OperatingStatus | '')}><option value="">全部</option>{Object.entries(OPERATING_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
        <div className="qualification-filter-actions">
          <button data-testid="admin-tenant-qualification-tenants-query" type="button" onClick={() => { setApplied({ keyword, verification, operating }); setPage(0); void tenants.refetch(); }}>查询</button>
          <button data-testid="admin-tenant-qualification-tenants-reset" className="button-secondary" type="button" onClick={() => { setKeyword(''); setVerification(''); setOperating(''); setApplied({ keyword: '', verification: '', operating: '' }); setPage(0); void tenants.refetch(); }}>重置</button>
        </div>
      </section>
      {tenants.isLoading && <p data-testid="admin-tenant-qualification-tenants-loading">正在加载机构列表…</p>}
      {tenants.isError && <div data-testid="admin-tenant-qualification-tenants-error" className="qualification-alert error" role="alert">机构列表加载失败。<button data-testid="admin-tenant-qualification-tenants-retry" type="button" onClick={() => void tenants.refetch()}>重新加载</button></div>}
      {!tenants.isLoading && !tenants.isError && rows.length === 0 && <p data-testid="admin-tenant-qualification-tenants-empty" className="card">没有符合条件的机构。</p>}
      {!tenants.isLoading && !tenants.isError && rows.length > 0 && (
        <div className="card qualification-table-wrap">
          <table data-testid="admin-tenant-qualification-tenants-table" className="ratio-table">
            <thead><tr><th>机构编号</th><th>简称</th><th>全称</th><th>认证状态</th><th>生命周期</th><th>运行状态</th><th>提交时间</th><th>客户经理</th><th>操作</th></tr></thead>
            <tbody>{rows.map((tenant) => (
              <tr key={tenant.tenantId} data-testid="admin-tenant-qualification-tenants-row" data-tenant-id={tenant.tenantId}>
                <td>{tenant.tenantNo}</td><td>{tenant.shortName}</td><td>{tenant.fullName}</td>
                <td><span className={`qualification-status status-${tenant.verificationStatus.toLowerCase()}`}>{VERIFICATION_LABELS[tenant.verificationStatus]}</span></td>
                <td>{tenant.lifecycleStatus}</td><td>{tenant.operatingStatus ? OPERATING_LABELS[tenant.operatingStatus] : '尚未开户'}</td>
                <td>{displayTime(tenant.submittedAt)}</td><td>{tenant.bizManager ?? '—'}</td>
                <td><div className="qualification-row-actions">
                  {canReview && <button data-testid="admin-tenant-qualification-tenants-review-open" type="button" onClick={() => void openReview(tenant.tenantId)}>审核</button>}
                  {canEdit && <button data-testid="admin-tenant-qualification-tenants-edit" className="button-secondary" type="button" onClick={() => openEdit(tenant)}>编辑</button>}
                  {canStatus && tenant.accountRevision !== null && <button data-testid="admin-tenant-qualification-tenants-status-action" className="button-secondary" type="button" onClick={() => openStatus(tenant)}>账户状态</button>}
                </div></td>
              </tr>
            ))}</tbody>
          </table>
          <div className="pagination-row"><button data-testid="admin-tenant-qualification-tenants-previous" className="button-secondary" type="button" disabled={page === 0} onClick={() => { setPage((current) => current - 1); void tenants.refetch(); }}>上一页</button><span data-testid="admin-tenant-qualification-tenants-page-status">第 {page + 1} / {pageCount} 页，共 {filtered.length} 条</span><button data-testid="admin-tenant-qualification-tenants-next" className="button-secondary" type="button" disabled={page + 1 >= pageCount} onClick={() => { setPage((current) => current + 1); void tenants.refetch(); }}>下一页</button></div>
        </div>
      )}
      {reviewLoading && <p className="qualification-alert">正在加载审核资料…</p>}
      {reviewError && <p className="qualification-alert error" role="alert">{reviewError}</p>}
      <p role="status" aria-live="polite" className="qualification-success">{liveStatus}</p>

      {selected && <ReviewDrawer tenant={selected} events={events} canEvidence={canEvidence} evidenceLoading={evidenceLoading} humanConfirmed={humanConfirmed} pending={mutationPending} error={reviewError} onHumanConfirmed={setHumanConfirmed} onClose={() => { setSelected(null); setReviewError(''); }} onInspect={() => void inspect()} onEvidence={(kind) => void showEvidence(kind)} onDecision={setDecision} />}
      {evidenceOpen && <ModalDialog labelledBy="tenant-evidence-title" onRequestClose={closeEvidence}><div data-testid="admin-tenant-qualification-tenants-review-evidence-dialog"><h2 id="tenant-evidence-title">{evidenceKind === 'BUSINESS_LICENSE' ? '营业执照证明材料' : evidenceKind === 'REPRESENTATIVE_ID_FRONT' ? '法人身份证正面证明材料' : '法人身份证反面证明材料'}</h2>{evidenceLoading ? <p role="status">正在安全加载证明材料…</p> : evidenceError ? <p role="alert">{evidenceError}</p> : evidenceUrl ? <iframe title={evidenceKind === 'BUSINESS_LICENSE' ? '营业执照证明材料' : evidenceKind === 'REPRESENTATIVE_ID_FRONT' ? '法人身份证正面证明材料' : '法人身份证反面证明材料'} src={evidenceUrl} className="qualification-evidence-frame" /> : null}<button data-testid="admin-tenant-qualification-tenants-review-evidence-close" type="button" onClick={closeEvidence}>关闭</button></div></ModalDialog>}
      {decision && selected && (
        <ModalDialog labelledBy="tenant-decision-title" onRequestClose={() => setDecision(null)}><div data-testid="admin-tenant-qualification-tenants-review-decision"><h2 id="tenant-decision-title">确认审核决定</h2><p data-testid="admin-tenant-qualification-tenants-review-decision-consequence">{decision === 'APPROVE' ? '通过后将启用初始管理员并创建唯一试用账户。' : decision === 'REJECT' ? '机构需按本次审核原因修正资料后重新提交。' : '机构将收到补充材料要求，重新提交前不能获得发送资格。'}</p><label htmlFor="tenant-decision-reason">审核原因<textarea id="tenant-decision-reason" data-testid="admin-tenant-qualification-tenants-review-decision-reason" rows={4} maxLength={500} value={decisionReason} onChange={(event) => setDecisionReason(event.target.value)} /></label>{decisionError && <p className="qualification-alert error" role="alert">{decisionError}</p>}<div className="qualification-dialog-actions"><button data-testid="admin-tenant-qualification-tenants-review-decision-cancel" className="button-secondary" type="button" onClick={() => setDecision(null)}>取消</button><button data-testid="admin-tenant-qualification-tenants-review-decision-confirm" type="button" disabled={!decisionReason.trim() || decisionPending || (decision === 'APPROVE' && !humanConfirmed)} onClick={() => void submitDecision()}>{decisionPending ? '保存中…' : '确认决定'}</button></div></div></ModalDialog>
      )}
      {editing && (
        <ModalDialog labelledBy="tenant-edit-title" onRequestClose={() => setEditing(null)}><div data-testid="admin-tenant-qualification-tenants-edit-drawer" className="qualification-drawer"><h2 id="tenant-edit-title">编辑机构资料</h2><p>只维护非认证元数据；认证绑定资料必须由机构重新认证。</p><label htmlFor="tenant-customer-level">客户等级<select id="tenant-customer-level" data-testid="admin-tenant-qualification-tenants-edit-customer-grade" value={editValues.customerLevel} onChange={(event) => setEditValues((current) => ({ ...current, customerLevel: event.target.value }))}>{[1, 2, 3, 4, 5].map((level) => <option key={level} value={level}>{level} 级</option>)}</select></label><label htmlFor="tenant-biz-manager">客户经理<input id="tenant-biz-manager" data-testid="admin-tenant-qualification-tenants-edit-business-manager" maxLength={64} value={editValues.bizManager} onChange={(event) => setEditValues((current) => ({ ...current, bizManager: event.target.value }))} /></label><label htmlFor="tenant-industry">所属行业<input id="tenant-industry" data-testid="admin-tenant-qualification-tenants-edit-industry" maxLength={64} value={editValues.industry} onChange={(event) => setEditValues((current) => ({ ...current, industry: event.target.value }))} /></label><label htmlFor="tenant-edit-reason">变更原因<textarea id="tenant-edit-reason" data-testid="admin-tenant-qualification-tenants-edit-reason" rows={3} maxLength={500} value={editValues.reason} onChange={(event) => setEditValues((current) => ({ ...current, reason: event.target.value }))} /></label>{editError && <p className="qualification-alert error" role="alert">{editError}</p>}<div className="qualification-dialog-actions"><button data-testid="admin-tenant-qualification-tenants-edit-cancel" className="button-secondary" type="button" onClick={() => setEditing(null)}>取消</button><button data-testid="admin-tenant-qualification-tenants-edit-save" type="button" disabled={!editValues.reason.trim() || mutationPending} onClick={() => void saveEdit()}>保存变更</button></div></div></ModalDialog>
      )}
      {statusTenant && (
        <ModalDialog labelledBy="tenant-status-title" onRequestClose={() => setStatusTenant(null)}><div data-testid="admin-tenant-qualification-tenants-status-dialog"><h2 id="tenant-status-title">变更账户运行状态</h2><label htmlFor="tenant-status-target">目标状态<select id="tenant-status-target" data-testid="admin-tenant-qualification-tenants-status-target" value={statusTarget} onChange={(event) => setStatusTarget(event.target.value as OperatingStatus)}>{Object.entries(OPERATING_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label><label htmlFor="tenant-status-reason">变更原因<textarea id="tenant-status-reason" data-testid="admin-tenant-qualification-tenants-status-reason" rows={3} maxLength={500} value={statusReason} onChange={(event) => setStatusReason(event.target.value)} /></label><p data-testid="admin-tenant-qualification-tenants-status-consequence">禁用或欠费冻结会拒绝新的发送工作，但保留历史资质和审计记录。</p>{statusError && <p className="qualification-alert error" role="alert">{statusError}</p>}<div className="qualification-dialog-actions"><button data-testid="admin-tenant-qualification-tenants-status-cancel" className="button-secondary" type="button" onClick={() => setStatusTenant(null)}>取消</button><button data-testid="admin-tenant-qualification-tenants-status-confirm" type="button" disabled={!statusReason.trim() || statusTarget === statusTenant.operatingStatus || mutationPending} onClick={() => void saveStatus()}>确认变更</button></div></div></ModalDialog>
      )}
    </section>
  );
}

function ReviewDrawer({ tenant, events, canEvidence, evidenceLoading, humanConfirmed, pending, error, onHumanConfirmed, onClose, onInspect, onEvidence, onDecision }: {
  tenant: AdminTenantReview; events: QualificationEvent[]; canEvidence: boolean; evidenceLoading: boolean; humanConfirmed: boolean;
  pending: boolean; error: string; onHumanConfirmed: (checked: boolean) => void; onClose: () => void;
  onInspect: () => void; onEvidence: (kind: 'BUSINESS_LICENSE' | 'REPRESENTATIVE_ID_FRONT' | 'REPRESENTATIVE_ID_BACK') => void;
  onDecision: (decision: 'APPROVE' | 'REJECT' | 'SUPPLEMENT_REQUIRED') => void;
}) {
  const inspectionComplete = tenant.inspectionStatus === 'COMPLETED';
  return (
    <ModalDialog labelledBy="tenant-review-title" onRequestClose={onClose}>
      <div data-testid="admin-tenant-qualification-tenants-review-drawer" className="qualification-drawer">
        <div className="qualification-page-header"><h2 id="tenant-review-title">审核 {tenant.shortName}</h2><button data-testid="admin-tenant-qualification-tenants-review-drawer-close" className="button-secondary" type="button" onClick={onClose} aria-label="关闭审核抽屉">关闭</button></div>
        <dl className="qualification-review-facts"><div><dt>企业全称</dt><dd>{tenant.fullName}</dd></div><div><dt>统一社会信用代码</dt><dd>{tenant.unifiedSocialCreditCode}</dd></div><div><dt>法定代表人</dt><dd>{tenant.legalRepresentativeName}</dd></div><div><dt>业务联系人</dt><dd>{tenant.contactName}</dd></div><div><dt>注册资本</dt><dd>{tenant.registeredCapital}</dd></div><div><dt>经营范围</dt><dd>{tenant.businessScope}</dd></div><div><dt>注册地址</dt><dd>{tenant.registeredAddress}</dd></div><div><dt>经营地址</dt><dd>{tenant.businessAddress}</dd></div><div><dt>营业执照有效期</dt><dd>{tenant.licenseValidUntil}</dd></div><div><dt>商标签名意向</dt><dd>{tenant.trademarkUse ? '是' : '否'}</dd></div><div><dt>提交时间</dt><dd>{displayTime(tenant.submittedAt)}</dd></div></dl>
        {canEvidence && <div className="qualification-evidence-actions"><button data-testid="admin-tenant-qualification-tenants-review-evidence-open" type="button" className="button-secondary" disabled={evidenceLoading} onClick={() => onEvidence('BUSINESS_LICENSE')}>{evidenceLoading ? '正在加载证明材料…' : '查看营业执照证明'}</button><button data-testid="admin-tenant-qualification-tenants-review-evidence-legal-id-front-open" type="button" className="button-secondary" disabled={evidenceLoading} onClick={() => onEvidence('REPRESENTATIVE_ID_FRONT')}>查看法人身份证正面</button><button data-testid="admin-tenant-qualification-tenants-review-evidence-legal-id-back-open" type="button" className="button-secondary" disabled={evidenceLoading} onClick={() => onEvidence('REPRESENTATIVE_ID_BACK')}>查看法人身份证反面</button></div>}
        <section className="qualification-inspection"><h3>OCR 辅助核对</h3><dl className="qualification-review-facts"><div><dt>提交企业全称</dt><dd>{tenant.fullName}</dd></div><div><dt>提交统一社会信用代码</dt><dd>{tenant.unifiedSocialCreditCode}</dd></div><div><dt>识别企业全称</dt><dd>{tenant.inspectedCompanyName ?? '尚未识别'}</dd></div><div><dt>识别统一社会信用代码</dt><dd>{tenant.inspectedCreditCode ?? '尚未识别'}</dd></div><div><dt>检查状态</dt><dd>{tenant.inspectionStatus}</dd></div><div><dt>检查完成时间</dt><dd>{displayTime(tenant.inspectionCompletedAt)}</dd></div></dl><button data-testid="admin-tenant-qualification-tenants-review-ocr-status" type="button" className="button-secondary" disabled={pending || inspectionComplete} onClick={onInspect}>{inspectionComplete ? `已完成：${tenant.inspectedCompanyName ?? '未提取企业名'} / ${tenant.inspectionConfidence === null ? '无置信度' : `${Math.round(tenant.inspectionConfidence * 100)}%`}` : tenant.inspectionStatus === 'FAILED' ? '检查失败，点击重试' : tenant.inspectionStatus === 'PENDING' ? '检查中…' : '开始 OCR 辅助检查'}</button><label htmlFor="tenant-human-confirmed" className="qualification-check"><input id="tenant-human-confirmed" data-testid="admin-tenant-qualification-tenants-review-human-confirmed" type="checkbox" checked={humanConfirmed} disabled={!inspectionComplete || pending} onChange={(event) => onHumanConfirmed(event.target.checked)} />我已人工比对原始证明与企业资料，确认结果一致</label></section>
        {error && <p className="qualification-alert error" role="alert">{error}</p>}
        <div className="qualification-dialog-actions"><button data-testid="admin-tenant-qualification-tenants-review-approve-open" type="button" disabled={!inspectionComplete || !humanConfirmed || pending} onClick={() => onDecision('APPROVE')}>通过</button><button data-testid="admin-tenant-qualification-tenants-review-supplement-open" type="button" className="button-secondary" disabled={pending} onClick={() => onDecision('SUPPLEMENT_REQUIRED')}>要求补充材料</button><button data-testid="admin-tenant-qualification-tenants-review-reject-open" type="button" className="button-danger" disabled={pending} onClick={() => onDecision('REJECT')}>驳回</button></div>
        <section><h3>变更记录</h3>{events.length ? <ol>{events.map((event) => <li key={event.id}>{displayTime(event.createdAt)} · {event.action} · {event.reason}</li>)}</ol> : <p>暂无变更记录。</p>}</section>
      </div>
    </ModalDialog>
  );
}
