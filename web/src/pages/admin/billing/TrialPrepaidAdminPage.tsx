import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  activateTrial,
  listBalanceAudits,
  requestBalanceAuditExport,
  TRIAL_PREPAID_PERMISSIONS,
} from '@/api/trialPrepaidApi';
import {
  approveContract,
  getTrialAnalysis,
  listActivePriceBooks,
  listTrialCandidates,
  type TrialCandidate,
  type WorkbenchFilters,
} from '@/api/contractPricingApi';
import { mutationErrorMessage } from '@/api/client';
import ModalDialog from '@/components/common/ModalDialog';
import { QueryField, QueryPanel } from '@/components/common/QueryPanel';
import { useIdentityAccess } from '@/pages/admin/identity/useIdentityAccess';
import { isPlatformRole, protectedQueryKey, useAuthStore } from '@/store/authStore';
import '@/styles/trial-prepaid.css';

const EMPTY_FILTERS: WorkbenchFilters = { keyword: '', salesOwner: '', industry: '', trialStatus: '' };
const ATTACHMENT_PATTERN = /^oss:\/\/contracts\/[A-Za-z0-9][A-Za-z0-9._-]{0,199}$/;

function displayTime(value: string): string {
  const zoned = /(?:Z|[+-]\d\d:\d\d)$/.test(value) ? value : `${value}+08:00`;
  return new Date(zoned).toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' });
}

function displayRate(value: number | null): string {
  return value === null ? '不可用' : `${(value * 100).toFixed(2)}%`;
}

function requestErrorMessage(failure: unknown, fallback: string): string {
  const response = (failure as { response?: { data?: { message?: unknown } } })?.response;
  return typeof response?.data?.message === 'string' && response.data.message.trim()
    ? response.data.message
    : mutationErrorMessage(failure, fallback);
}

function candidateName(candidate: TrialCandidate): string {
  return `${candidate.shortName}（${candidate.tenantNo}）`;
}

function localInput(value: string): string {
  return value.slice(0, 16);
}

export default function TrialPrepaidAdminPage() {
  const userType = useAuthStore((state) => state.userType);
  const platformRole = isPlatformRole(userType);
  const supportedPlatformRole = userType === 'ADMIN' || userType === 'OPERATOR' || userType === 'FINANCE';
  const admin = userType === 'ADMIN';
  const access = useIdentityAccess(platformRole && !admin);
  const canRead = supportedPlatformRole && (admin || access.can(TRIAL_PREPAID_PERMISSIONS.read));
  const canWrite = supportedPlatformRole && (admin || access.can(TRIAL_PREPAID_PERMISSIONS.write));
  const queryClient = useQueryClient();

  const [filterDraft, setFilterDraft] = useState<WorkbenchFilters>(EMPTY_FILTERS);
  const [filters, setFilters] = useState<WorkbenchFilters>(EMPTY_FILTERS);
  const [analysisTenant, setAnalysisTenant] = useState<TrialCandidate | null>(null);
  const [adjustmentTenant, setAdjustmentTenant] = useState<TrialCandidate | null>(null);
  const [conversionTenant, setConversionTenant] = useState<TrialCandidate | null>(null);
  const [auditTenantDraft, setAuditTenantDraft] = useState('42');
  const [auditTenantApplied, setAuditTenantApplied] = useState('42');
  const [quota, setQuota] = useState('500');
  const [startAt, setStartAt] = useState('2026-09-09T00:00');
  const [endAt, setEndAt] = useState('2026-09-23T00:00');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [adjustmentFeedback, setAdjustmentFeedback] = useState('');
  const [conversionFeedback, setConversionFeedback] = useState('');
  const [billingMode, setBillingMode] = useState('POSTPAID');
  const [priceBookVersion, setPriceBookVersion] = useState('');
  const [contractNo, setContractNo] = useState('');
  const [signedAt, setSignedAt] = useState('');
  const [attachmentRef, setAttachmentRef] = useState('');
  const [creditLimitMil, setCreditLimitMil] = useState('');
  const [billingPeriod, setBillingPeriod] = useState('MONTHLY');
  const submitLatch = useRef(false);

  const workbenchKey = protectedQueryKey('contract-pricing-workbench', filters);
  const workbench = useQuery({
    queryKey: workbenchKey,
    queryFn: () => listTrialCandidates(filters),
    enabled: canRead,
    retry: false,
  });
  const priceBooks = useQuery({
    queryKey: protectedQueryKey('contract-pricing-price-books'),
    queryFn: listActivePriceBooks,
    enabled: canRead,
    retry: false,
  });
  const analysis = useQuery({
    queryKey: protectedQueryKey('contract-pricing-analysis', analysisTenant?.tenantId ?? null),
    queryFn: () => getTrialAnalysis(analysisTenant!.tenantId),
    enabled: canRead && analysisTenant !== null,
    retry: false,
  });

  const auditTenantId = auditTenantApplied ? Number(auditTenantApplied) : null;
  const auditKey = protectedQueryKey('trial-prepaid-balance-audits', auditTenantId);
  const audits = useQuery({
    queryKey: auditKey,
    queryFn: () => listBalanceAudits(auditTenantId),
    enabled: canRead,
    retry: false,
  });

  const activateMutation = useMutation({
    mutationFn: () => activateTrial(
      adjustmentTenant!.tenantId,
      Number(quota),
      startAt ? `${startAt}:00` : null,
      endAt ? `${endAt}:00` : null,
    ),
    onMutate: () => {
      setMessage('');
    },
    onSuccess: async (overview) => {
      setMessage(`试用已调整：${overview.quotaRemaining}/${overview.quotaTotal}，有效期至 ${overview.validUntil}`);
      setError('');
      setAdjustmentFeedback('');
      setAdjustmentTenant(null);
      await queryClient.invalidateQueries({ queryKey: protectedQueryKey('contract-pricing-workbench') });
    },
    onError: (failure) => {
      setAdjustmentFeedback(requestErrorMessage(failure, '试用配置保存失败'));
    },
  });

  const contractMutation = useMutation({
    mutationFn: () => approveContract(conversionTenant!.tenantId, {
      billingMode,
      priceBookVersion,
      contractNo,
      signedAt,
      attachmentRef,
      creditLimitMil: billingMode === 'POSTPAID' ? Number(creditLimitMil) : null,
      billingPeriod: billingMode === 'POSTPAID' ? billingPeriod : null,
    }),
    onMutate: () => {
      setMessage('');
    },
    onSuccess: async (contract) => {
      setMessage(`签约已生效：${contract.billingMode} / ${contract.priceBookVersion}`);
      setError('');
      setConversionFeedback('');
      setConversionTenant(null);
      await queryClient.invalidateQueries({ queryKey: protectedQueryKey('contract-pricing-workbench') });
    },
    onError: (failure) => {
      setConversionFeedback(requestErrorMessage(failure, '签约保存失败'));
    },
    onSettled: () => {
      submitLatch.current = false;
    },
  });

  const balanceAuditExportMutation = useMutation({
    mutationFn: () => requestBalanceAuditExport(auditTenantId),
    onSuccess: (job) => {
      setMessage(`余额审计导出任务已创建：${job.id}`);
      setError('');
    },
    onError: (failure) => {
      setError(requestErrorMessage(failure, '余额审计导出请求失败'));
      setMessage('');
    },
  });

  const conversionPending = contractMutation.isPending || submitLatch.current;
  const rows = workbench.isError ? [] : (workbench.data ?? []);
  const workbenchState = workbench.isFetching ? 'loading' : workbench.isError ? 'error' : rows.length === 0 ? 'empty' : 'success';
  const workbenchStatusText = workbenchState === 'loading' ? '正在加载试用机构'
    : workbenchState === 'error' ? '试用机构加载失败'
      : workbenchState === 'empty' ? '暂无试用机构' : `试用机构：${workbench.data?.length ?? 0} 条`;

  useEffect(() => {
    if (conversionTenant && !priceBookVersion && priceBooks.data?.[0]) {
      setPriceBookVersion(priceBooks.data[0].priceBookVersion);
    }
  }, [conversionTenant, priceBookVersion, priceBooks.data]);

  function openAdjustment(candidate: TrialCandidate) {
    setAdjustmentTenant(candidate);
    setQuota(String(candidate.quotaTotal));
    setStartAt(localInput(candidate.trialStartAt));
    setEndAt(localInput(candidate.trialEndAt));
    setAdjustmentFeedback('');
  }

  function openConversion(candidate: TrialCandidate) {
    if (!candidate.conversionEligible) return;
    setConversionTenant(candidate);
    setBillingMode('POSTPAID');
    setPriceBookVersion(priceBooks.data?.[0]?.priceBookVersion ?? '');
    setContractNo('');
    setSignedAt('');
    setAttachmentRef('');
    setCreditLimitMil('');
    setBillingPeriod('MONTHLY');
    setConversionFeedback('');
  }

  function submitConversion() {
    if (submitLatch.current || !conversionTenant) return;
    if (!priceBookVersion || !contractNo.trim() || !signedAt || !attachmentRef.trim()) {
      setConversionFeedback('请完整填写价目表、合同编号、签约日期和附件引用。');
      return;
    }
    if (!ATTACHMENT_PATTERN.test(attachmentRef) || attachmentRef.includes('..')) {
      setConversionFeedback('附件引用必须是 oss://contracts/<object> 的安全单段格式。');
      return;
    }
    if (billingMode === 'POSTPAID' && (!(Number(creditLimitMil) > 0) || !billingPeriod)) {
      setConversionFeedback('后付费合同必须填写正数授信额度和账期。');
      return;
    }
    submitLatch.current = true;
    setConversionFeedback('正在提交，请勿重复操作。');
    contractMutation.mutate();
  }

  if (!supportedPlatformRole || (!canRead && !access.isLoading)) {
    return <p role="alert" data-testid="admin-trial-prepaid-access-denied">无权查看试用和余额账本。</p>;
  }

  return (
    <section className="trial-prepaid-page" data-testid="admin-trial-prepaid-page">
      <nav aria-label="面包屑">平台管理 / 试用转正式工作台</nav>
      <header className="trial-prepaid-header">
        <div>
          <h1>试用转正式工作台</h1>
          <p className="page-description">从真实试用机构中筛选、分析并确认正式合同；机构身份始终由所选行绑定。</p>
        </div>
      </header>
      {message && <p role="status" data-testid="admin-trial-prepaid-message" className="trial-prepaid-alert success">{message}</p>}
      {error && <p role="alert" data-testid="admin-trial-prepaid-error" className="trial-prepaid-alert error">{error}</p>}

      <section data-testid="admin-contract-pricing-tenant-contract-page">
        <div data-testid="admin-trial-conversion-workbench-filters">
          <div
            data-testid="admin-trial-conversion-workbench-query-status"
            data-query-result-state={workbenchState}
            data-state={workbenchState}
            role={workbench.isError ? 'alert' : 'status'}
            aria-live={workbench.isError ? 'assertive' : 'polite'}
            aria-busy={workbench.isFetching}
            aria-describedby={workbench.isError ? 'admin-trial-conversion-workbench-error-details' : undefined}
          >
            {workbenchStatusText}
          </div>
          <QueryPanel
          initiallyExpanded
          showStatus={false}
          submitDisabled={!canRead}
          onSubmit={() => {
            setFilters({ ...filterDraft });
            return true;
          }}
          onReset={() => {
            setFilterDraft(EMPTY_FILTERS);
            setFilters(EMPTY_FILTERS);
          }}
          onRefresh={() => void workbench.refetch()}
          queryStatus={{
            testId: 'query-status',
            label: '试用机构',
            isFetching: workbench.isFetching,
            isError: workbench.isError,
            isEmpty: rows.length === 0,
            count: workbench.data?.length,
            errorDetailsId: 'admin-trial-conversion-workbench-error-details',
          }}
          result={(
            <>
              {workbench.isError && (
                <p id="admin-trial-conversion-workbench-error-details" className="trial-prepaid-alert error">
                  试用机构加载失败，请使用刷新重试。
                </p>
              )}
              <div data-testid="admin-trial-conversion-workbench-table">
                <div className="trial-workbench-table-wrap" data-testid="shared-trial-conversion-workbench-data-table">
                  <table className="ratio-table trial-workbench-table" data-testid="data-table">
                    <thead>
                      <tr>
                        <th>机构</th><th>销售 / 行业</th><th>配置快照</th><th>试用窗口 / 额度</th>
                        <th>消息表现</th><th>投诉</th><th>数据状态</th><th>服务资格</th><th>操作</th>
                      </tr>
                    </thead>
                    <tbody>
                      {rows.length === 0 && (
                        <tr data-testid="table-empty"><td colSpan={9}>
                          <span data-testid="shared-trial-conversion-workbench-table-empty">
                            {workbench.isError ? '试用机构暂不可用。' : workbench.isFetching ? '正在加载试用机构…' : '没有符合条件的试用机构，请调整筛选条件。'}
                          </span>
                        </td></tr>
                      )}
                      {rows.map((candidate) => (
                      <tr key={candidate.tenantId} data-testid="admin-trial-conversion-workbench-row" data-tenant-no={candidate.tenantNo}>
                        <td><strong>{candidate.shortName}</strong><br />{candidate.tenantNo}<br />{candidate.fullName}<br />{candidate.lifecycleStatus} / {candidate.trialStatus}</td>
                        <td>{candidate.salesOwner ?? '未分配'}<br />{candidate.industry ?? '未设置'}</td>
                        <td><code>{candidate.configurationSnapshotVersion}</code></td>
                        <td>
                          {displayTime(candidate.trialStartAt)}—{displayTime(candidate.trialEndAt)}<br />
                          已用 {candidate.quotaUsed}/{candidate.quotaTotal}，剩余 {candidate.remainingDays} 天
                        </td>
                        <td>{candidate.successCount}/{candidate.messageCount}<br />成功率 {displayRate(candidate.successRate)}</td>
                        <td>{candidate.complaintCount} 条<br />投诉率 {displayRate(candidate.complaintRate)}</td>
                        <td>{candidate.dataQuality}<br />{displayTime(candidate.statisticsAt)}<br /><small>{candidate.sourceRegistry}</small></td>
                        <td>
                          <span className={`trial-prepaid-status ${candidate.conversionEligible ? '' : 'status-trial_frozen'}`}>
                            {candidate.conversionEligible ? '可转正式' : '不可转正式'}
                          </span>
                          <div data-testid="admin-trial-conversion-workbench-eligibility-reasons">
                            {candidate.ineligibilityReasons.join('；') || '服务资格校验通过'}
                          </div>
                        </td>
                        <td className="trial-workbench-actions">
                          <button type="button" className="button-secondary" data-testid="admin-trial-conversion-workbench-row-analysis" disabled={workbench.isFetching} onClick={() => setAnalysisTenant(candidate)}>试用分析</button>
                          <button type="button" className="button-secondary" data-testid="admin-trial-conversion-workbench-row-adjust" disabled={!canWrite || workbench.isFetching} onClick={() => openAdjustment(candidate)}>调整试用</button>
                          <button type="button" data-testid="admin-trial-conversion-workbench-row-convert" disabled={!canWrite || workbench.isFetching || !candidate.conversionEligible} onClick={() => openConversion(candidate)}>转正式</button>
                        </td>
                      </tr>
                    ))}</tbody>
                  </table>
                </div>
              </div>
            </>
          )}
        >
          <QueryField name="trial-keyword" label="机构名称或编号">
            <input data-testid="admin-trial-conversion-workbench-filter-keyword" maxLength={128} value={filterDraft.keyword} onChange={(event) => setFilterDraft({ ...filterDraft, keyword: event.target.value })} />
          </QueryField>
          <QueryField name="trial-sales-owner" label="销售负责人">
            <input data-testid="admin-trial-conversion-workbench-filter-sales-owner" maxLength={64} value={filterDraft.salesOwner} onChange={(event) => setFilterDraft({ ...filterDraft, salesOwner: event.target.value })} />
          </QueryField>
          <QueryField name="trial-industry" label="行业">
            <input data-testid="admin-trial-conversion-workbench-filter-industry" maxLength={64} value={filterDraft.industry} onChange={(event) => setFilterDraft({ ...filterDraft, industry: event.target.value })} />
          </QueryField>
          <QueryField name="trial-status" label="试用状态">
            <select data-testid="admin-trial-conversion-workbench-filter-status" value={filterDraft.trialStatus} onChange={(event) => setFilterDraft({ ...filterDraft, trialStatus: event.target.value })}>
              <option value="">全部</option><option value="TRIAL">试用中</option><option value="TRIAL_FROZEN">已冻结/到期</option>
            </select>
          </QueryField>
          </QueryPanel>
        </div>
      </section>

      {analysisTenant && (
        <ModalDialog labelledBy="trial-analysis-title" onRequestClose={() => setAnalysisTenant(null)}>
          <div data-testid="admin-trial-conversion-workbench-analysis-dialog" className="trial-workbench-dialog">
            <h2 id="trial-analysis-title">{candidateName(analysisTenant)}试用分析</h2>
            {analysis.isFetching && <p role="status">正在加载试用分析…</p>}
            {analysis.isError && <p role="alert">试用分析加载失败，请关闭后重试。</p>}
            {analysis.data && (
              <>
                <p>统计区间：{displayTime(analysis.data.periodStart)}—{displayTime(analysis.data.periodEnd)}</p>
                <p>数据质量：{analysis.data.dataQuality}；更新时间：{displayTime(analysis.data.statisticsAt)}；来源：{analysis.data.sourceRegistry}</p>
                {analysis.data.dataQuality === 'NO_DATA' && <p>试用期内暂无消息与投诉数据。</p>}
                <h3>每日趋势</h3>
                <table className="ratio-table"><thead><tr><th>日期</th><th>消息</th><th>成功</th><th>失败</th><th>投诉</th></tr></thead>
                  <tbody>{analysis.data.trend.map((row) => <tr key={row.date}><td>{row.date}</td><td>{row.messageCount}</td><td>{row.successCount}</td><td>{row.failureCount}</td><td>{row.complaintCount}</td></tr>)}</tbody>
                </table>
                <h3>消息状态</h3>
                <p>{analysis.data.messageStatuses.map((row) => `${row.status} ${row.count}`).join('；') || '暂无消息状态数据'}</p>
                <h3>投诉明细</h3>
                {analysis.data.complaints.length === 0 ? <p>暂无投诉。</p> : (
                  <table className="ratio-table"><thead><tr><th>时间</th><th>来源</th><th>消息</th><th>状态</th><th>摘要</th></tr></thead>
                    <tbody>{analysis.data.complaints.map((row) => <tr key={row.id}><td>{displayTime(row.createdAt)}</td><td>{row.source}</td><td>{row.messageId ?? '—'}</td><td>{row.status}</td><td>{row.summary ?? '—'}</td></tr>)}</tbody>
                  </table>
                )}
              </>
            )}
            <button type="button" className="button-secondary" data-testid="admin-trial-conversion-workbench-analysis-close" onClick={() => setAnalysisTenant(null)}>关闭</button>
          </div>
        </ModalDialog>
      )}

      {adjustmentTenant && (
        <ModalDialog labelledBy="trial-adjustment-title" onRequestClose={() => { if (!activateMutation.isPending) setAdjustmentTenant(null); }}>
          <div data-testid="admin-trial-conversion-workbench-adjust-dialog" className="trial-workbench-dialog">
            <h2 id="trial-adjustment-title">调整 {candidateName(adjustmentTenant)} 的试用</h2>
            <p>机构身份由工作台所选行绑定，不可编辑。</p>
            <div data-testid="shared-trial-conversion-workbench-entity-form"><div className="trial-prepaid-form" data-testid="entity-form">
              <label>试用额度<input data-testid="admin-trial-prepaid-tenant-trial-quota" type="number" min="1" value={quota} onChange={(event) => setQuota(event.target.value)} /></label>
              <label>有效期开始<input data-testid="admin-trial-prepaid-tenant-trial-validity-start" type="datetime-local" value={startAt} onChange={(event) => setStartAt(event.target.value)} /></label>
              <label data-testid="admin-trial-prepaid-tenant-trial-validity">有效期结束<input data-testid="admin-trial-prepaid-tenant-trial-validity-end" type="datetime-local" value={endAt} onChange={(event) => setEndAt(event.target.value)} /></label>
            </div></div>
            {adjustmentFeedback && <p role="alert" className="trial-prepaid-alert error">{adjustmentFeedback}</p>}
            <div className="trial-workbench-dialog-actions">
              <span data-testid="shared-trial-conversion-workbench-form-cancel"><button type="button" className="button-secondary" data-testid="form-cancel" disabled={activateMutation.isPending} onClick={() => setAdjustmentTenant(null)}><span data-testid="admin-trial-conversion-workbench-adjust-cancel">取消</span></button></span>
              <span data-testid="shared-trial-conversion-workbench-form-submit"><button type="button" data-testid="form-submit" disabled={activateMutation.isPending || !(Number(quota) > 0) || !startAt || !endAt || startAt >= endAt} onClick={() => activateMutation.mutate()}><span data-testid="admin-trial-prepaid-activate-trial">{activateMutation.isPending ? '保存中…' : '保存试用调整'}</span></button></span>
            </div>
          </div>
        </ModalDialog>
      )}

      {conversionTenant && (
        <ModalDialog labelledBy="trial-conversion-title" onRequestClose={() => { if (!conversionPending) setConversionTenant(null); }}>
          <div className="trial-workbench-dialog">
            <h2 id="trial-conversion-title">确认试用转正式</h2>
            <p data-testid="admin-trial-conversion-workbench-selected-tenant"><strong>{candidateName(conversionTenant)}</strong><br />请确认合同归属，机构身份不可编辑。</p>
            <div className="trial-prepaid-form" data-testid="entity-form">
              <label>计费模式<select data-testid="admin-contract-pricing-tenant-contract-billing-mode" value={billingMode} disabled={conversionPending} onChange={(event) => setBillingMode(event.target.value)}><option value="POSTPAID">后付费</option><option value="PREPAID">预付费</option></select></label>
              <label>有效价目表版本<select data-testid="admin-contract-pricing-tenant-contract-price-version" value={priceBookVersion} disabled={conversionPending || priceBooks.isFetching || priceBooks.isError || (priceBooks.data ?? []).length === 0} onChange={(event) => setPriceBookVersion(event.target.value)}><option value="">请选择</option>{(priceBooks.data ?? []).map((price) => <option key={price.priceBookVersion} value={price.priceBookVersion}>{price.priceBookVersion} / {price.productCode} / {price.unitPriceMil} 厘</option>)}</select></label>
              {priceBooks.isFetching && <p role="status">正在加载有效价目表…</p>}
              {priceBooks.isError && <p role="alert">有效价目表加载失败。<button type="button" className="button-secondary" data-testid="admin-trial-conversion-workbench-price-retry" onClick={() => void priceBooks.refetch()}>重试</button></p>}
              {!priceBooks.isFetching && !priceBooks.isError && (priceBooks.data ?? []).length === 0 && <p role="alert">当前没有有效价目表，无法转正式。</p>}
              <label>合同编号<input data-testid="admin-contract-pricing-tenant-contract-number" maxLength={64} value={contractNo} disabled={conversionPending} onChange={(event) => setContractNo(event.target.value)} /></label>
              <label>签约日期<input data-testid="admin-contract-pricing-tenant-contract-signed-date" type="date" value={signedAt} disabled={conversionPending} onChange={(event) => setSignedAt(event.target.value)} /></label>
              <label>合同附件引用<input data-testid="admin-contract-pricing-tenant-contract-attachment" maxLength={223} value={attachmentRef} disabled={conversionPending} onChange={(event) => setAttachmentRef(event.target.value)} /><small>填写外部系统已分配的 oss://contracts/&lt;object&gt; 单段引用；本页不上传或校验外部对象存在性。</small></label>
              {billingMode === 'POSTPAID' && (
                <div data-testid="admin-contract-pricing-tenant-contract-postpaid-fields" className="trial-prepaid-form">
                  <label>授信额度（厘）<input data-testid="admin-contract-pricing-tenant-contract-credit-limit" type="number" min="1" value={creditLimitMil} disabled={conversionPending} onChange={(event) => setCreditLimitMil(event.target.value)} /></label>
                  <label data-testid="admin-contract-pricing-tenant-contract-credit-period">账期<select data-testid="admin-contract-pricing-tenant-contract-billing-period" value={billingPeriod} disabled={conversionPending} onChange={(event) => setBillingPeriod(event.target.value)}><option value="MONTHLY">月结</option><option value="QUARTERLY">季结</option></select></label>
                </div>
              )}
            </div>
            <p data-testid="admin-trial-conversion-workbench-conversion-feedback" role={conversionFeedback && !conversionPending ? 'alert' : 'status'} className={conversionFeedback && !conversionPending ? 'trial-prepaid-alert error' : ''}>{conversionFeedback || '提交后将原子写入合同并更新试用与机构生命周期。'}</p>
            <div className="trial-workbench-dialog-actions">
              <button type="button" className="button-secondary" data-testid="form-cancel" disabled={conversionPending} onClick={() => setConversionTenant(null)}><span data-testid="admin-trial-conversion-workbench-conversion-cancel">取消</span></button>
              <button type="button" data-testid="form-submit" disabled={!canWrite || conversionPending || priceBooks.isFetching || priceBooks.isError || !priceBookVersion} onClick={submitConversion}><span data-testid="admin-contract-pricing-tenant-contract-approve">{conversionPending ? '提交中…' : '批准转正式签约'}</span></button>
            </div>
          </div>
        </ModalDialog>
      )}

      <section data-testid="admin-balance-audit">
        <h2>余额审计</h2>
        <button type="button" data-testid="admin-secure-async-balance-audit-export" disabled={!canRead} onClick={() => balanceAuditExportMutation.mutate()}>请求安全异步导出</button>
        <QueryPanel
          legacyPanelTestId="admin-balance-audit-query-panel"
          onSubmit={() => {
            const nextTenantId = auditTenantDraft ? Number(auditTenantDraft) : null;
            if (Object.is(nextTenantId, auditTenantId)) return false;
            setAuditTenantApplied(auditTenantDraft);
            return true;
          }}
          onReset={() => {
            setAuditTenantDraft('');
            setAuditTenantApplied('');
          }}
          onRefresh={() => void audits.refetch()}
          submitDisabled={!canRead}
          queryStatus={{
            testId: 'admin-trial-prepaid-balance-audit-query-status',
            label: '余额审计',
            isFetching: audits.isFetching,
            isError: audits.isError,
            isEmpty: (audits.data ?? []).length === 0,
            count: audits.data?.length,
            errorDetailsId: 'admin-trial-prepaid-balance-audit-error-details',
          }}
          result={(
            <>
              {audits.isError && <p id="admin-trial-prepaid-balance-audit-error-details" data-testid="admin-trial-prepaid-balance-audit-error">余额审计加载失败。</p>}
              <table className="ratio-table" data-testid="admin-trial-prepaid-balance-audit-table">
                <thead><tr><th>机构</th><th>业务单</th><th>类型</th><th>金额(厘)</th><th>余额前/后</th><th>冻结前/后</th><th>版本</th><th>操作人</th><th>时间</th></tr></thead>
                <tbody>{(audits.data ?? []).map((row) => (
                  <tr key={`${row.businessDocId}-${row.mutationType}-${row.createdAt}`} data-testid="admin-trial-prepaid-balance-audit-row">
                    <td>{row.tenantId}</td><td>{row.businessDocId}</td><td>{row.mutationType}</td><td>{row.amountMil}</td>
                    <td>{row.beforeBalanceMil}/{row.afterBalanceMil}</td><td>{row.beforeFrozenMil}/{row.afterFrozenMil}</td>
                    <td>{row.accountVersion}</td><td>{row.actor}</td><td>{displayTime(row.createdAt)}</td>
                  </tr>
                ))}</tbody>
              </table>
            </>
          )}
        >
          <QueryField name="balance-audit-tenant" label="机构 ID">
            <input type="number" min="1" data-testid="admin-trial-prepaid-balance-audit-tenant-filter" value={auditTenantDraft} onChange={(event) => setAuditTenantDraft(event.target.value)} />
          </QueryField>
        </QueryPanel>
      </section>
    </section>
  );
}
