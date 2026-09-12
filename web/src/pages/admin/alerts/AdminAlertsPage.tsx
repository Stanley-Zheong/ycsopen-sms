import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  acknowledgeAlert,
  evaluateAlertSource,
  getAlertDashboard,
  listAlertDeliveries,
  listAlertHistory,
  listAlertRules,
  muteAlert,
  resolveAlert,
  saveAlertRule,
  type AlertRecord,
} from '@/api/alertEngineApi';
import { mutationErrorMessage } from '@/api/client';
import ModalDialog from '@/components/common/ModalDialog';
import { QueryField, QueryPanel } from '@/components/common/QueryPanel';
import '@/styles/alert-engine.css';

const defaultRule = {
  ruleName: '通道失败率告警',
  ruleType: 'FAILURE_RATE',
  metricName: 'FAILURE_RATE',
  metricSource: 'statistics_aggregates',
  thresholdValue: '0.1',
  comparisonOp: '>=',
  durationMinutes: '5',
  severity: 'CRITICAL',
  notifyChannels: '["SMS","EMAIL","DINGTALK","WECOM"]',
  notificationTargets: '["operations","finance","tenant:7"]',
  sourceScope: 'PLATFORM',
  status: 'ACTIVE',
};

const defaultEvaluation = {
  metricName: defaultRule.metricName,
  thresholdValue: defaultRule.thresholdValue,
  durationMinutes: defaultRule.durationMinutes,
};

export default function AdminAlertsPage() {
  const queryClient = useQueryClient();
  const [ruleDraft, setRuleDraft] = useState(defaultRule);
  const [historyFilterDraft, setHistoryFilterDraft] = useState({ status: '', severity: '' });
  const [historyFilter, setHistoryFilter] = useState({ status: '', severity: '' });
  const [activeTab, setActiveTab] = useState<'ALL' | 'ACTIVE' | 'SEVERE'>('ALL');
  const [resolveReason, setResolveReason] = useState('确认来源已恢复');
  const [muteReason, setMuteReason] = useState('运营临时静音');
  const [ruleOpen, setRuleOpen] = useState(false);
  const [evaluationOpen, setEvaluationOpen] = useState(false);
  const [ruleError, setRuleError] = useState('');
  const [evaluationError, setEvaluationError] = useState('');
  const [evaluationDraft, setEvaluationDraft] = useState(defaultEvaluation);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  const dashboard = useQuery({ queryKey: ['alert-dashboard'], queryFn: getAlertDashboard, retry: false });
  const rules = useQuery({ queryKey: ['alert-rules'], queryFn: listAlertRules, retry: false });
  const history = useQuery({ queryKey: ['alert-history', historyFilter], queryFn: () => listAlertHistory(historyFilter), retry: false });
  const deliveries = useQuery({ queryKey: ['alert-deliveries'], queryFn: () => listAlertDeliveries(), retry: false });

  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['alert-dashboard'] }),
      queryClient.invalidateQueries({ queryKey: ['alert-rules'] }),
      queryClient.invalidateQueries({ queryKey: ['alert-history'] }),
      queryClient.invalidateQueries({ queryKey: ['alert-deliveries'] }),
    ]);
  };
  const ok = async (text: string) => {
    setMessage(text);
    setError('');
    await refresh();
  };
  const fail = (failure: unknown, fallback: string) => {
    setError(mutationErrorMessage(failure, fallback));
    setMessage('');
  };
  const saveRule = useMutation({
    mutationFn: () => saveAlertRule({
      ...ruleDraft,
      thresholdValue: Number(ruleDraft.thresholdValue),
      durationMinutes: Number(ruleDraft.durationMinutes),
    }),
    onSuccess: async () => {
      await ok('告警规则已保存');
      setRuleOpen(false);
      setRuleError('');
      setRuleDraft(defaultRule);
    },
    onError: (failure) => {
      setMessage('');
      setRuleError(mutationErrorMessage(failure, '告警规则保存失败'));
    },
  });
  const evaluate = useMutation({
    mutationFn: () => evaluateAlertSource({
      metricName: evaluationDraft.metricName,
      metricValue: Number(evaluationDraft.thresholdValue) + 0.05,
      sustainedMinutes: Number(evaluationDraft.durationMinutes),
      sourceModule: 'CHANNEL',
      sourceKey: 'channel:11:demo',
      title: '通道失败率过高',
      description: '通道 11 失败率超过阈值',
      impactScope: '影响 1250 项',
      forceAdapterFailure: false,
    }),
    onSuccess: async (result) => {
      await ok(`告警评估完成：${result.alerts.length} 条`);
      setEvaluationOpen(false);
      setEvaluationError('');
      setEvaluationDraft(defaultEvaluation);
    },
    onError: (failure) => {
      setMessage('');
      setEvaluationError(mutationErrorMessage(failure, '告警评估失败'));
    },
  });
  const acknowledge = useMutation({
    mutationFn: (row: AlertRecord) => acknowledgeAlert(row.id),
    onSuccess: () => ok('告警已确认'),
    onError: (failure) => fail(failure, '告警确认失败'),
  });
  const resolve = useMutation({
    mutationFn: (row: AlertRecord) => resolveAlert(row.id, resolveReason),
    onSuccess: () => ok('告警已解决'),
    onError: (failure) => fail(failure, '告警解决失败'),
  });
  const mute = useMutation({
    mutationFn: (row: AlertRecord) => muteAlert(row.id, 30, muteReason),
    onSuccess: () => ok('告警通知已静音'),
    onError: (failure) => fail(failure, '告警静音失败'),
  });

  const visibleAlerts = useMemo(() => {
    const rows = history.data ?? [];
    if (activeTab === 'ACTIVE') return rows.filter((row) => row.status === 'ACTIVE');
    if (activeTab === 'SEVERE') return rows.filter((row) => ['HIGH', 'CRITICAL'].includes(row.severity));
    return rows;
  }, [activeTab, history.data]);

  const setRule = (field: keyof typeof ruleDraft, value: string) => setRuleDraft((current) => ({ ...current, [field]: value }));

  return (
    <section className="alert-engine-page" data-testid="admin-alert-engine-page">
      <nav aria-label="面包屑">告警管理 / 告警规则 / 通知设置 / 告警历史</nav>
      <header className="alert-engine-header">
        <div>
          <h1>告警管理</h1>
          <p className="page-description">规则命中后生成唯一告警 episode，记录通知投递，支持确认、解决与全局静音。</p>
        </div>
        <button type="button" data-testid="admin-alert-engine-refresh" onClick={() => void refresh()}>刷新</button>
      </header>

      {message && <p role="status" className="alert-engine-message success" data-testid="admin-alert-engine-message">{message}</p>}
      {error && <p role="alert" className="alert-engine-message error" data-testid="admin-alert-engine-error">{error}</p>}

      <section className="alert-engine-cards" data-testid="admin-alert-engine-dashboard-cards">
        <article><span>总告警</span><strong>{dashboard.data?.totalCount ?? 0}</strong></article>
        <article><span>活跃告警</span><strong>{dashboard.data?.activeCount ?? 0}</strong></article>
        <article><span>严重告警</span><strong>{dashboard.data?.severeCount ?? 0}</strong></article>
        <article><span>已解决</span><strong>{dashboard.data?.resolvedCount ?? 0}</strong></article>
      </section>

      <section className="card alert-engine-rule-grid">
        <div data-testid="admin-alert-engine-rules-page">
          <h2>告警规则</h2>
          <button type="button" data-testid="admin-alert-engine-rule-create-open" onClick={() => { setRuleDraft(defaultRule); setRuleError(''); setRuleOpen(true); }}>新建告警规则</button>
          <button type="button" data-testid="admin-alert-engine-source-evaluate" onClick={() => { setEvaluationError(''); setEvaluationOpen(true); }}>模拟源事件评估</button>
        </div>

        <div data-testid="admin-alert-engine-notification-targets">
          <h2>通知设置</h2>
          <p>默认渠道：{ruleDraft.notifyChannels}</p>
          <p>默认对象：{ruleDraft.notificationTargets}</p>
          <p>支持 SMS、EMAIL、DINGTALK、WECOM，以及 individual、role、tenant、finance、operations 目标。</p>
        </div>
      </section>

      {ruleOpen && (
        <ModalDialog labelledBy="alert-rule-create-title" onRequestClose={() => { setRuleOpen(false); setRuleError(''); setRuleDraft(defaultRule); }}>
          <form data-testid="admin-alert-engine-rule-form-dialog" onSubmit={(event) => { event.preventDefault(); saveRule.mutate(); }}>
            <h2 id="alert-rule-create-title">新建告警规则</h2>
            <label>规则名称<input required data-testid="admin-alert-engine-rule-name" value={ruleDraft.ruleName} onChange={(event) => setRule('ruleName', event.target.value)} /></label>
            <label>指标<select data-testid="admin-alert-engine-rule-metric" value={ruleDraft.metricName} onChange={(event) => setRule('metricName', event.target.value)}>
              <option value="CHANNEL_HEALTH">通道异常</option>
              <option value="FAILURE_RATE">失败率</option>
              <option value="BALANCE">余额不足</option>
              <option value="QUEUE_BACKLOG">队列积压</option>
              <option value="COMPLAINT_RATIO">投诉占比</option>
            </select></label>
            <label>比较符<input required data-testid="admin-alert-engine-rule-comparison" value={ruleDraft.comparisonOp} onChange={(event) => setRule('comparisonOp', event.target.value)} /></label>
            <label>阈值<input required type="number" step="any" data-testid="admin-alert-engine-rule-threshold" value={ruleDraft.thresholdValue} onChange={(event) => setRule('thresholdValue', event.target.value)} /></label>
            <label>持续分钟<input required type="number" min="1" data-testid="admin-alert-engine-rule-duration" value={ruleDraft.durationMinutes} onChange={(event) => setRule('durationMinutes', event.target.value)} /></label>
            <label>严重级别<select data-testid="admin-alert-engine-rule-severity" value={ruleDraft.severity} onChange={(event) => setRule('severity', event.target.value)}>
              <option value="LOW">低</option><option value="MEDIUM">中</option><option value="HIGH">高</option><option value="CRITICAL">严重</option>
            </select></label>
            <label>通知渠道<input required data-testid="admin-alert-engine-notify-channels" value={ruleDraft.notifyChannels} onChange={(event) => setRule('notifyChannels', event.target.value)} /></label>
            <label>通知对象<input required data-testid="admin-alert-engine-notification-targets-input" value={ruleDraft.notificationTargets} onChange={(event) => setRule('notificationTargets', event.target.value)} /></label>
            {ruleError && <p role="alert" data-testid="admin-alert-engine-rule-form-error" className="alert-engine-message error">{ruleError}</p>}
            <div className="dialog-actions">
              <button type="button" className="button-secondary" data-testid="admin-alert-engine-rule-create-cancel" onClick={() => { setRuleOpen(false); setRuleError(''); setRuleDraft(defaultRule); }}>取消</button>
              <button type="submit" data-testid="admin-alert-engine-rule-save" disabled={saveRule.isPending}>保存规则</button>
            </div>
          </form>
        </ModalDialog>
      )}

      {evaluationOpen && (
        <ModalDialog labelledBy="alert-evaluation-title" onRequestClose={() => { setEvaluationOpen(false); setEvaluationError(''); }}>
          <form data-testid="admin-alert-engine-source-evaluate-dialog" onSubmit={(event) => { event.preventDefault(); evaluate.mutate(); }}>
            <h2 id="alert-evaluation-title">模拟源事件评估</h2>
            <label>指标<select data-testid="admin-alert-engine-source-evaluate-metric" value={evaluationDraft.metricName} onChange={(event) => setEvaluationDraft({ ...evaluationDraft, metricName: event.target.value })}>
              <option value="CHANNEL_HEALTH">通道异常</option><option value="FAILURE_RATE">失败率</option><option value="BALANCE">余额不足</option><option value="QUEUE_BACKLOG">队列积压</option><option value="COMPLAINT_RATIO">投诉占比</option>
            </select></label>
            <label>规则阈值<input required type="number" step="any" data-testid="admin-alert-engine-source-evaluate-threshold" value={evaluationDraft.thresholdValue} onChange={(event) => setEvaluationDraft({ ...evaluationDraft, thresholdValue: event.target.value })} /></label>
            <label>持续分钟<input required type="number" min="1" data-testid="admin-alert-engine-source-evaluate-duration" value={evaluationDraft.durationMinutes} onChange={(event) => setEvaluationDraft({ ...evaluationDraft, durationMinutes: event.target.value })} /></label>
            {evaluationError && <p role="alert" data-testid="admin-alert-engine-source-evaluate-error" className="alert-engine-message error">{evaluationError}</p>}
            <div className="dialog-actions">
              <button type="button" className="button-secondary" data-testid="admin-alert-engine-source-evaluate-cancel" onClick={() => { setEvaluationOpen(false); setEvaluationError(''); }}>取消</button>
              <button type="submit" data-testid="admin-alert-engine-source-evaluate-submit" disabled={evaluate.isPending}>执行评估</button>
            </div>
          </form>
        </ModalDialog>
      )}

      <section data-testid="admin-alert-engine-alert-history">
        <div className="alert-engine-toolbar">
          <h2>告警历史</h2>
        </div>
        <div className="alert-engine-tabs" data-testid="admin-alert-engine-dashboard-alert-tabs">
          <button type="button" onClick={() => setActiveTab('ALL')}>全部</button>
          <button type="button" onClick={() => setActiveTab('ACTIVE')}>活跃</button>
          <button type="button" onClick={() => setActiveTab('SEVERE')}>严重</button>
        </div>
        <label>解决原因<input data-testid="admin-alert-engine-resolve-reason" value={resolveReason} onChange={(event) => setResolveReason(event.target.value)} /></label>
        <label>静音原因<input data-testid="admin-alert-engine-mute-reason" value={muteReason} onChange={(event) => setMuteReason(event.target.value)} /></label>
        <QueryPanel
          onSubmit={() => setHistoryFilter({ ...historyFilterDraft })}
          onReset={() => {
            setHistoryFilterDraft({ status: '', severity: '' });
            setHistoryFilter({ status: '', severity: '' });
            setActiveTab('ALL');
          }}
          result={(
            <>
              {history.isLoading && <p>正在加载…</p>}
              {history.isError && <p role="alert">告警历史加载失败。</p>}
              {!history.isLoading && !history.isError && visibleAlerts.length === 0 && <p>暂无告警历史。</p>}
              {visibleAlerts.length > 0 && (
                <table className="alert-engine-table">
                  <thead>
                    <tr><th>标题</th><th>级别</th><th>状态</th><th>描述</th><th>来源</th><th>影响</th><th>触发</th><th>投递</th><th>操作</th></tr>
                  </thead>
                  <tbody>
                    {visibleAlerts.map((row) => (
                      <tr key={row.id} data-testid="admin-alert-engine-alert-row">
                        <td>{row.title}</td><td>{row.severity}</td><td>{row.status}</td><td>{row.content}</td>
                        <td>{row.sourceModule}/{row.sourceKey}</td><td>{row.impactScope}</td><td>{row.triggeredAt ?? '-'}</td><td>{row.deliveryState}</td>
                        <td data-testid="admin-alert-engine-dashboard-alert-action">
                          <button type="button" data-testid="admin-alert-engine-alert-acknowledge" onClick={() => acknowledge.mutate(row)}>确认</button>
                          <button type="button" data-testid="admin-alert-engine-alert-history-acknowledge" onClick={() => acknowledge.mutate(row)}>历史确认</button>
                          <button type="button" data-testid="admin-alert-engine-alert-resolve" onClick={() => resolve.mutate(row)}>解决</button>
                          <button type="button" data-testid="admin-alert-engine-alert-mute" onClick={() => mute.mutate(row)}>静音</button>
                          <button type="button" data-testid="admin-alert-engine-alert-history-mute" onClick={() => mute.mutate(row)}>历史静音</button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </>
          )}
        >
          <QueryField name="status" label="状态">
            <select data-testid="admin-alert-engine-history-status-filter" value={historyFilterDraft.status} onChange={(event) => setHistoryFilterDraft((current) => ({ ...current, status: event.target.value }))}>
              <option value="">全部</option><option value="ACTIVE">活跃</option><option value="ACKNOWLEDGED">已确认</option><option value="RESOLVED">已解决</option>
            </select>
          </QueryField>
          <QueryField name="severity" label="级别">
            <select data-testid="admin-alert-engine-history-severity-filter" value={historyFilterDraft.severity} onChange={(event) => setHistoryFilterDraft((current) => ({ ...current, severity: event.target.value }))}>
              <option value="">全部</option><option value="MEDIUM">中</option><option value="HIGH">高</option><option value="CRITICAL">严重</option>
            </select>
          </QueryField>
        </QueryPanel>
      </section>

      <section className="card" data-testid="admin-alert-engine-alert-delivery-attempts">
        <h2>通知投递证据</h2>
        <table className="alert-engine-table">
          <thead><tr><th>告警</th><th>渠道</th><th>目标</th><th>结果</th><th>重试</th><th>状态</th><th>失败原因</th></tr></thead>
          <tbody>
            {(deliveries.data ?? []).map((row) => (
              <tr key={row.id} data-testid="admin-alert-engine-delivery-row">
                <td>{row.alertRecordId}</td><td>{row.channel}</td><td>{row.targetSnapshot}</td><td>{row.providerResult}</td>
                <td>{row.retryCount}</td><td>{row.status}</td><td>{row.failureReason ?? '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section className="card">
        <h2>规则列表</h2>
        <table className="alert-engine-table">
          <thead><tr><th>规则</th><th>指标</th><th>阈值</th><th>持续</th><th>级别</th><th>渠道</th><th>目标</th><th>状态</th></tr></thead>
          <tbody>
            {(rules.data ?? []).map((row) => (
              <tr key={row.id} data-testid="admin-alert-engine-rule-row">
                <td>{row.ruleName}</td><td>{row.metricName}</td><td>{row.comparisonOp}{row.thresholdValue}</td>
                <td>{row.durationMinutes}</td><td>{row.severity}</td><td>{row.notifyChannels}</td><td>{row.notificationTargets}</td><td>{row.status}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </section>
  );
}
