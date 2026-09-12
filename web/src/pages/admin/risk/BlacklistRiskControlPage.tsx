import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  appealRiskDecision,
  BLACKLIST_RISK_PERMISSIONS,
  checkRisk,
  createBlacklistEntry,
  disableBlacklistEntry,
  importBlacklistEntries,
  listBlacklistEntries,
  listRiskProviders,
  requestBlacklistExport,
  riskAnalytics,
  saveRiskProvider,
  type BlacklistEntryPayload,
  type RiskDecisionRow,
  type RiskProviderPayload,
} from '@/api/blacklistRiskControlApi';
import { mutationErrorMessage } from '@/api/client';
import ModalDialog from '@/components/common/ModalDialog';
import { useIdentityAccess } from '@/pages/admin/identity/useIdentityAccess';
import { isPlatformRole, protectedQueryKey, useAuthStore } from '@/store/authStore';
import '@/styles/blacklist-risk-control.css';

const ENTRY_FORM: BlacklistEntryPayload = {
  tenantId: 42,
  mobile: '13900000001',
  listType: 'BLACK',
  source: 'MANUAL',
  reason: '投诉风险',
};

const PROVIDER_FORM: RiskProviderPayload = {
  providerName: 'local-risk',
  providerUrl: 'https://risk.example.test',
  credentialRef: 'secret-ref',
  checkLevel: 'ADVANCED',
  thresholdScore: 80,
  timeoutMs: 500,
  fallbackPolicy: 'CACHE',
  status: 'ACTIVE',
  cacheTtlSeconds: 300,
};

const IMPORT_FORM = {
  tenantId: ENTRY_FORM.tenantId,
  listType: ENTRY_FORM.listType,
  reason: ENTRY_FORM.reason,
};

export default function BlacklistRiskControlPage() {
  const userType = useAuthStore((state) => state.userType);
  const platformRole = isPlatformRole(userType);
  const access = useIdentityAccess(platformRole);
  const admin = userType === 'ADMIN';
  const canRead = admin || access.can(BLACKLIST_RISK_PERMISSIONS.read);
  const canWrite = admin || access.can(BLACKLIST_RISK_PERMISSIONS.write);
  const canImport = admin || access.can(BLACKLIST_RISK_PERMISSIONS.import);
  const canExport = admin || access.can(BLACKLIST_RISK_PERMISSIONS.export);
  const canProviderRead = admin || access.can(BLACKLIST_RISK_PERMISSIONS.providerRead);
  const canProviderWrite = admin || access.can(BLACKLIST_RISK_PERMISSIONS.providerWrite);
  const canAnalyze = admin || access.can(BLACKLIST_RISK_PERMISSIONS.analysisRead);
  const canCheck = admin || access.can(BLACKLIST_RISK_PERMISSIONS.analysisCheck);
  const canAppeal = admin || access.can(BLACKLIST_RISK_PERMISSIONS.appeal);
  const canUsePage = canRead || canWrite || canImport || canExport || canProviderRead || canProviderWrite || canAnalyze || canCheck || access.isLoading;
  const [entryForm, setEntryForm] = useState(ENTRY_FORM);
  const [filters, setFilters] = useState({ tenantId: '42', listType: '', status: 'ACTIVE' });
  const [importText, setImportText] = useState('13900000002\nbad-mobile');
  const [importForm, setImportForm] = useState(IMPORT_FORM);
  const [providerForm, setProviderForm] = useState(PROVIDER_FORM);
  const [entryOpen, setEntryOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [providerOpen, setProviderOpen] = useState(false);
  const [entryError, setEntryError] = useState('');
  const [importError, setImportError] = useState('');
  const [providerError, setProviderError] = useState('');
  const [riskTenantId, setRiskTenantId] = useState('42');
  const [riskInput, setRiskInput] = useState('13900000001\n13900009999');
  const [decisions, setDecisions] = useState<RiskDecisionRow[]>([]);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const queryClient = useQueryClient();
  const entriesKey = protectedQueryKey('blacklist-risk-entries');
  const providerKey = protectedQueryKey('blacklist-risk-providers');
  const analyticsKey = protectedQueryKey('blacklist-risk-analytics');

  const entries = useQuery({ queryKey: [...entriesKey, filters], queryFn: () => listBlacklistEntries(filters), enabled: platformRole && canRead, retry: false });
  const providers = useQuery({ queryKey: providerKey, queryFn: listRiskProviders, enabled: platformRole && canProviderRead, retry: false });
  const analytics = useQuery({ queryKey: analyticsKey, queryFn: riskAnalytics, enabled: platformRole && canAnalyze, retry: false });
  const refresh = async () => Promise.all([
    queryClient.invalidateQueries({ queryKey: entriesKey }),
    queryClient.invalidateQueries({ queryKey: providerKey }),
    queryClient.invalidateQueries({ queryKey: analyticsKey }),
  ]);

  const onSuccess = async (text: string) => {
    setMessage(text);
    setError('');
    await refresh();
  };
  const onError = (failure: unknown, fallback: string) => {
    setError(mutationErrorMessage(failure, fallback));
    setMessage('');
  };

  const createMutation = useMutation({
    mutationFn: createBlacklistEntry,
    onSuccess: async () => {
      await onSuccess('黑白名单已保存。');
      setEntryOpen(false);
      setEntryError('');
      setEntryForm(ENTRY_FORM);
    },
    onError: (failure) => {
      setMessage('');
      setEntryError(mutationErrorMessage(failure, '黑白名单保存失败'));
    },
  });
  const importMutation = useMutation({
    mutationFn: () => importBlacklistEntries({
      tenantId: importForm.tenantId,
      mobiles: importText.split(/\s+/).filter(Boolean),
      listType: importForm.listType,
      source: 'BATCH_IMPORT',
      reason: importForm.reason,
    }),
    onSuccess: async (result) => {
      await onSuccess(`导入完成：成功 ${result.success}，失败 ${result.failed}。`);
      setImportOpen(false);
      setImportError('');
      setImportForm(IMPORT_FORM);
      setImportText('13900000002\nbad-mobile');
    },
    onError: (failure) => {
      setMessage('');
      setImportError(mutationErrorMessage(failure, '导入失败'));
    },
  });
  const exportMutation = useMutation({
    mutationFn: () => requestBlacklistExport(filters),
    onSuccess: (result) => onSuccess(`导出请求已登记：${result.requestId}，匹配 ${result.matchedRows} 条。`),
    onError: (failure) => onError(failure, '导出请求失败'),
  });
  const disableMutation = useMutation({
    mutationFn: disableBlacklistEntry,
    onSuccess: () => onSuccess('黑白名单记录已移除。'),
    onError: (failure) => onError(failure, '移除失败'),
  });
  const providerMutation = useMutation({
    mutationFn: saveRiskProvider,
    onSuccess: async () => {
      await onSuccess('第三方风控配置已保存。');
      setProviderOpen(false);
      setProviderError('');
      setProviderForm(PROVIDER_FORM);
    },
    onError: (failure) => {
      setMessage('');
      setProviderError(mutationErrorMessage(failure, '第三方风控配置保存失败'));
    },
  });
  const checkMutation = useMutation({
    mutationFn: (forceProviderFailure: boolean) => checkRisk(Number(riskTenantId), riskInput.split(/\s+/).filter(Boolean), forceProviderFailure),
    onSuccess: async (result) => {
      setDecisions(result);
      await onSuccess('风险试算已记录。');
    },
    onError: (failure) => onError(failure, '风险试算失败'),
  });
  const appealMutation = useMutation({
    mutationFn: (decision: RiskDecisionRow) => appealRiskDecision(decision.id, '误判标记'),
    onSuccess: () => onSuccess('误判申诉已记录，原始决定保持不变。'),
    onError: (failure) => onError(failure, '申诉失败'),
  });

  if (!platformRole || !canUsePage) {
    return <p role="alert" data-testid="admin-blacklist-risk-access-denied">无权查看黑白名单与风控。</p>;
  }

  const rows = entries.data ?? [];
  const providerRows = providers.data ?? [];
  const stats = analytics.data;

  return (
    <section className="blacklist-risk-page" data-testid="admin-blacklist-risk-page">
      <nav aria-label="面包屑">验证规则 / 黑白名单与第三方风控</nav>
      <header className="blacklist-risk-header">
        <div>
          <h1>黑白名单与第三方风控</h1>
          <p className="page-description">在发送任务创建前完成白名单、系统黑名单、机构黑名单、第三方风险判定，并记录可申诉证据。</p>
        </div>
      </header>
      {message && <p role="status" data-testid="admin-blacklist-risk-message" className="blacklist-risk-alert success">{message}</p>}
      {error && <p role="alert" data-testid="admin-blacklist-risk-error" className="blacklist-risk-alert error">{error}</p>}

      <section className="card" data-testid="admin-blacklist-risk-black-white-lists-page">
        <h2>黑白名单</h2>
        <div className="blacklist-risk-form" data-testid="admin-blacklist-risk-black-white-lists-filters">
          <label>筛选机构
            <input data-testid="admin-blacklist-risk-black-white-lists-filter-tenant" value={filters.tenantId} onChange={(event) => setFilters({ ...filters, tenantId: event.target.value })} />
          </label>
          <label>筛选类型
            <select data-testid="admin-blacklist-risk-black-white-lists-filter-type" value={filters.listType} onChange={(event) => setFilters({ ...filters, listType: event.target.value })}>
              <option value="">全部</option>
              <option value="BLACK">黑名单</option>
              <option value="WHITE">白名单</option>
            </select>
          </label>
          <label>筛选状态
            <select data-testid="admin-blacklist-risk-black-white-lists-filter-status" value={filters.status} onChange={(event) => setFilters({ ...filters, status: event.target.value })}>
              <option value="">全部</option>
              <option value="ACTIVE">生效</option>
              <option value="DISABLED">已移除</option>
            </select>
          </label>
        </div>
        {canWrite && <button type="button" data-testid="admin-blacklist-risk-black-white-lists-create-open" onClick={() => { setEntryError(''); setEntryOpen(true); }}>新建名单记录</button>}
        {entryOpen && (
          <ModalDialog labelledBy="blacklist-entry-create-title" onRequestClose={() => { setEntryOpen(false); setEntryError(''); }}>
            <form className="blacklist-risk-form" data-testid="admin-blacklist-risk-black-white-lists-create-dialog" onSubmit={(event) => { event.preventDefault(); createMutation.mutate(entryForm); }}>
              <h2 id="blacklist-entry-create-title">新建名单记录</h2>
              <div className="dialog-form-fields" data-testid="admin-blacklist-risk-black-white-lists-form">
                <label>机构<input data-testid="admin-blacklist-risk-black-white-lists-tenant" type="number" value={entryForm.tenantId ?? ''} onChange={(event) => setEntryForm({ ...entryForm, tenantId: Number(event.target.value) || null })} /></label>
                <label>手机号<input required pattern="1[3-9][0-9]{9}" data-testid="admin-blacklist-risk-black-white-lists-mobile" value={entryForm.mobile} onChange={(event) => setEntryForm({ ...entryForm, mobile: event.target.value })} /></label>
                <label>类型<select data-testid="admin-blacklist-risk-black-white-lists-type" value={entryForm.listType} onChange={(event) => setEntryForm({ ...entryForm, listType: event.target.value })}><option value="BLACK">黑名单</option><option value="WHITE">白名单</option></select></label>
                <label>来源<select data-testid="admin-blacklist-risk-black-white-lists-source" value={entryForm.source} onChange={(event) => setEntryForm({ ...entryForm, source: event.target.value })}><option value="MANUAL">人工</option><option value="COMPLAINT_LINKED">投诉关联</option><option value="THIRD_PARTY_RISK">第三方风险</option></select></label>
                <label>原因<input required data-testid="admin-blacklist-risk-black-white-lists-reason" value={entryForm.reason} onChange={(event) => setEntryForm({ ...entryForm, reason: event.target.value })} /></label>
              </div>
              {entryError && <p role="alert" data-testid="admin-blacklist-risk-black-white-lists-create-error" className="blacklist-risk-alert error">{entryError}</p>}
              <div className="dialog-actions"><button type="button" className="button-secondary" data-testid="admin-blacklist-risk-black-white-lists-create-cancel" onClick={() => { setEntryOpen(false); setEntryError(''); }}>取消</button><button type="submit" data-testid="admin-blacklist-risk-black-white-lists-save" disabled={createMutation.isPending}>保存</button></div>
            </form>
          </ModalDialog>
        )}
        <div className="blacklist-risk-actions">
          <button type="button" data-testid="admin-blacklist-risk-black-white-lists-import" disabled={!canImport} onClick={() => { setImportError(''); setImportOpen(true); }}>批量导入</button>
          <button type="button" data-testid="admin-blacklist-risk-black-white-lists-export" disabled={!canExport} onClick={() => exportMutation.mutate()}>请求导出</button>
        </div>
        {importOpen && (
          <ModalDialog labelledBy="blacklist-import-title" onRequestClose={() => { setImportOpen(false); setImportError(''); }}>
            <form className="blacklist-risk-form" data-testid="admin-blacklist-risk-black-white-lists-import-dialog" onSubmit={(event) => { event.preventDefault(); importMutation.mutate(); }}>
              <h2 id="blacklist-import-title">批量导入名单</h2>
              <label>机构<input data-testid="admin-blacklist-risk-black-white-lists-import-tenant" type="number" value={importForm.tenantId ?? ''} onChange={(event) => setImportForm({ ...importForm, tenantId: Number(event.target.value) || null })} /></label>
              <label>类型<select data-testid="admin-blacklist-risk-black-white-lists-import-type" value={importForm.listType} onChange={(event) => setImportForm({ ...importForm, listType: event.target.value })}><option value="BLACK">黑名单</option><option value="WHITE">白名单</option></select></label>
              <label>原因<input required data-testid="admin-blacklist-risk-black-white-lists-import-reason" value={importForm.reason} onChange={(event) => setImportForm({ ...importForm, reason: event.target.value })} /></label>
              <label className="blacklist-risk-import">手机号列表<textarea required data-testid="admin-blacklist-risk-black-white-lists-import-input" value={importText} onChange={(event) => setImportText(event.target.value)} /></label>
              {importError && <p role="alert" data-testid="admin-blacklist-risk-black-white-lists-import-error" className="blacklist-risk-alert error">{importError}</p>}
              <div className="dialog-actions">
                <button type="button" className="button-secondary" data-testid="admin-blacklist-risk-black-white-lists-import-cancel" onClick={() => { setImportOpen(false); setImportError(''); }}>取消</button>
                <button type="submit" data-testid="admin-blacklist-risk-black-white-lists-import-submit" disabled={importMutation.isPending}>导入</button>
              </div>
            </form>
          </ModalDialog>
        )}
        <table className="ratio-table" data-testid="admin-blacklist-risk-black-white-lists-table">
          <thead><tr><th>类型</th><th>机构</th><th>脱敏手机号</th><th>来源</th><th>状态</th><th>原因</th><th>有效期</th><th>创建时间</th><th>动作</th></tr></thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.id} data-testid="admin-blacklist-risk-black-white-lists-row">
                <td>{row.listType}</td>
                <td>{row.tenantId ?? '系统级'}</td>
                <td>{row.maskedMobile}</td>
                <td>{row.source}</td>
                <td>{row.status}</td>
                <td>{row.reason}</td>
                <td>{row.expiresAt ?? '长期'}</td>
                <td>{row.createdAt}</td>
                <td><button type="button" data-testid="admin-blacklist-risk-black-white-lists-disable" disabled={!canWrite || row.status !== 'ACTIVE'} onClick={() => disableMutation.mutate(row.id)}>移除</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section className="card" data-testid="admin-blacklist-risk-risk-provider-page">
        <h2>第三方风控服务</h2>
        {canProviderWrite && <button type="button" data-testid="admin-blacklist-risk-risk-provider-create-open" onClick={() => { setProviderError(''); setProviderOpen(true); }}>新建风控服务</button>}
        {providerOpen && (
          <ModalDialog labelledBy="risk-provider-create-title" onRequestClose={() => { setProviderOpen(false); setProviderError(''); }}>
            <form className="blacklist-risk-form" data-testid="admin-blacklist-risk-risk-provider-create-dialog" onSubmit={(event) => { event.preventDefault(); providerMutation.mutate(providerForm); }}>
              <h2 id="risk-provider-create-title">新建风控服务</h2>
              <label>服务名<input required data-testid="admin-blacklist-risk-risk-provider-name" value={providerForm.providerName} onChange={(event) => setProviderForm({ ...providerForm, providerName: event.target.value })} /></label>
              <label>URL<input required type="url" data-testid="admin-blacklist-risk-risk-provider-url" value={providerForm.providerUrl} onChange={(event) => setProviderForm({ ...providerForm, providerUrl: event.target.value })} /></label>
              <label>凭据引用<input required data-testid="admin-blacklist-risk-risk-provider-credential" value={providerForm.credentialRef} onChange={(event) => setProviderForm({ ...providerForm, credentialRef: event.target.value })} /></label>
              <label>等级<select data-testid="admin-blacklist-risk-risk-provider-level" value={providerForm.checkLevel} onChange={(event) => setProviderForm({ ...providerForm, checkLevel: event.target.value })}><option value="BASIC">基础</option><option value="INTERMEDIATE">中级</option><option value="ADVANCED">高级</option></select></label>
              <label>阈值<input required min="0" max="100" data-testid="admin-blacklist-risk-risk-provider-threshold" type="number" value={providerForm.thresholdScore} onChange={(event) => setProviderForm({ ...providerForm, thresholdScore: Number(event.target.value) })} /></label>
              <label>超时<input required min="1" data-testid="admin-blacklist-risk-risk-provider-timeout" type="number" value={providerForm.timeoutMs} onChange={(event) => setProviderForm({ ...providerForm, timeoutMs: Number(event.target.value) })} /></label>
              <label>降级<select data-testid="admin-blacklist-risk-risk-provider-fallback" value={providerForm.fallbackPolicy} onChange={(event) => setProviderForm({ ...providerForm, fallbackPolicy: event.target.value })}><option value="ALLOW">失败放行</option><option value="CACHE">使用缓存</option></select></label>
              {providerError && <p role="alert" data-testid="admin-blacklist-risk-risk-provider-create-error" className="blacklist-risk-alert error">{providerError}</p>}
              <div className="dialog-actions"><button type="button" className="button-secondary" data-testid="admin-blacklist-risk-risk-provider-create-cancel" onClick={() => { setProviderOpen(false); setProviderError(''); }}>取消</button><button type="submit" data-testid="admin-blacklist-risk-risk-provider-save" disabled={providerMutation.isPending}>保存风控配置</button></div>
            </form>
          </ModalDialog>
        )}
        <ul data-testid="admin-blacklist-risk-risk-provider-list">
          {providerRows.map((provider) => <li key={provider.id}>{provider.providerName} / {provider.checkLevel} / {provider.fallbackPolicy}</li>)}
        </ul>
      </section>

      <section className="card" data-testid="admin-blacklist-risk-intercept-analytics-page">
        <h2>拦截分析与试算</h2>
        <div data-testid="admin-blacklist-risk-intercept-analytics-cards" className="blacklist-risk-cards">
          <span>总决策 {stats?.total ?? 0}</span>
          <span>拦截 {stats?.blocked ?? 0}</span>
          <span>系统 {stats?.systemHits ?? 0}</span>
          <span>机构 {stats?.tenantHits ?? 0}</span>
          <span>第三方 {stats?.providerHits ?? 0}</span>
          <span>降级 {stats?.degraded ?? 0}</span>
        </div>
        <label>检测机构
          <input data-testid="admin-blacklist-risk-intercept-check-tenant" type="number" value={riskTenantId} onChange={(event) => setRiskTenantId(event.target.value)} />
        </label>
        <label>检测对象
          <textarea data-testid="admin-blacklist-risk-intercept-check-input" value={riskInput} onChange={(event) => setRiskInput(event.target.value)} />
        </label>
        <div className="blacklist-risk-actions">
          <button type="button" data-testid="admin-blacklist-risk-intercept-check-run" disabled={!canCheck} onClick={() => checkMutation.mutate(false)}>执行试算</button>
          <button type="button" data-testid="admin-blacklist-risk-risk-provider-degraded" disabled={!canCheck} onClick={() => checkMutation.mutate(true)}>模拟降级</button>
        </div>
        <table className="ratio-table" data-testid="admin-blacklist-risk-intercept-decisions-table">
          <thead><tr><th>来源</th><th>结果</th><th>对象</th><th>原因</th><th>任务</th><th>计费</th><th>申诉</th></tr></thead>
          <tbody>
            {decisions.map((decision) => (
              <tr key={decision.id} data-testid="admin-blacklist-risk-intercept-decision-row">
                <td>{decision.sourceCategory}</td>
                <td>{decision.riskResult}</td>
                <td>{decision.mobileRef}</td>
                <td>{decision.traceReason}</td>
                <td>{decision.taskCreated ? '已创建' : '未创建'}</td>
                <td>{decision.charged ? '已计费' : '未计费'}</td>
                <td><button type="button" data-testid="admin-blacklist-risk-intercept-appeal" disabled={!canAppeal} onClick={() => appealMutation.mutate(decision)}>误判</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </section>
  );
}
