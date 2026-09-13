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
  const [filters, setFilters] = useState({ name: '', type: '', action: '', status: 'ACTIVE' });
  const [form, setForm] = useState(DEFAULT_RULE);
  const [importText, setImportText] = useState('同号秒级限制\n同IP分钟限制');
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
    onSuccess: () => onSuccess('频控规则已保存并热更新。'),
    onError: (failure) => onError(failure, '频控规则保存失败'),
  });
  const importMutation = useMutation({
    mutationFn: () => importFrequencyRules({
      ruleNames: importText.split(/\n+/).map((value) => value.trim()).filter(Boolean),
      limitType: form.limitType,
      limitCount: form.limitCount,
      limitWindowSeconds: form.limitWindowSeconds,
      action: form.action,
      scope: form.scope,
      scopeRefId: form.scopeRefId,
    }),
    onSuccess: (result) => onSuccess(`频控规则导入完成：成功 ${result.success}，失败 ${result.failed}。`),
    onError: (failure) => onError(failure, '频控规则导入失败'),
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

      <section className="card" data-testid="admin-frequency-api-frequency-rules-policy-page">
        <h2>规则列表</h2>
        <div className="frequency-rules-form" data-testid="admin-frequency-api-frequency-rules-filters">
          <label>规则名
            <input data-testid="admin-frequency-api-frequency-rules-filter-name" value={filters.name} onChange={(event) => setFilters({ ...filters, name: event.target.value })} />
          </label>
          <label>维度
            <select data-testid="admin-frequency-api-frequency-rules-filter-type" value={filters.type} onChange={(event) => setFilters({ ...filters, type: event.target.value })}>
              <option value="">全部</option><option value="MOBILE">手机号</option><option value="TENANT_LEVEL">机构</option><option value="IP">IP</option><option value="CONTENT_SIMILARITY">内容相似度</option>
            </select>
          </label>
          <label>动作
            <select data-testid="admin-frequency-api-frequency-rules-filter-action" value={filters.action} onChange={(event) => setFilters({ ...filters, action: event.target.value })}>
              <option value="">全部</option><option value="BLOCK">拦截</option><option value="DELAY">延迟</option><option value="ALERT">告警</option>
            </select>
          </label>
          <label>状态
            <select data-testid="admin-frequency-api-frequency-rules-filter-status" value={filters.status} onChange={(event) => setFilters({ ...filters, status: event.target.value })}>
              <option value="">全部</option><option value="ACTIVE">启用</option><option value="DISABLED">停用</option>
            </select>
          </label>
        </div>
        <div className="frequency-rules-form" data-testid="admin-frequency-api-frequency-rules-form">
          <label>规则名
            <input data-testid="admin-frequency-api-frequency-rules-name" value={form.ruleName} onChange={(event) => setForm({ ...form, ruleName: event.target.value })} />
          </label>
          <label>维度
            <select data-testid="admin-frequency-api-frequency-rules-type" value={form.limitType} onChange={(event) => setForm({ ...form, limitType: event.target.value })}>
              <option value="MOBILE">手机号</option><option value="TENANT_LEVEL">机构</option><option value="IP">IP</option><option value="CONTENT_SIMILARITY">内容相似度</option>
            </select>
          </label>
          <label>次数
            <input data-testid="admin-frequency-api-frequency-rules-count" type="number" value={form.limitCount} onChange={(event) => setForm({ ...form, limitCount: Number(event.target.value) || 1 })} />
          </label>
          <label>窗口
            <select data-testid="admin-frequency-api-frequency-rules-window" value={form.limitWindowSeconds} onChange={(event) => setForm({ ...form, limitWindowSeconds: Number(event.target.value) })}>
              <option value={1}>秒</option><option value={60}>分钟</option><option value={3600}>小时</option><option value={86400}>天</option>
            </select>
          </label>
          <label>动作
            <select data-testid="admin-frequency-api-frequency-rules-action" value={form.action} onChange={(event) => setForm({ ...form, action: event.target.value })}>
              <option value="BLOCK">拦截</option><option value="DELAY">延迟</option><option value="ALERT">告警</option>
            </select>
          </label>
          <label>作用域
            <select data-testid="admin-frequency-api-frequency-rules-scope" value={form.scope} onChange={(event) => setForm({ ...form, scope: event.target.value, scopeRefId: event.target.value === 'GLOBAL' ? null : form.scopeRefId })}>
              <option value="GLOBAL">全平台</option><option value="TENANT">指定机构</option><option value="API_KEY">指定 API Key</option>
            </select>
          </label>
          <label>作用域ID
            <input data-testid="admin-frequency-api-frequency-rules-scope-ref" type="number" value={form.scopeRefId ?? ''} onChange={(event) => setForm({ ...form, scopeRefId: Number(event.target.value) || null })} />
          </label>
          <button type="button" data-testid="admin-frequency-api-frequency-rules-save" disabled={!canWrite} onClick={() => saveMutation.mutate(form)}>保存并热更新</button>
        </div>
        <label className="frequency-rules-import">批量导入
          <textarea data-testid="admin-frequency-api-frequency-rules-import-input" value={importText} onChange={(event) => setImportText(event.target.value)} />
        </label>
        <div className="frequency-rules-actions">
          <button type="button" data-testid="admin-frequency-api-frequency-rules-import" disabled={!canImport} onClick={() => importMutation.mutate()}>导入规则</button>
          <button type="button" data-testid="admin-frequency-api-frequency-rules-export" disabled={!canExport} onClick={() => exportMutation.mutate()}>请求导出</button>
        </div>
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
      </section>

      <section className="card" data-testid="shared-frequency-api-queued-feedback">
        <h2>高并发反馈</h2>
        <p>API 超限返回 429；控制台批量或发送超量时应展示排队或延迟处理提示，避免重复提交。</p>
      </section>
    </section>
  );
}
