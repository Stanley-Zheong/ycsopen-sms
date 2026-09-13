import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  evaluateTenantRisk,
  listTenantRiskEpisodes,
  listTenantRiskRules,
  recoverTenantRiskEpisode,
  saveTenantRiskRule,
  type TenantRiskEpisode,
} from '@/api/tenantRiskAutoPauseApi';
import { mutationErrorMessage } from '@/api/client';
import '@/styles/alert-engine.css';

const defaultDraft = {
  tenantId: '7',
  ruleName: '失败率封停',
  metric: 'FAILURE_RATE',
  thresholdValue: '0.2000',
  durationMinutes: '15',
  action: 'AUTO_SUSPEND',
  notifyTargets: '["tenant:7","ops"]',
  status: 'ACTIVE',
  numerator: '25',
  denominator: '100',
  windowMinutes: '15',
  sourceKey: 'failure-window-7',
  sourceRegistry: 'statistics_aggregates',
};

function displayRate(row: TenantRiskEpisode) {
  if (row.dataQuality === 'UNKNOWN' || row.rate == null) {
    return '未知';
  }
  return row.rate.toString();
}

export default function AdminTenantRiskPage() {
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState(defaultDraft);
  const [reviewId, setReviewId] = useState('review-42');
  const [recoveryNote, setRecoveryNote] = useState('来源复核通过，恢复提交');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const tenantId = Number(draft.tenantId || 0);
  const rules = useQuery({ queryKey: ['tenant-risk-rules', tenantId], queryFn: () => listTenantRiskRules(tenantId), retry: false });
  const episodes = useQuery({ queryKey: ['tenant-risk-episodes', tenantId], queryFn: () => listTenantRiskEpisodes(tenantId), retry: false });

  async function refresh() {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['tenant-risk-rules'] }),
      queryClient.invalidateQueries({ queryKey: ['tenant-risk-episodes'] }),
    ]);
  }

  function fail(failure: unknown, fallback: string) {
    setError(mutationErrorMessage(failure, fallback));
    setMessage('');
  }

  const saveRule = useMutation({
    mutationFn: () => saveTenantRiskRule({
      tenantId,
      ruleName: draft.ruleName,
      metric: draft.metric,
      thresholdValue: Number(draft.thresholdValue),
      durationMinutes: Number(draft.durationMinutes),
      action: draft.action,
      notifyTargets: draft.notifyTargets,
      status: draft.status,
    }),
    onSuccess: () => refresh().then(() => setMessage('机构风险规则已保存')),
    onError: (failure) => fail(failure, '机构风险规则保存失败'),
  });

  const evaluate = useMutation({
    mutationFn: () => evaluateTenantRisk({
      tenantId,
      metric: draft.metric,
      numerator: Number(draft.numerator),
      denominator: Number(draft.denominator),
      windowMinutes: Number(draft.windowMinutes),
      sourceKey: draft.sourceKey,
      sourceRegistry: draft.sourceRegistry,
    }),
    onSuccess: () => refresh().then(() => setMessage('机构风险评估完成')),
    onError: (failure) => fail(failure, '机构风险评估失败'),
  });

  const recover = useMutation({
    mutationFn: (row: TenantRiskEpisode) => recoverTenantRiskEpisode(row.id, { reviewId, note: recoveryNote }),
    onSuccess: () => refresh().then(() => setMessage('机构风险恢复已提交')),
    onError: (failure) => fail(failure, '机构风险恢复失败'),
  });

  const setField = (field: keyof typeof draft, value: string) => setDraft((current) => ({ ...current, [field]: value }));
  const firstEpisode = episodes.data?.[0];

  return (
    <section className="alert-engine-page" data-testid="admin-tenant-risk-rules-page">
      <nav aria-label="面包屑">风控中心 / 机构风险 / 自动暂停</nav>
      <header className="alert-engine-header">
        <div>
          <h1>机构风险预警与自动暂停</h1>
          <p className="page-description">按投诉率、失败率、退订率来源快照评估；来源不完整时显示未知，不显示安全比例。</p>
        </div>
        <button type="button" onClick={() => void refresh()} data-testid="admin-tenant-risk-refresh">刷新</button>
      </header>

      {message && <p role="status" className="alert-engine-message success" data-testid="admin-tenant-risk-message">{message}</p>}
      {error && <p role="alert" className="alert-engine-message error" data-testid="admin-tenant-risk-error">{error}</p>}

      <section className="card alert-engine-rule-grid" data-testid="admin-tenant-risk-rule-panel">
        <div>
          <h2>阈值规则</h2>
          <label>机构 ID<input data-testid="admin-tenant-risk-tenant-id" value={draft.tenantId} onChange={(event) => setField('tenantId', event.target.value)} /></label>
          <label>规则名称<input data-testid="admin-tenant-risk-rule-name" value={draft.ruleName} onChange={(event) => setField('ruleName', event.target.value)} /></label>
          <label>指标<select data-testid="admin-tenant-risk-metric" value={draft.metric} onChange={(event) => setField('metric', event.target.value)}>
            <option value="COMPLAINT_RATE">投诉率</option>
            <option value="FAILURE_RATE">失败率</option>
            <option value="UNSUBSCRIBE_RATE">退订率</option>
          </select></label>
          <label>阈值<input data-testid="admin-tenant-risk-threshold" value={draft.thresholdValue} onChange={(event) => setField('thresholdValue', event.target.value)} /></label>
          <label>持续窗口<input data-testid="admin-tenant-risk-duration" value={draft.durationMinutes} onChange={(event) => setField('durationMinutes', event.target.value)} /></label>
          <label>动作<select data-testid="admin-tenant-risk-action" value={draft.action} onChange={(event) => setField('action', event.target.value)}>
            <option value="NOTIFY">仅通知</option>
            <option value="AUTO_SUSPEND">自动暂停</option>
          </select></label>
          <label>通知对象<input data-testid="admin-tenant-risk-notify-targets" value={draft.notifyTargets} onChange={(event) => setField('notifyTargets', event.target.value)} /></label>
          <button type="button" data-testid="admin-tenant-risk-rule-save" onClick={() => saveRule.mutate()}>保存规则</button>
        </div>
        <div data-testid="admin-tenant-risk-source-evaluation">
          <h2>来源快照评估</h2>
          <label>分子<input data-testid="admin-tenant-risk-numerator" value={draft.numerator} onChange={(event) => setField('numerator', event.target.value)} /></label>
          <label>分母<input data-testid="admin-tenant-risk-denominator" value={draft.denominator} onChange={(event) => setField('denominator', event.target.value)} /></label>
          <label>来源窗口<input data-testid="admin-tenant-risk-window" value={draft.windowMinutes} onChange={(event) => setField('windowMinutes', event.target.value)} /></label>
          <label>来源键<input data-testid="admin-tenant-risk-source-key" value={draft.sourceKey} onChange={(event) => setField('sourceKey', event.target.value)} /></label>
          <label>来源注册<input data-testid="admin-tenant-risk-source-registry" value={draft.sourceRegistry} onChange={(event) => setField('sourceRegistry', event.target.value)} /></label>
          <button type="button" data-testid="admin-tenant-risk-evaluate" onClick={() => evaluate.mutate()}>评估来源</button>
        </div>
      </section>

      <section className="card" data-testid="admin-tenant-risk-current-rules">
        <h2>当前规则</h2>
        {rules.isLoading && <p data-testid="admin-tenant-risk-rules-loading">正在加载规则…</p>}
        {rules.isError && <p role="alert" data-testid="admin-tenant-risk-rules-error">规则加载失败。</p>}
        <table className="alert-engine-table" data-testid="admin-tenant-risk-rules-table">
          <thead><tr><th>名称</th><th>指标</th><th>阈值</th><th>窗口</th><th>动作</th><th>通知</th></tr></thead>
          <tbody>
            {(rules.data ?? []).map((row) => (
              <tr key={row.id} data-testid="admin-tenant-risk-rule-row">
                <td>{row.ruleName}</td>
                <td>{row.metric}</td>
                <td>{row.thresholdValue}</td>
                <td>{row.durationMinutes}</td>
                <td>{row.action}</td>
                <td>{row.notifyTargets}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section className="card" data-testid="admin-tenant-risk-tenant-risk-pause-detail">
        <h2>风险 episode / 暂停详情</h2>
        {episodes.isLoading && <p data-testid="admin-tenant-risk-loading">正在加载风险 episode…</p>}
        {episodes.isError && <p role="alert" data-testid="admin-tenant-risk-load-error">风险 episode 加载失败。</p>}
        <div className="alert-engine-cards">
          <article><span>数据质量</span><strong data-testid="admin-tenant-risk-data-quality">{firstEpisode?.dataQuality ?? '-'}</strong></article>
          <article><span>来源比例</span><strong data-testid="admin-tenant-risk-rate">{firstEpisode ? displayRate(firstEpisode) : '-'}</strong></article>
          <article><span>暂停状态</span><strong>{firstEpisode?.status ?? '-'}</strong></article>
        </div>
        <table className="alert-engine-table" data-testid="admin-tenant-risk-episode-table">
          <thead><tr><th>机构</th><th>指标</th><th>来源</th><th>分子</th><th>分母</th><th>质量</th><th>比例</th><th>动作</th><th>状态</th></tr></thead>
          <tbody>
            {(episodes.data ?? []).map((row) => (
              <tr key={row.id} data-testid="admin-tenant-risk-episode-row">
                <td>{row.tenantId}</td>
                <td>{row.metric}</td>
                <td>{row.sourceRegistry}</td>
                <td>{row.numerator}</td>
                <td>{row.denominator}</td>
                <td>{row.dataQuality}</td>
                <td>{displayRate(row)}</td>
                <td>{row.action}</td>
                <td>{row.status}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <pre data-testid="admin-tenant-risk-source-snapshot">{firstEpisode?.sourceSnapshot ?? '暂无来源快照'}</pre>
        <span data-testid="admin-complaint-ratio-dashboard-complaint-ratio-tenant">投诉率来源可进入机构风险 episode</span>
      </section>

      <section className="card alert-engine-rule-grid" data-testid="admin-tenant-risk-tenant-risk-recovery">
        <div>
          <h2>授权恢复</h2>
          <label>复核 ID<input data-testid="admin-tenant-risk-recovery-review-id" value={reviewId} onChange={(event) => setReviewId(event.target.value)} /></label>
          <label>恢复说明<input data-testid="admin-tenant-risk-recovery-note" value={recoveryNote} onChange={(event) => setRecoveryNote(event.target.value)} /></label>
          <button type="button" data-testid="admin-tenant-risk-recover" disabled={!firstEpisode} onClick={() => firstEpisode && recover.mutate(firstEpisode)}>恢复机构</button>
        </div>
        <p>恢复动作必须绑定复核 ID，并保留原始来源快照。</p>
      </section>
    </section>
  );
}
