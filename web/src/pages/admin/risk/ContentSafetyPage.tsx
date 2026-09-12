import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  contentSafetyAnalytics,
  CONTENT_SAFETY_PERMISSIONS,
  deleteContentSafetyPolicy,
  importContentSafetyPolicies,
  listContentSafetyPolicies,
  requestContentSafetyExport,
  saveContentSafetyPolicy,
  scanFinalContent,
  type ContentSafetyPolicyPayload,
} from '@/api/contentSafetyApi';
import { mutationErrorMessage } from '@/api/client';
import ModalDialog from '@/components/common/ModalDialog';
import { useIdentityAccess } from '@/pages/admin/identity/useIdentityAccess';
import { isPlatformRole, protectedQueryKey, useAuthStore } from '@/store/authStore';
import '@/styles/content-safety.css';

const POLICY_FORM: ContentSafetyPolicyPayload = {
  word: '营销',
  category: 'MARKETING',
  level: 'HIGH',
  replacement: '通知',
  action: 'REPLACE',
  scope: 'GLOBAL',
  scopeRefId: null,
  status: 'ACTIVE',
};

const IMPORT_FORM: Pick<ContentSafetyPolicyPayload, 'category' | 'level' | 'replacement' | 'action' | 'scope' | 'scopeRefId'> = {
  category: POLICY_FORM.category,
  level: POLICY_FORM.level,
  replacement: POLICY_FORM.replacement,
  action: POLICY_FORM.action,
  scope: POLICY_FORM.scope,
  scopeRefId: POLICY_FORM.scopeRefId,
};

export default function ContentSafetyPage() {
  const userType = useAuthStore((state) => state.userType);
  const platformRole = isPlatformRole(userType);
  const access = useIdentityAccess(platformRole);
  const admin = userType === 'ADMIN';
  const canRead = admin || access.can(CONTENT_SAFETY_PERMISSIONS.read);
  const canWrite = admin || access.can(CONTENT_SAFETY_PERMISSIONS.write);
  const canImport = admin || access.can(CONTENT_SAFETY_PERMISSIONS.import);
  const canExport = admin || access.can(CONTENT_SAFETY_PERMISSIONS.export);
  const canScan = admin || access.can(CONTENT_SAFETY_PERMISSIONS.scan);
  const canUsePage = canRead || canWrite || canImport || canExport || canScan || access.isLoading;
  const [filters, setFilters] = useState({ word: '', category: '', level: '', action: '', status: 'ACTIVE' });
  const [form, setForm] = useState(POLICY_FORM);
  const [importText, setImportText] = useState('高危营销\nＡＢＣ');
  const [importForm, setImportForm] = useState(IMPORT_FORM);
  const [scanTenant, setScanTenant] = useState('17');
  const [scanTemplate, setScanTemplate] = useState('8');
  const [scanContent, setScanContent] = useState('【签名】变量填入ＡＢＣ，高危营销');
  const [scanResult, setScanResult] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [createError, setCreateError] = useState('');
  const [importError, setImportError] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const queryClient = useQueryClient();
  const policyKey = protectedQueryKey('content-safety-policies');
  const analyticsKey = protectedQueryKey('content-safety-analytics');

  const policies = useQuery({ queryKey: [...policyKey, filters], queryFn: () => listContentSafetyPolicies(filters), enabled: platformRole && canRead, retry: false });
  const analytics = useQuery({ queryKey: analyticsKey, queryFn: contentSafetyAnalytics, enabled: platformRole && canRead, retry: false });
  const refresh = async () => Promise.all([
    queryClient.invalidateQueries({ queryKey: policyKey }),
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
    mutationFn: saveContentSafetyPolicy,
    onSuccess: async () => {
      await onSuccess('内容审核策略已热更新。');
      setCreateOpen(false);
      setCreateError('');
      setForm(POLICY_FORM);
    },
    onError: (failure) => {
      setMessage('');
      setCreateError(mutationErrorMessage(failure, '内容审核策略保存失败'));
    },
  });
  const importMutation = useMutation({
    mutationFn: () => importContentSafetyPolicies({
      words: importText.split(/\s+/).filter(Boolean),
      category: importForm.category,
      level: importForm.level,
      replacement: importForm.replacement,
      action: importForm.action,
      scope: importForm.scope,
      scopeRefId: importForm.scopeRefId,
    }),
    onSuccess: async (result) => {
      await onSuccess(`词库导入完成：成功 ${result.success}，失败 ${result.failed}。`);
      setImportOpen(false);
      setImportError('');
      setImportForm(IMPORT_FORM);
      setImportText('高危营销\nＡＢＣ');
    },
    onError: (failure) => {
      setMessage('');
      setImportError(mutationErrorMessage(failure, '词库导入失败'));
    },
  });
  const exportMutation = useMutation({
    mutationFn: () => requestContentSafetyExport(filters),
    onSuccess: (result) => onSuccess(`导出请求已登记：${result.requestId}，匹配 ${result.matchedRows} 条。`),
    onError: (failure) => onError(failure, '导出请求失败'),
  });
  const deleteMutation = useMutation({
    mutationFn: deleteContentSafetyPolicy,
    onSuccess: () => onSuccess('内容审核策略已禁用，历史命中证据保留。'),
    onError: (failure) => onError(failure, '策略禁用失败'),
  });
  const scanMutation = useMutation({
    mutationFn: () => scanFinalContent(Number(scanTenant), Number(scanTemplate), scanContent),
    onSuccess: async (result) => {
      setScanResult(result.blocked ? `拦截：${result.reason}` : `放行：${result.finalContent}`);
      await onSuccess('最终内容试扫已执行。');
    },
    onError: (failure) => onError(failure, '最终内容试扫失败'),
  });

  if (!platformRole || !canUsePage) {
    return <p role="alert" data-testid="admin-runtime-content-content-safety-access-denied">无权查看内容审核。</p>;
  }

  const rows = policies.data ?? [];
  const stats = analytics.data;

  return (
    <section className="content-safety-page" data-testid="admin-runtime-content-content-safety-page">
      <nav aria-label="面包屑">验证规则 / 内容审核管理</nav>
      <header className="content-safety-header">
        <div>
          <h1>内容审核管理</h1>
          <p className="page-description">运行时扫描签名、模板和变量拼接后的最终文本，按作用域与级别执行拦截、替换或告警。</p>
        </div>
      </header>
      {message && <p role="status" data-testid="admin-runtime-content-content-safety-message" className="content-safety-alert success">{message}</p>}
      {error && <p role="alert" data-testid="admin-runtime-content-content-safety-error" className="content-safety-alert error">{error}</p>}

      <section className="card" data-testid="admin-runtime-content-content-safety-cards">
        <h2>统计卡片</h2>
        <div className="content-safety-cards">
          <span>词库总数 {stats?.total ?? 0}</span>
          <span>启用 {stats?.active ?? 0}</span>
          <span>今日拦截 {stats?.intercepts ?? 0}</span>
          <span>拦截率 {Math.round((stats?.interceptRate ?? 0) * 100)}%</span>
          <span>覆盖率 {Math.round((stats?.coverageRate ?? 0) * 100)}%</span>
        </div>
      </section>

      <section className="card" data-testid="admin-runtime-content-content-safety-policy-page">
        <h2>词库策略</h2>
        <div className="content-safety-form" data-testid="admin-runtime-content-content-safety-filters">
          <label>敏感词
            <input data-testid="admin-runtime-content-content-safety-filter-word" value={filters.word} onChange={(event) => setFilters({ ...filters, word: event.target.value })} />
          </label>
          <label>分类
            <select data-testid="admin-runtime-content-content-safety-filter-category" value={filters.category} onChange={(event) => setFilters({ ...filters, category: event.target.value })}>
              <option value="">全部</option><option value="ILLEGAL">违法</option><option value="FINANCIAL">金融</option><option value="MARKETING">营销</option><option value="POLITICAL">政治</option><option value="ADULT">色情</option><option value="OTHER">其他</option>
            </select>
          </label>
          <label>级别
            <select data-testid="admin-runtime-content-content-safety-filter-level" value={filters.level} onChange={(event) => setFilters({ ...filters, level: event.target.value })}>
              <option value="">全部</option><option value="HIGH">高</option><option value="MEDIUM">中</option><option value="LOW">低</option>
            </select>
          </label>
          <label>动作
            <select data-testid="admin-runtime-content-content-safety-filter-action" value={filters.action} onChange={(event) => setFilters({ ...filters, action: event.target.value })}>
              <option value="">全部</option><option value="BLOCK">拦截</option><option value="REPLACE">替换</option><option value="ALERT">告警</option>
            </select>
          </label>
          <label>状态
            <select data-testid="admin-runtime-content-content-safety-filter-status" value={filters.status} onChange={(event) => setFilters({ ...filters, status: event.target.value })}>
              <option value="">全部</option><option value="ACTIVE">启用</option><option value="DISABLED">禁用</option>
            </select>
          </label>
        </div>
        {canWrite && <button type="button" data-testid="admin-runtime-content-content-safety-create-open" onClick={() => { setCreateError(''); setCreateOpen(true); }}>新建词库策略</button>}
        {createOpen && (
          <ModalDialog labelledBy="content-safety-create-title" onRequestClose={() => { setCreateOpen(false); setCreateError(''); }}>
            <form className="content-safety-form" data-testid="admin-runtime-content-content-safety-create-dialog" onSubmit={(event) => { event.preventDefault(); saveMutation.mutate(form); }}>
              <h2 id="content-safety-create-title">新建词库策略</h2>
              <div className="dialog-form-fields" data-testid="admin-runtime-content-content-safety-form">
              <label>敏感词<input required data-testid="admin-runtime-content-content-safety-word" value={form.word} onChange={(event) => setForm({ ...form, word: event.target.value })} /></label>
              <label>分类
                <select data-testid="admin-runtime-content-content-safety-category" value={form.category} onChange={(event) => setForm({ ...form, category: event.target.value })}>
                  <option value="ILLEGAL">违法</option><option value="FINANCIAL">金融</option><option value="MARKETING">营销</option><option value="POLITICAL">政治</option><option value="ADULT">色情</option><option value="OTHER">其他</option>
                </select>
              </label>
              <label>级别<select data-testid="admin-runtime-content-content-safety-level" value={form.level} onChange={(event) => setForm({ ...form, level: event.target.value })}><option value="HIGH">高</option><option value="MEDIUM">中</option><option value="LOW">低</option></select></label>
              <label>替换词<input data-testid="admin-runtime-content-content-safety-replacement" value={form.replacement ?? ''} onChange={(event) => setForm({ ...form, replacement: event.target.value })} /></label>
              <label>动作<select data-testid="admin-runtime-content-content-safety-action" value={form.action} onChange={(event) => setForm({ ...form, action: event.target.value })}><option value="BLOCK">拦截</option><option value="REPLACE">替换</option><option value="ALERT">告警</option></select></label>
              <label>作用域
                <select data-testid="admin-runtime-content-content-safety-scope" value={form.scope} onChange={(event) => setForm({ ...form, scope: event.target.value, scopeRefId: event.target.value === 'GLOBAL' ? null : form.scopeRefId })}>
                  <option value="GLOBAL">全平台</option><option value="TENANT">指定机构</option><option value="PRODUCT">指定产品</option>
                </select>
              </label>
              <label>作用域ID<input data-testid="admin-runtime-content-content-safety-scope-ref" type="number" value={form.scopeRefId ?? ''} onChange={(event) => setForm({ ...form, scopeRefId: Number(event.target.value) || null })} /></label>
              </div>
              {createError && <p role="alert" data-testid="admin-runtime-content-content-safety-create-error" className="content-safety-alert error">{createError}</p>}
              <div className="dialog-actions">
                <button type="button" className="button-secondary" data-testid="admin-runtime-content-content-safety-create-cancel" onClick={() => { setCreateOpen(false); setCreateError(''); }}>取消</button>
                <button type="submit" data-testid="admin-runtime-content-content-safety-save" disabled={saveMutation.isPending}>保存并热更新</button>
              </div>
            </form>
          </ModalDialog>
        )}
        <div className="content-safety-actions">
          <button type="button" data-testid="admin-runtime-content-content-safety-import" disabled={!canImport} onClick={() => { setImportError(''); setImportOpen(true); }}>导入词库</button>
          <button type="button" data-testid="admin-runtime-content-content-safety-export" disabled={!canExport} onClick={() => exportMutation.mutate()}>请求导出</button>
        </div>
        {importOpen && (
          <ModalDialog labelledBy="content-safety-import-title" onRequestClose={() => { setImportOpen(false); setImportError(''); }}>
            <form className="content-safety-form" data-testid="admin-runtime-content-content-safety-import-dialog" onSubmit={(event) => { event.preventDefault(); importMutation.mutate(); }}>
              <h2 id="content-safety-import-title">批量导入词库</h2>
              <label>分类<select data-testid="admin-runtime-content-content-safety-import-category" value={importForm.category} onChange={(event) => setImportForm({ ...importForm, category: event.target.value })}><option value="ILLEGAL">违法</option><option value="FINANCIAL">金融</option><option value="MARKETING">营销</option><option value="POLITICAL">政治</option><option value="ADULT">色情</option><option value="OTHER">其他</option></select></label>
              <label>级别<select data-testid="admin-runtime-content-content-safety-import-level" value={importForm.level} onChange={(event) => setImportForm({ ...importForm, level: event.target.value })}><option value="HIGH">高</option><option value="MEDIUM">中</option><option value="LOW">低</option></select></label>
              <label>替换词<input data-testid="admin-runtime-content-content-safety-import-replacement" value={importForm.replacement ?? ''} onChange={(event) => setImportForm({ ...importForm, replacement: event.target.value })} /></label>
              <label>动作<select data-testid="admin-runtime-content-content-safety-import-action" value={importForm.action} onChange={(event) => setImportForm({ ...importForm, action: event.target.value })}><option value="BLOCK">拦截</option><option value="REPLACE">替换</option><option value="ALERT">告警</option></select></label>
              <label>作用域<select data-testid="admin-runtime-content-content-safety-import-scope" value={importForm.scope} onChange={(event) => setImportForm({ ...importForm, scope: event.target.value, scopeRefId: event.target.value === 'GLOBAL' ? null : importForm.scopeRefId })}><option value="GLOBAL">全平台</option><option value="TENANT">指定机构</option><option value="PRODUCT">指定产品</option></select></label>
              <label>作用域ID<input required={importForm.scope !== 'GLOBAL'} data-testid="admin-runtime-content-content-safety-import-scope-ref" type="number" value={importForm.scopeRefId ?? ''} onChange={(event) => setImportForm({ ...importForm, scopeRefId: Number(event.target.value) || null })} /></label>
              <label className="content-safety-import">敏感词列表<textarea required data-testid="admin-runtime-content-content-safety-import-input" value={importText} onChange={(event) => setImportText(event.target.value)} /></label>
              {importError && <p role="alert" data-testid="admin-runtime-content-content-safety-import-error" className="content-safety-alert error">{importError}</p>}
              <div className="dialog-actions">
                <button type="button" className="button-secondary" data-testid="admin-runtime-content-content-safety-import-cancel" onClick={() => { setImportOpen(false); setImportError(''); }}>取消</button>
                <button type="submit" data-testid="admin-runtime-content-content-safety-import-submit" disabled={importMutation.isPending}>导入词库</button>
              </div>
            </form>
          </ModalDialog>
        )}
        <table className="ratio-table" data-testid="admin-runtime-content-content-safety-table">
          <thead><tr><th>词</th><th>分类</th><th>级别</th><th>替换</th><th>动作</th><th>作用域</th><th>状态</th><th>命中</th><th>创建时间</th><th>操作</th></tr></thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.id} data-testid="admin-runtime-content-content-safety-row">
                <td>{row.word}</td><td>{row.category}</td><td>{row.level}</td><td>{row.replacement ?? '-'}</td><td>{row.action}</td><td>{row.scope}{row.scopeRefId ? `:${row.scopeRefId}` : ''}</td><td>{row.status}</td><td>{row.hitCount}</td><td>{row.createdAt ?? '-'}</td>
                <td><button type="button" data-testid="admin-runtime-content-content-safety-delete" disabled={!canWrite || row.status !== 'ACTIVE'} onClick={() => deleteMutation.mutate(row.id)}>删除/禁用</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section className="card" data-testid="admin-runtime-content-content-safety-scan-panel">
        <h2>最终内容试扫</h2>
        <div className="content-safety-form">
          <label>机构ID
            <input data-testid="admin-runtime-content-content-safety-scan-tenant" type="number" value={scanTenant} onChange={(event) => setScanTenant(event.target.value)} />
          </label>
          <label>模板ID
            <input data-testid="admin-runtime-content-content-safety-scan-template" type="number" value={scanTemplate} onChange={(event) => setScanTemplate(event.target.value)} />
          </label>
        </div>
        <textarea data-testid="admin-runtime-content-content-safety-scan-content" value={scanContent} onChange={(event) => setScanContent(event.target.value)} />
        <button type="button" data-testid="admin-runtime-content-content-safety-scan" disabled={!canScan} onClick={() => scanMutation.mutate()}>试扫最终内容</button>
        {scanResult && <p data-testid="admin-runtime-content-content-safety-scan-result">{scanResult}</p>}
      </section>
    </section>
  );
}
