import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  disableFrequencyRule,
  enableFrequencyRule,
  frequencyAnalytics,
  FREQUENCY_PERMISSIONS,
  importFrequencyRules,
  listFrequencyRules,
  requestFrequencyExport,
  saveFrequencyRule,
  type FrequencyRulePayload,
} from '@/api/frequencyRuleApi';
import { mutationErrorMessage } from '@/api/client';
import ModalDialog from '@/components/common/ModalDialog';
import { QueryField, QueryPanel } from '@/components/common/QueryPanel';
import { useIdentityAccess } from '@/pages/admin/identity/useIdentityAccess';
import { isPlatformRole, protectedQueryKey, useAuthStore } from '@/store/authStore';
import '@/styles/frequency-rules.css';

const DEFAULT_RULE: FrequencyRulePayload = {
  ruleName: '同号秒级限制',
  limitType: 'MOBILE',
  limitCount: 3,
  limitWindowSeconds: 1,
  action: 'BLOCK',
  scope: 'GLOBAL',
  scopeRefId: null,
  status: 'ACTIVE',
};

const IMPORT_RULE: Pick<FrequencyRulePayload, 'limitType' | 'limitCount' | 'limitWindowSeconds' | 'action' | 'scope' | 'scopeRefId'> = {
  limitType: DEFAULT_RULE.limitType,
  limitCount: DEFAULT_RULE.limitCount,
  limitWindowSeconds: DEFAULT_RULE.limitWindowSeconds,
  action: DEFAULT_RULE.action,
  scope: DEFAULT_RULE.scope,
  scopeRefId: DEFAULT_RULE.scopeRefId,
};

const EMPTY_FILTERS = { name: '', type: '', action: '', status: '' };

export default function FrequencyRulesPage() {
  const userType = useAuthStore((state) => state.userType);
  const platformRole = isPlatformRole(userType);
  const access = useIdentityAccess(platformRole);
  const admin = userType === 'ADMIN';
  const canRead = admin || access.can(FREQUENCY_PERMISSIONS.read);
  const canWrite = admin || access.can(FREQUENCY_PERMISSIONS.write);
  const canImport = admin || access.can(FREQUENCY_PERMISSIONS.import);
  const canExport = admin || access.can(FREQUENCY_PERMISSIONS.export);
  const canUsePage = canRead || canWrite || canImport || canExport || access.isLoading;
  const [draftFilters, setDraftFilters] = useState(EMPTY_FILTERS);
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [form, setForm] = useState(DEFAULT_RULE);
  const [importText, setImportText] = useState('同号秒级限制\n同IP分钟限制');
  const [importRule, setImportRule] = useState(IMPORT_RULE);
  const [createOpen, setCreateOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [createError, setCreateError] = useState('');
  const [importError, setImportError] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const queryClient = useQueryClient();
  const rulesKey = protectedQueryKey('frequency-rules');
  const analyticsKey = protectedQueryKey('frequency-analytics');

  const rules = useQuery({ queryKey: [...rulesKey, filters], queryFn: () => listFrequencyRules(filters), enabled: platformRole && canRead, retry: false });
  const analytics = useQuery({ queryKey: analyticsKey, queryFn: frequencyAnalytics, enabled: platformRole && canRead, retry: false });
  const refresh = async () => Promise.all([
    queryClient.invalidateQueries({ queryKey: rulesKey }),
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
  const saveMutation = useMutation({
    mutationFn: saveFrequencyRule,
    onSuccess: async () => {
      await onSuccess('频控规则已保存并热更新。');
      setCreateOpen(false);
      setCreateError('');
      setForm(DEFAULT_RULE);
    },
    onError: (failure) => {
      setMessage('');
      setCreateError(mutationErrorMessage(failure, '频控规则保存失败'));
    },
  });
  const importMutation = useMutation({
    mutationFn: () => importFrequencyRules({
      ruleNames: importText.split(/\n+/).map((value) => value.trim()).filter(Boolean),
      limitType: importRule.limitType,
      limitCount: importRule.limitCount,
      limitWindowSeconds: importRule.limitWindowSeconds,
      action: importRule.action,
      scope: importRule.scope,
      scopeRefId: importRule.scopeRefId,
    }),
    onSuccess: async (result) => {
      await onSuccess(`频控规则导入完成：成功 ${result.success}，失败 ${result.failed}。`);
      setImportOpen(false);
      setImportError('');
      setImportRule(IMPORT_RULE);
      setImportText('同号秒级限制\n同IP分钟限制');
    },
    onError: (failure) => {
      setMessage('');
      setImportError(mutationErrorMessage(failure, '频控规则导入失败'));
    },
  });
  const exportMutation = useMutation({
    mutationFn: () => requestFrequencyExport(filters),
    onSuccess: (result) => onSuccess(`导出请求已登记：${result.requestId}，匹配 ${result.matchedRows} 条。`),
    onError: (failure) => onError(failure, '导出请求失败'),
  });
  const disableMutation = useMutation({
    mutationFn: disableFrequencyRule,
    onSuccess: () => onSuccess('频控规则已停用，历史命中证据保留。'),
    onError: (failure) => onError(failure, '停用失败'),
  });
  const enableMutation = useMutation({
    mutationFn: enableFrequencyRule,
    onSuccess: () => onSuccess('频控规则已启用。'),
    onError: (failure) => onError(failure, '启用失败'),
  });

  if (!platformRole || !canUsePage) {
    return <p role="alert" data-testid="admin-frequency-api-frequency-rules-access-denied">无权查看频控规则。</p>;
  }

  const rows = rules.data ?? [];
  const stats = analytics.data;

  return (
    <section className="frequency-rules-page" data-testid="admin-frequency-api-frequency-rules-page">
      <nav aria-label="面包屑">验证规则 / 频控规则</nav>
      <header className="frequency-rules-header">
        <div>
          <h1>频控与 API 限流</h1>
          <p className="page-description">按手机号、机构、IP、内容相似度维度执行固定窗口频控；API Key 秒/分/时/日限流在发送入口前置拦截。</p>
        </div>
      </header>
      {message && <p role="status" data-testid="admin-frequency-api-frequency-rules-message" className="frequency-rules-alert success">{message}</p>}
      {error && <p role="alert" data-testid="admin-frequency-api-frequency-rules-error" className="frequency-rules-alert error">{error}</p>}

      <section className="card" data-testid="admin-frequency-api-frequency-rules-cards">
        <h2>统计卡片</h2>
        <div className="frequency-rules-cards">
          <span>规则总数 {stats?.total ?? 0}</span>
          <span>启用 {stats?.active ?? 0}</span>
          <span>今日命中 {stats?.hits ?? 0}</span>
          <span>今日拦截 {stats?.blocked ?? 0}</span>
          <span>延迟 {stats?.delayed ?? 0}</span>
          <span>告警 {stats?.alerts ?? 0}</span>
          <span>拦截率 {Math.round((stats?.blockRate ?? 0) * 100)}%</span>
          <span>覆盖率 {Math.round((stats?.coverageRate ?? 0) * 100)}%</span>
        </div>
      </section>

      <section data-testid="admin-frequency-api-frequency-rules-policy-page">
        <h2>规则列表</h2>
        <QueryPanel
          legacyPanelTestId="admin-frequency-api-frequency-rules-filters"
          onSubmit={() => setFilters({ ...draftFilters })}
          onReset={() => { setDraftFilters(EMPTY_FILTERS); setFilters(EMPTY_FILTERS); }}
          result={<>
            {canWrite && <button type="button" data-testid="admin-frequency-api-frequency-rules-create-open" onClick={() => { setCreateError(''); setCreateOpen(true); }}>新建频控规则</button>}
            {createOpen && (
          <ModalDialog labelledBy="frequency-rule-create-title" onRequestClose={() => { setCreateOpen(false); setCreateError(''); }}>
            <form className="frequency-rules-form" data-testid="admin-frequency-api-frequency-rules-create-dialog" onSubmit={(event) => { event.preventDefault(); saveMutation.mutate(form); }}>
              <h2 id="frequency-rule-create-title">新建频控规则</h2>
              <div className="dialog-form-fields" data-testid="admin-frequency-api-frequency-rules-form">
                <label>规则名<input required data-testid="admin-frequency-api-frequency-rules-name" value={form.ruleName} onChange={(event) => setForm({ ...form, ruleName: event.target.value })} /></label>
                <label>维度<select data-testid="admin-frequency-api-frequency-rules-type" value={form.limitType} onChange={(event) => setForm({ ...form, limitType: event.target.value })}><option value="MOBILE">手机号</option><option value="TENANT_LEVEL">机构</option><option value="IP">IP</option><option value="CONTENT_SIMILARITY">内容相似度</option></select></label>
                <label>次数<input required min="1" data-testid="admin-frequency-api-frequency-rules-count" type="number" value={form.limitCount} onChange={(event) => setForm({ ...form, limitCount: Number(event.target.value) || 1 })} /></label>
                <label>窗口<select data-testid="admin-frequency-api-frequency-rules-window" value={form.limitWindowSeconds} onChange={(event) => setForm({ ...form, limitWindowSeconds: Number(event.target.value) })}><option value={1}>秒</option><option value={60}>分钟</option><option value={3600}>小时</option><option value={86400}>天</option></select></label>
                <label>动作<select data-testid="admin-frequency-api-frequency-rules-action" value={form.action} onChange={(event) => setForm({ ...form, action: event.target.value })}><option value="BLOCK">拦截</option><option value="DELAY">延迟</option><option value="ALERT">告警</option></select></label>
                <label>作用域<select data-testid="admin-frequency-api-frequency-rules-scope" value={form.scope} onChange={(event) => setForm({ ...form, scope: event.target.value, scopeRefId: event.target.value === 'GLOBAL' ? null : form.scopeRefId })}><option value="GLOBAL">全平台</option><option value="TENANT">指定机构</option><option value="API_KEY">指定 API Key</option></select></label>
                <label>作用域ID<input data-testid="admin-frequency-api-frequency-rules-scope-ref" type="number" value={form.scopeRefId ?? ''} onChange={(event) => setForm({ ...form, scopeRefId: Number(event.target.value) || null })} /></label>
              </div>
              {createError && <p role="alert" data-testid="admin-frequency-api-frequency-rules-create-error" className="frequency-rules-alert error">{createError}</p>}
              <div className="dialog-actions">
                <button type="button" className="button-secondary" data-testid="admin-frequency-api-frequency-rules-create-cancel" onClick={() => { setCreateOpen(false); setCreateError(''); }}>取消</button>
                <button type="submit" data-testid="admin-frequency-api-frequency-rules-save" disabled={saveMutation.isPending}>保存并热更新</button>
              </div>
            </form>
          </ModalDialog>
            )}
            <div className="frequency-rules-actions">
              <button type="button" data-testid="admin-frequency-api-frequency-rules-import" disabled={!canImport} onClick={() => { setImportError(''); setImportOpen(true); }}>导入规则</button>
              <button type="button" data-testid="admin-frequency-api-frequency-rules-export" disabled={!canExport} onClick={() => exportMutation.mutate()}>请求导出</button>
            </div>
            {importOpen && (
          <ModalDialog labelledBy="frequency-import-title" onRequestClose={() => { setImportOpen(false); setImportError(''); }}>
            <form className="frequency-rules-form" data-testid="admin-frequency-api-frequency-rules-import-dialog" onSubmit={(event) => { event.preventDefault(); importMutation.mutate(); }}>
              <h2 id="frequency-import-title">批量导入频控规则</h2>
              <label>维度<select data-testid="admin-frequency-api-frequency-rules-import-type" value={importRule.limitType} onChange={(event) => setImportRule({ ...importRule, limitType: event.target.value })}><option value="MOBILE">手机号</option><option value="TENANT_LEVEL">机构</option><option value="IP">IP</option><option value="CONTENT_SIMILARITY">内容相似度</option></select></label>
              <label>次数<input required min="1" data-testid="admin-frequency-api-frequency-rules-import-count" type="number" value={importRule.limitCount} onChange={(event) => setImportRule({ ...importRule, limitCount: Number(event.target.value) || 1 })} /></label>
              <label>窗口<select data-testid="admin-frequency-api-frequency-rules-import-window" value={importRule.limitWindowSeconds} onChange={(event) => setImportRule({ ...importRule, limitWindowSeconds: Number(event.target.value) })}><option value={1}>秒</option><option value={60}>分钟</option><option value={3600}>小时</option><option value={86400}>天</option></select></label>
              <label>动作<select data-testid="admin-frequency-api-frequency-rules-import-action" value={importRule.action} onChange={(event) => setImportRule({ ...importRule, action: event.target.value })}><option value="BLOCK">拦截</option><option value="DELAY">延迟</option><option value="ALERT">告警</option></select></label>
              <label>作用域<select data-testid="admin-frequency-api-frequency-rules-import-scope" value={importRule.scope} onChange={(event) => setImportRule({ ...importRule, scope: event.target.value, scopeRefId: event.target.value === 'GLOBAL' ? null : importRule.scopeRefId })}><option value="GLOBAL">全平台</option><option value="TENANT">指定机构</option><option value="API_KEY">指定 API Key</option></select></label>
              <label>作用域ID<input required={importRule.scope !== 'GLOBAL'} data-testid="admin-frequency-api-frequency-rules-import-scope-ref" type="number" value={importRule.scopeRefId ?? ''} onChange={(event) => setImportRule({ ...importRule, scopeRefId: Number(event.target.value) || null })} /></label>
              <label className="frequency-rules-import">规则名列表<textarea required data-testid="admin-frequency-api-frequency-rules-import-input" value={importText} onChange={(event) => setImportText(event.target.value)} /></label>
              {importError && <p role="alert" data-testid="admin-frequency-api-frequency-rules-import-error" className="frequency-rules-alert error">{importError}</p>}
              <div className="dialog-actions">
                <button type="button" className="button-secondary" data-testid="admin-frequency-api-frequency-rules-import-cancel" onClick={() => { setImportOpen(false); setImportError(''); }}>取消</button>
                <button type="submit" data-testid="admin-frequency-api-frequency-rules-import-submit" disabled={importMutation.isPending}>导入规则</button>
              </div>
            </form>
          </ModalDialog>
            )}
            {rules.isLoading && <p role="status">正在加载频控规则…</p>}
            {rules.isError && <p role="alert">频控规则加载失败。</p>}
            {!rules.isLoading && !rules.isError && rows.length === 0 && <p>暂无频控规则。</p>}
            {!rules.isLoading && !rules.isError && rows.length > 0 && (
              <table className="ratio-table" data-testid="admin-frequency-api-frequency-rules-table">
                <thead><tr><th>规则</th><th>维度</th><th>次数</th><th>窗口</th><th>动作</th><th>作用域</th><th>状态</th><th>命中</th><th>创建时间</th><th>操作</th></tr></thead>
                <tbody>
                  {rows.map((row) => (
                    <tr key={row.id} data-testid="admin-frequency-api-frequency-rules-row">
                      <td>{row.ruleName}</td><td>{row.limitType}</td><td>{row.limitCount}</td><td>{row.limitWindowSeconds}</td><td>{row.action}</td><td>{row.scope}{row.scopeRefId ? `:${row.scopeRefId}` : ''}</td><td>{row.status}</td><td>{row.hitCount}</td><td>{row.createdAt ?? '-'}</td>
                      <td>
                        {row.status === 'ACTIVE'
                          ? <button type="button" data-testid="admin-frequency-api-frequency-rules-disable" disabled={!canWrite} onClick={() => disableMutation.mutate(row.id)}>停用</button>
                          : <button type="button" data-testid="admin-frequency-api-frequency-rules-enable" disabled={!canWrite} onClick={() => enableMutation.mutate(row.id)}>启用</button>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </>}
        >
          <QueryField name="name" label="规则名">
            <input data-testid="admin-frequency-api-frequency-rules-filter-name" value={draftFilters.name} onChange={(event) => setDraftFilters({ ...draftFilters, name: event.target.value })} />
          </QueryField>
          <QueryField name="type" label="维度">
            <select data-testid="admin-frequency-api-frequency-rules-filter-type" value={draftFilters.type} onChange={(event) => setDraftFilters({ ...draftFilters, type: event.target.value })}>
              <option value="">全部</option><option value="MOBILE">手机号</option><option value="TENANT_LEVEL">机构</option><option value="IP">IP</option><option value="CONTENT_SIMILARITY">内容相似度</option>
            </select>
          </QueryField>
          <QueryField name="action" label="动作">
            <select data-testid="admin-frequency-api-frequency-rules-filter-action" value={draftFilters.action} onChange={(event) => setDraftFilters({ ...draftFilters, action: event.target.value })}>
              <option value="">全部</option><option value="BLOCK">拦截</option><option value="DELAY">延迟</option><option value="ALERT">告警</option>
            </select>
          </QueryField>
          <QueryField name="status" label="状态">
            <select data-testid="admin-frequency-api-frequency-rules-filter-status" value={draftFilters.status} onChange={(event) => setDraftFilters({ ...draftFilters, status: event.target.value })}>
              <option value="">全部</option><option value="ACTIVE">启用</option><option value="DISABLED">停用</option>
            </select>
          </QueryField>
        </QueryPanel>
      </section>

      <section className="card" data-testid="shared-frequency-api-queued-feedback">
        <h2>高并发反馈</h2>
        <p>API 超限返回 429；控制台批量或发送超量时应展示排队或延迟处理提示，避免重复提交。</p>
      </section>
    </section>
  );
}
