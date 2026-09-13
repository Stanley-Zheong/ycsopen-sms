import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  approveFeeWarningEpisode,
  evaluateFeeWarning,
  listFeeWarningEpisodes,
  listFeeWarningRules,
  saveFeeWarningRule,
  type FeeWarningEpisode,
} from '@/api/feeWarningCreditApi';
import { mutationErrorMessage } from '@/api/client';
import '@/styles/alert-engine.css';

const defaultDraft = {
  ruleName: '授信比例预警',
  tenantId: '42',
  metricType: 'POSTPAID_CREDIT_RATIO',
  thresholdValue: '0.85',
  action: 'MANUAL_APPROVAL',
  notifyChannels: '["SMS","EMAIL"]',
  notificationTargets: '["tenant:42","finance","operations"]',
  status: 'ACTIVE',
};

function displayChannels(value: string | string[]) {
  return Array.isArray(value) ? value.join(',') : value;
}

export default function AdminFeeWarningPage() {
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState(defaultDraft);
  const [approvalReason, setApprovalReason] = useState('允许本次提交');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const tenantId = Number(draft.tenantId || 0);
  const rules = useQuery({ queryKey: ['fee-warning-rules', tenantId], queryFn: () => listFeeWarningRules(tenantId), retry: false });
  const episodes = useQuery({ queryKey: ['fee-warning-episodes', tenantId], queryFn: () => listFeeWarningEpisodes(tenantId), retry: false });

  async function refresh() {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['fee-warning-rules'] }),
      queryClient.invalidateQueries({ queryKey: ['fee-warning-episodes'] }),
    ]);
  }

  function fail(failure: unknown, fallback: string) {
    setError(mutationErrorMessage(failure, fallback));
    setMessage('');
  }

  const saveRule = useMutation({
    mutationFn: () => saveFeeWarningRule({
      ...draft,
      tenantId,
      thresholdValue: Number(draft.thresholdValue),
    }),
    onSuccess: () => refresh().then(() => setMessage('费用预警规则已保存')),
    onError: (failure) => fail(failure, '费用预警规则保存失败'),
  });
  const evaluate = useMutation({
    mutationFn: () => evaluateFeeWarning({ tenantId, estimatedAmountMil: 50 }),
    onSuccess: (rows) => refresh().then(() => setMessage(`费用预警评估完成：${rows.length} 条`)),
    onError: (failure) => fail(failure, '费用预警评估失败'),
  });
  const approve = useMutation({
    mutationFn: (row: FeeWarningEpisode) => approveFeeWarningEpisode(row.id, approvalReason),
    onSuccess: () => refresh().then(() => setMessage('费用预警审批已通过')),
    onError: (failure) => fail(failure, '费用预警审批失败'),
  });

  const setField = (field: keyof typeof draft, value: string) => setDraft((current) => ({ ...current, [field]: value }));

  return (
    <section className="alert-engine-page" data-testid="admin-fee-warning-credit-page">
      <nav aria-label="面包屑">财务中心 / 费用预警 / 授信管控</nav>
      <header className="alert-engine-header">
        <div>
          <h1>费用预警与授信管控</h1>
          <p className="page-description">按真实余额、预计可用天数和后付费授信比例触发唯一 episode，并记录通知投递证据。</p>
        </div>
        <button type="button" onClick={() => void refresh()} data-testid="admin-fee-warning-refresh">刷新</button>
      </header>

      {message && <p role="status" className="alert-engine-message success" data-testid="admin-fee-warning-message">{message}</p>}
      {error && <p role="alert" className="alert-engine-message error" data-testid="admin-fee-warning-error">{error}</p>}

      <section className="card alert-engine-rule-grid" data-testid="admin-fee-warning-rule-panel">
        <div>
          <h2>阈值规则</h2>
          <label>机构 ID<input data-testid="admin-fee-warning-fee-warning-tenant-id" value={draft.tenantId} onChange={(event) => setField('tenantId', event.target.value)} /></label>
          <label>规则名称<input data-testid="admin-fee-warning-fee-warning-rule-name" value={draft.ruleName} onChange={(event) => setField('ruleName', event.target.value)} /></label>
          <label>指标<select data-testid="admin-fee-warning-fee-warning-metric" value={draft.metricType} onChange={(event) => setField('metricType', event.target.value)}>
            <option value="PREPAID_AMOUNT">预付费余额</option>
            <option value="PREPAID_ESTIMATED_DAYS">预计可用天数</option>
            <option value="POSTPAID_CREDIT_RATIO">后付费授信比例</option>
          </select></label>
          <label>阈值<input data-testid="admin-fee-warning-fee-warning-threshold" value={draft.thresholdValue} onChange={(event) => setField('thresholdValue', event.target.value)} /></label>
          <label>动作<select data-testid="admin-fee-warning-fee-warning-action" value={draft.action} onChange={(event) => setField('action', event.target.value)}>
            <option value="WARN_ONLY">仅通知</option>
            <option value="MANUAL_APPROVAL">人工审批</option>
            <option value="BLOCK">自动阻断</option>
          </select></label>
          <button type="button" data-testid="admin-fee-warning-fee-warning-rule-save" onClick={() => saveRule.mutate()}>保存规则</button>
          <button type="button" data-testid="admin-fee-warning-fee-warning-evaluate" onClick={() => evaluate.mutate()}>按当前来源评估</button>
        </div>
        <div data-testid="admin-fee-warning-fee-warning-notification-targets">
          <h2>通知对象</h2>
          <label>渠道<input data-testid="admin-fee-warning-fee-warning-notify-channels" value={draft.notifyChannels} onChange={(event) => setField('notifyChannels', event.target.value)} /></label>
          <label>对象<input data-testid="admin-fee-warning-fee-warning-targets" value={draft.notificationTargets} onChange={(event) => setField('notificationTargets', event.target.value)} /></label>
          <p>支持 tenant、finance、operations 等目标，投递证据写入告警投递记录。</p>
        </div>
      </section>

      <section className="card" data-testid="admin-fee-warning-current-rules">
        <h2>当前规则</h2>
        {rules.isLoading && <p data-testid="admin-fee-warning-rules-loading">正在加载规则…</p>}
        {rules.isError && <p role="alert" data-testid="admin-fee-warning-rules-error">规则加载失败。</p>}
        <table className="alert-engine-table" data-testid="admin-fee-warning-rules-table">
          <thead><tr><th>名称</th><th>指标</th><th>阈值</th><th>动作</th><th>状态</th><th>通知</th></tr></thead>
          <tbody>
            {(rules.data ?? []).map((row) => (
              <tr key={row.id} data-testid="admin-fee-warning-rule-row">
                <td>{row.ruleName}</td>
                <td>{row.metricType}</td>
                <td>{row.thresholdValue}</td>
                <td>{row.action}</td>
                <td>{row.status}</td>
                <td>{displayChannels(row.notifyChannels)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section className="card" data-testid="admin-fee-warning-fee-warning-credit-action">
        <h2>预警 episode 与授信动作</h2>
        {episodes.isLoading && <p data-testid="admin-fee-warning-loading">正在加载费用预警…</p>}
        {episodes.isError && <p role="alert" data-testid="admin-fee-warning-load-error">费用预警加载失败。</p>}
        <label>审批原因<input data-testid="admin-fee-warning-fee-warning-approval-reason" value={approvalReason} onChange={(event) => setApprovalReason(event.target.value)} /></label>
        <table className="alert-engine-table" data-testid="admin-fee-warning-episode-table">
          <thead><tr><th>机构</th><th>指标</th><th>来源金额</th><th>授信</th><th>比例</th><th>动作</th><th>审批</th><th>投递</th><th>操作</th></tr></thead>
          <tbody>
            {(episodes.data ?? []).map((row) => (
              <tr key={row.id} data-testid="admin-fee-warning-episode-row">
                <td>{row.tenantId}</td>
                <td>{row.metricType}</td>
                <td>{row.sourceAmountMil}</td>
                <td>{row.creditLimitMil ?? '-'}</td>
                <td>{row.ratio ?? '-'}</td>
                <td>{row.action}</td>
                <td data-testid="admin-fee-warning-fee-warning-enforcement-action">{row.approvalState}</td>
                <td>{row.deliveryState}</td>
                <td><button type="button" data-testid="admin-fee-warning-fee-warning-approve" onClick={() => approve.mutate(row)}>批准</button></td>
              </tr>
            ))}
          </tbody>
        </table>
        <pre data-testid="admin-fee-warning-source-snapshot">{episodes.data?.[0]?.sourceSnapshot ?? '暂无来源快照'}</pre>
      </section>
    </section>
  );
}
