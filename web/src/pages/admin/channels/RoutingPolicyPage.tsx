import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  importRoutingPolicy,
  listCircuitStates,
  listRoutingRules,
  listRoutingVersions,
  recordCircuit,
  ROUTING_POLICY_PERMISSIONS,
  saveRetryPolicy,
  simulateRouting,
  type SimulationResult,
} from '@/api/routingPolicyApi';
import { mutationErrorMessage } from '@/api/client';
import { useIdentityAccess } from '@/pages/admin/identity/useIdentityAccess';
import { isPlatformRole, protectedQueryKey, useAuthStore } from '@/store/authStore';
import '@/styles/routing-policy.css';

export default function RoutingPolicyPage() {
  const userType = useAuthStore((state) => state.userType);
  const platformRole = isPlatformRole(userType);
  const access = useIdentityAccess(platformRole);
  const admin = userType === 'ADMIN';
  const canRead = admin || access.can(ROUTING_POLICY_PERMISSIONS.read);
  const canWrite = admin || access.can(ROUTING_POLICY_PERMISSIONS.write);
  const canImport = admin || access.can(ROUTING_POLICY_PERMISSIONS.import);
  const queryClient = useQueryClient();
  const versionKey = protectedQueryKey('routing-policy-versions');
  const ruleKey = protectedQueryKey('routing-policy-rules');
  const circuitKey = protectedQueryKey('routing-policy-circuits');
  const versions = useQuery({ queryKey: versionKey, queryFn: listRoutingVersions, enabled: platformRole && canRead, retry: false });
  const rules = useQuery({ queryKey: ruleKey, queryFn: listRoutingRules, enabled: platformRole && canRead, retry: false });
  const circuits = useQuery({ queryKey: circuitKey, queryFn: listCircuitStates, enabled: platformRole && canRead, retry: false });
  const [versionNo, setVersionNo] = useState('RP20260909');
  const [ruleText, setRuleText] = useState('10,CARRIER,MOBILE,CHANNEL,CH_MAIN,100');
  const [tenantId, setTenantId] = useState('42');
  const [carrier, setCarrier] = useState('MOBILE');
  const [prefix, setPrefix] = useState('139');
  const [content, setContent] = useState('营销通知');
  const [category, setCategory] = useState('FAILURE');
  const [channelCode, setChannelCode] = useState('CH_MAIN');
  const [simulation, setSimulation] = useState<SimulationResult | null>(null);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const refresh = async () => Promise.all([
    queryClient.invalidateQueries({ queryKey: versionKey }),
    queryClient.invalidateQueries({ queryKey: ruleKey }),
    queryClient.invalidateQueries({ queryKey: circuitKey }),
  ]);
  const onError = (failure: unknown, fallback: string) => {
    setError(mutationErrorMessage(failure, fallback));
    setMessage('');
  };
  const importMutation = useMutation({
    mutationFn: () => importRoutingPolicy({
      versionNo,
      sourceName: '运营策略',
      effectiveAt: null,
      rules: ruleText.split(/\n+/).map((line) => {
        const [priority, conditionType, conditionValue, targetType, targetRef, weight] = line.split(',').map((part) => part.trim());
        return { priority: Number(priority), conditionType, conditionValue, targetType, targetRef, weight: Number(weight || 0) };
      }),
    }),
    onSuccess: async (result) => {
      setMessage(`路由版本 ${result.versionNo} 已导入：${result.imported} 条`);
      setError('');
      await refresh();
    },
    onError: (failure) => onError(failure, '路由策略导入失败'),
  });
  const simulateMutation = useMutation({
    mutationFn: () => simulateRouting({
      tenantId: tenantId ? Number(tenantId) : null,
      carrier,
      prefix,
      content,
      normalizedCategory: category,
    }),
    onSuccess: (result) => {
      setSimulation(result);
      setMessage(`模拟完成：${result.targetType}/${result.targetRef}`);
      setError('');
    },
    onError: (failure) => onError(failure, '路由模拟失败'),
  });
  const circuitMutation = useMutation({
    mutationFn: () => recordCircuit(channelCode, false, 900),
    onSuccess: async (result) => {
      setMessage(`熔断状态已记录：${result.channelCode}/${result.status}`);
      setError('');
      await refresh();
    },
    onError: (failure) => onError(failure, '熔断记录失败'),
  });
  const retryMutation = useMutation({
    mutationFn: () => saveRetryPolicy({ normalizedCategory: category, retryable: category !== 'SUCCESS', delaySeconds: 30, maxAttempts: category === 'SUCCESS' ? 0 : 3, status: 'ACTIVE' }),
    onSuccess: (result) => {
      setMessage(`重试策略已保存：${result.normalizedCategory}/${result.maxAttempts}`);
      setError('');
    },
    onError: (failure) => onError(failure, '重试策略保存失败'),
  });

  if (!platformRole || (!canRead && !access.isLoading)) {
    return <p role="alert" data-testid="admin-routing-circuit-routing-policy-access-denied">无权查看路由策略。</p>;
  }

  return (
    <section className="routing-policy-page" data-testid="admin-routing-circuit-routing-policy-page">
      <nav aria-label="面包屑">通道管理 / 路由策略</nav>
      <header className="routing-policy-header">
        <div>
          <h1>路由、熔断与重试策略</h1>
          <p className="page-description">维护有序分流规则、模拟匹配结果、查看熔断状态，并维护归一化错误到重试动作的映射。</p>
        </div>
      </header>
      {message && <p role="status" data-testid="admin-routing-circuit-routing-policy-message" className="routing-policy-alert success">{message}</p>}
      {error && <p role="alert" data-testid="admin-routing-circuit-routing-policy-error" className="routing-policy-alert error">{error}</p>}

      <section className="card routing-policy-flow" data-testid="admin-routing-circuit-routing-policy-business-flow" aria-labelledby="routing-policy-flow-title">
        <div className="routing-policy-flow-heading">
          <h2 id="routing-policy-flow-title">业务流说明</h2>
          <p>页面按“规则准备 → 请求模拟 → 通道健康 → 失败动作”的发送链路组织，先确定可用路由，再结合熔断与重试策略决定最终动作。</p>
        </div>
        <ol className="routing-policy-flow-steps">
          <li data-testid="admin-routing-circuit-routing-policy-flow-import">
            <strong>1. 提交校验 / 导入有序规则</strong>
            <span>运营将规则按优先级导入版本，形成后续发送请求可匹配的候选目标。</span>
          </li>
          <li data-testid="admin-routing-circuit-routing-policy-flow-simulate">
            <strong>2. 路由匹配 / 模拟路由</strong>
            <span>技术或运营用机构、运营商、号段和内容样本验证命中规则，确认目标通道或通道组。</span>
          </li>
          <li data-testid="admin-routing-circuit-routing-policy-flow-circuit">
            <strong>3. 通道熔断 / 熔断状态</strong>
            <span>系统根据失败、成功与延迟记录判断通道是否可用，已打开熔断的通道不再进入发送候选。</span>
          </li>
          <li data-testid="admin-routing-circuit-routing-policy-flow-retry">
            <strong>4. 重试或终止 / 重试规则</strong>
            <span>归一化错误决定是否重试、延迟多久以及最多尝试次数；成功类结果不再触发重试。</span>
          </li>
        </ol>
      </section>

      <section className="card">
        <h2>导入有序规则</h2>
        <p className="routing-policy-section-help">先导入版本化规则，后续模拟和线上发送都以激活版本为准。</p>
        <div className="routing-policy-form">
          <label>版本号<input data-testid="admin-routing-circuit-routing-policy-version" value={versionNo} onChange={(event) => setVersionNo(event.target.value)} /></label>
          <label>规则 CSV<textarea data-testid="admin-routing-circuit-routing-policy-import-input" value={ruleText} onChange={(event) => setRuleText(event.target.value)} /></label>
          <button type="button" className="routing-policy-action-button" data-testid="admin-routing-circuit-routing-policy-import" disabled={!canImport} onClick={() => importMutation.mutate()}>导入策略</button>
        </div>
      </section>

      <section className="card" data-testid="admin-routing-circuit-routing-policy-simulator">
        <h2>路由模拟</h2>
        <p className="routing-policy-section-help">用发送样本预演路由结果，帮助定位规则优先级、条件和目标配置是否符合预期。</p>
        <div className="routing-policy-form">
          <label>机构<input data-testid="admin-routing-circuit-routing-policy-tenant" value={tenantId} onChange={(event) => setTenantId(event.target.value)} /></label>
          <label>运营商<input data-testid="admin-routing-circuit-routing-policy-carrier" value={carrier} onChange={(event) => setCarrier(event.target.value)} /></label>
          <label>号段<input data-testid="admin-routing-circuit-routing-policy-prefix" value={prefix} onChange={(event) => setPrefix(event.target.value)} /></label>
          <label>内容<input data-testid="admin-routing-circuit-routing-policy-content" value={content} onChange={(event) => setContent(event.target.value)} /></label>
          <button type="button" className="routing-policy-action-button" data-testid="admin-routing-circuit-routing-policy-simulate" disabled={!canRead} onClick={() => simulateMutation.mutate()}>模拟路由</button>
        </div>
        {simulation && <p data-testid="admin-routing-circuit-routing-policy-simulation-result">{simulation.explanation} / {simulation.retryPolicy.normalizedCategory}:{simulation.retryPolicy.maxAttempts}</p>}
      </section>

      <section className="card" data-testid="admin-routing-circuit-routing-circuit-state">
        <h2>熔断状态</h2>
        <p className="routing-policy-section-help">记录通道异常后刷新熔断状态，打开熔断的通道会影响路由候选结果。</p>
        <div className="routing-policy-form">
          <label>通道<input data-testid="admin-routing-circuit-routing-circuit-channel" value={channelCode} onChange={(event) => setChannelCode(event.target.value)} /></label>
          <button type="button" className="routing-policy-action-button" data-testid="admin-routing-circuit-routing-circuit-record" disabled={!canWrite} onClick={() => circuitMutation.mutate()}>记录失败</button>
        </div>
        <table className="ratio-table">
          <thead><tr><th>通道</th><th>状态</th><th>失败</th><th>成功</th><th>延迟</th><th>历史</th></tr></thead>
          <tbody>{(circuits.data ?? []).map((row) => (
            <tr key={row.id} data-testid="admin-routing-circuit-routing-circuit-row"><td>{row.channelCode}</td><td>{row.status}</td><td>{row.failureCount}</td><td>{row.successCount}</td><td>{row.latencyMs}</td><td>{row.history}</td></tr>
          ))}</tbody>
        </table>
      </section>

      <section className="card" data-testid="admin-routing-circuit-routing-retry-rules">
        <h2>重试规则</h2>
        <p className="routing-policy-section-help">将发送回执归一化为可执行动作，决定失败后是否进入延迟重试。</p>
        <div className="routing-policy-form">
          <label>归一化错误<select data-testid="admin-routing-circuit-routing-retry-category" value={category} onChange={(event) => setCategory(event.target.value)}><option value="FAILURE">FAILURE</option><option value="UNKNOWN_REVIEW_REQUIRED">UNKNOWN_REVIEW_REQUIRED</option><option value="SUCCESS">SUCCESS</option></select></label>
          <button type="button" className="routing-policy-action-button" data-testid="admin-routing-circuit-routing-retry-save" disabled={!canWrite} onClick={() => retryMutation.mutate()}>保存重试策略</button>
        </div>
      </section>

      <section className="card">
        <h2>当前规则</h2>
        <table className="ratio-table" data-testid="admin-routing-circuit-routing-policy-table">
          <thead><tr><th>优先级</th><th>条件</th><th>目标</th><th>权重</th><th>版本</th></tr></thead>
          <tbody>{(rules.data ?? []).map((row) => (
            <tr key={row.id} data-testid="admin-routing-circuit-routing-policy-row"><td>{row.priority}</td><td>{row.conditionType}:{row.conditionValue}</td><td>{row.targetType}:{row.targetRef}</td><td>{row.weight}</td><td>{row.versionNo}</td></tr>
          ))}</tbody>
        </table>
      </section>

      <section className="card" data-testid="admin-routing-circuit-routing-policy-version-history">
        <h2>版本历史</h2>
        <table className="ratio-table">
          <tbody>{(versions.data ?? []).map((row) => (
            <tr key={row.id} data-testid="admin-routing-circuit-routing-policy-version-row"><td>{row.versionNo}</td><td>{row.status}</td><td>{row.sourceName}</td></tr>
          ))}</tbody>
        </table>
      </section>
    </section>
  );
}
