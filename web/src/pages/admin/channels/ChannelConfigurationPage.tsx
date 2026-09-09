import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import ModalDialog from '@/components/common/ModalDialog';
import {
  activateChannelConfiguration,
  listChannelConfigurations,
  listChannelDependencies,
  migrateChannelDependencies,
  offlineChannel,
  retryChannelActivation,
  saveChannelConfiguration,
  testChannelConnectivity,
  type ChannelActivationResult,
  type ChannelConfiguration,
  type ChannelConfigurationRequest,
  type ChannelDependencyView,
  type ChannelOperator,
  type ChannelProtocol,
} from '@/api/channelConfigurationApi';
import { mutationErrorMessage } from '@/api/client';
import { isPlatformRole, useAuthStore } from '@/store/authStore';
import '@/styles/channel-configuration.css';

const protocols: ChannelProtocol[] = ['CMPP', 'SGIP', 'SMGP', 'HTTP'];
const operators: ChannelOperator[] = ['MOBILE', 'UNICOM', 'TELECOM', 'VIRTUAL', 'INTERNATIONAL'];

interface FormState {
  id?: number;
  channelName: string;
  protocol: ChannelProtocol;
  operator: ChannelOperator;
  host: string;
  port: string;
  account: string;
  password: string;
  spId: string;
  serviceId: string;
  srcId: string;
  maxConnections: string;
  windowSize: string;
  tpsLimit: string;
  price: string;
  priority: string;
  activeWindow: string;
  extraConfig: string;
  availability: string;
}

const emptyForm: FormState = {
  channelName: '',
  protocol: 'CMPP',
  operator: 'MOBILE',
  host: '127.0.0.1',
  port: '7890',
  account: '',
  password: '',
  spId: '',
  serviceId: '',
  srcId: '',
  maxConnections: '4',
  windowSize: '8',
  tpsLimit: '100',
  price: '0.0000',
  priority: '50',
  activeWindow: '00:00-23:59',
  extraConfig: '{}',
  availability: 'AVAILABLE',
};

function fromChannel(row: ChannelConfiguration): FormState {
  return {
    id: row.id,
    channelName: row.name,
    protocol: row.protocol,
    operator: row.operator,
    host: row.host,
    port: String(row.port),
    account: '',
    password: '',
    spId: row.spId ?? '',
    serviceId: row.serviceId ?? '',
    srcId: row.srcId ?? '',
    maxConnections: String(row.maxConnections),
    windowSize: String(row.windowSize),
    tpsLimit: String(row.tpsLimit),
    price: row.price,
    priority: String(row.priority),
    activeWindow: row.activeWindow ?? '',
    extraConfig: JSON.stringify(row.extraConfig ?? {}),
    availability: row.availability,
  };
}

function toRequest(form: FormState): ChannelConfigurationRequest {
  return {
    name: form.channelName.trim(),
    protocol: form.protocol,
    operator: form.operator,
    host: form.host.trim(),
    port: Number(form.port),
    account: form.account,
    password: form.password,
    spId: form.spId.trim() || null,
    serviceId: form.serviceId.trim() || null,
    srcId: form.srcId.trim() || null,
    maxConnections: Number(form.maxConnections),
    windowSize: Number(form.windowSize),
    tpsLimit: Number(form.tpsLimit),
    price: form.price.trim(),
    priority: form.priority.trim() ? Number(form.priority) : null,
    activeWindow: form.activeWindow.trim() || null,
    extraConfig: JSON.parse(form.extraConfig.trim() || '{}') as Record<string, unknown>,
    availability: form.availability.trim() || 'AVAILABLE',
  };
}

function validate(form: FormState): string | null {
  if (!form.channelName.trim() || form.channelName.trim().length > 50) return '通道名称必填且不能超过 50 个字符。';
  if (!form.host.trim() || Number(form.port) < 1 || Number(form.port) > 65_535) return '端点地址或端口不合法。';
  if (!form.id && (!form.account.trim() || !form.password.trim())) return '新建通道必须填写账号和密码。';
  if (Number(form.maxConnections) < 1 || Number(form.windowSize) < 1 || Number(form.tpsLimit) < 1) return '连接数、窗口和 TPS 必须为正整数。';
  if (!/^\d+(\.\d{1,4})?$/.test(form.price.trim())) return '单价必须是最多四位小数的非负数字。';
  const priority = form.priority.trim() ? Number(form.priority) : 50;
  if (!Number.isInteger(priority) || priority < 1 || priority > 100) return '优先级必须是 1-100 的整数。';
  try {
    JSON.parse(form.extraConfig.trim() || '{}');
  } catch {
    return '扩展配置必须是合法 JSON。';
  }
  return null;
}

function displayTime(value: string | null) {
  return value ? new Date(value).toLocaleString() : '未记录';
}

function displayPrice(value: string | number) {
  const numeric = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(numeric) ? numeric.toFixed(4) : String(value);
}

export default function ChannelConfigurationPage() {
  const queryClient = useQueryClient();
  const userType = useAuthStore((state) => state.userType);
  const canRead = userType === 'ADMIN' || userType === 'OPERATOR';
  const canOperate = canRead;
  const [form, setForm] = useState<FormState | null>(null);
  const [dependenciesFor, setDependenciesFor] = useState<ChannelConfiguration | null>(null);
  const [offlineTarget, setOfflineTarget] = useState<ChannelConfiguration | null>(null);
  const [dependencyView, setDependencyView] = useState<ChannelDependencyView | null>(null);
  const [activationResult, setActivationResult] = useState<ChannelActivationResult | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [destinations, setDestinations] = useState<Record<string, string>>({});

  const channels = useQuery({
    queryKey: ['channel-configurations'],
    queryFn: listChannelConfigurations,
    retry: false,
    enabled: isPlatformRole(userType) && canRead,
  });

  const rows = useMemo(() => channels.data ?? [], [channels.data]);
  const formError = form ? validate(form) : null;
  const targetOptions = useMemo(
    () => rows.filter((row) => row.status !== 'OFFLINE' && row.id !== dependenciesFor?.id),
    [rows, dependenciesFor],
  );

  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: ['channel-configurations'] });
  };

  const save = useMutation({
    mutationFn: async () => {
      if (!form || formError) throw new Error(formError ?? '表单不完整');
      return saveChannelConfiguration(toRequest(form), form.id);
    },
    onSuccess: async () => {
      setForm(null);
      setMessage('通道配置已保存，凭证已受保护。');
      setError(null);
      await refresh();
    },
    onError: (failure) => setError(mutationErrorMessage(failure, '保存通道配置失败')),
  });

  const connectivity = useMutation({
    mutationFn: (id: number) => testChannelConnectivity(id),
    onSuccess: (result) => {
      setMessage(result.passed ? '连接性校验通过。' : `连接性校验未通过：${result.reasonCode}`);
      setError(result.passed ? null : `连接性校验未通过：${result.reasonCode}`);
    },
    onError: (failure) => setError(mutationErrorMessage(failure, '连接性校验失败')),
  });

  const activate = useMutation({
    mutationFn: (row: ChannelConfiguration) => activateChannelConfiguration(row.id, row.effectiveVersion),
    onSuccess: async (result) => {
      setActivationResult(result);
      setMessage(result.resultCode === 'EFFECTIVE' || result.resultCode === 'APPLIED'
        ? `版本 v${result.effectiveVersion} 已生效。`
        : `版本未生效：${result.safeReason}`);
      await refresh();
    },
    onError: (failure) => setError(mutationErrorMessage(failure, '激活通道配置失败')),
  });

  const retry = useMutation({
    mutationFn: (result: ChannelActivationResult) => retryChannelActivation(result.channelId, result.requestedVersion ?? 0),
    onSuccess: async (result) => {
      setActivationResult(result);
      await refresh();
    },
    onError: (failure) => setError(mutationErrorMessage(failure, '重试激活失败')),
  });

  const loadDependencies = useMutation({
    mutationFn: (row: ChannelConfiguration) => listChannelDependencies(row.id),
    onSuccess: (view) => {
      setDependencyView(view);
      setDestinations(Object.fromEntries(view.dependencies.map((item) => [`${item.source}:${item.referenceId}`, ''])));
    },
    onError: (failure) => setError(mutationErrorMessage(failure, '依赖预览加载失败')),
  });

  const migrate = useMutation({
    mutationFn: async () => {
      if (!dependenciesFor || !dependencyView) throw new Error('依赖信息不存在');
      return migrateChannelDependencies(dependenciesFor.id, {
        items: dependencyView.dependencies.map((item) => ({
          source: item.source,
          referenceId: item.referenceId,
          destinationChannelId: destinations[`${item.source}:${item.referenceId}`]
            ? Number(destinations[`${item.source}:${item.referenceId}`])
            : null,
        })),
      });
    },
    onSuccess: (view) => {
      setDependencyView(view);
      setMessage(view.unresolvedCount === 0 ? '依赖已迁移，可以下线通道。' : '依赖尚未全部迁移。');
    },
    onError: (failure) => setError(mutationErrorMessage(failure, '迁移依赖失败')),
  });

  const takeOffline = useMutation({
    mutationFn: (row: ChannelConfiguration) => offlineChannel(row.id),
    onSuccess: async (result) => {
      if (!result.changed) {
        setMessage(null);
        setError(`通道仍有 ${result.unresolvedDependencies.length} 个关联依赖，不能下线。`);
        return;
      }
      setOfflineTarget(null);
      setMessage('通道已下线。');
      setError(null);
      await refresh();
    },
    onError: (failure) => setError(mutationErrorMessage(failure, '通道仍有关联依赖，不能下线')),
  });

  if (!isPlatformRole(userType) || !canRead) {
    return <p role="alert" data-testid="admin-channel-configuration-channel-configuration-access-denied">无权查看通道配置。</p>;
  }

  return (
    <section data-testid="admin-channel-configuration-channel-configuration-page">
      <nav aria-label="面包屑">通道管理 / 通道配置</nav>
      <header className="channel-configuration-header">
        <div>
          <h1>通道配置</h1>
          <p className="page-description">维护上游通道配置、版本激活和下线依赖。</p>
        </div>
        <div className="channel-configuration-toolbar">
          <button type="button" data-testid="admin-channel-configuration-refresh" className="button-secondary" onClick={() => void refresh()} disabled={channels.isFetching}>刷新</button>
          {canOperate && <button type="button" data-testid="admin-channel-configuration-channel-create-open" onClick={() => setForm(emptyForm)}>新建通道</button>}
        </div>
      </header>

      {message && <p role="status" className="channel-configuration-alert success">{message}</p>}
      {error && <div role="alert" className="channel-configuration-alert error">{error}</div>}
      {activationResult && (
        <section className="card" data-testid="admin-channel-configuration-channel-configuration-activation-result">
          <h2>激活结果</h2>
          <dl className="channel-configuration-summary">
            <div><dt>状态</dt><dd>{activationResult.resultCode}</dd></div>
            <div><dt>目标版本</dt><dd>{activationResult.requestedVersion ?? '无'}</dd></div>
            <div><dt>当前版本</dt><dd>{activationResult.effectiveVersion ?? '无'}</dd></div>
            <div><dt>原因</dt><dd>{activationResult.safeReason}</dd></div>
          </dl>
          {activationResult.retryable && (
            <button type="button" onClick={() => retry.mutate(activationResult)} disabled={retry.isPending || !activationResult.retryable || activationResult.requestedVersion == null}>重试激活</button>
          )}
        </section>
      )}

      <section className="card channel-configuration-table">
        {channels.isLoading && <p>正在加载通道配置…</p>}
        {channels.isError && <p role="alert">通道配置加载失败。<button type="button" data-testid="admin-channel-configuration-load-retry" onClick={() => void refresh()}>重试</button></p>}
        {!channels.isLoading && !channels.isError && rows.length === 0 && <p>暂无通道配置。</p>}
        {rows.length > 0 && (
          <table className="ratio-table">
            <thead>
              <tr>
                <th>名称</th>
                <th>协议</th>
                <th>运营商</th>
                <th>端点</th>
                <th>版本</th>
                <th>单价</th>
                <th>优先级</th>
                <th>可用性</th>
                <th>状态</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => {
                const isOffline = row.status === 'OFFLINE';
                return (
                  <tr key={row.id} data-testid="admin-channel-configuration-channel-row" data-business-key={row.id}>
                    <td>{row.name}</td>
                    <td>{row.protocol}</td>
                    <td>{row.operator}</td>
                    <td>{row.host}:{row.port}</td>
                    <td>v{row.effectiveVersion ?? 0}</td>
                    <td data-testid="admin-channel-configuration-channel-configuration-price">{displayPrice(row.price)}</td>
                    <td>{row.priority}</td>
                    <td>{row.availability}</td>
                    <td>{row.status}</td>
                    <td>{displayTime(row.updatedAt)}</td>
                    <td>
                      <div className="channel-configuration-row-actions">
                        <button type="button" data-testid="admin-channel-configuration-channel-edit" className="button-secondary" onClick={() => setForm(fromChannel(row))} disabled={isOffline}>编辑</button>
                        <button type="button" data-testid="admin-channel-configuration-channel-configuration-connectivity-test" onClick={() => connectivity.mutate(row.id)} disabled={connectivity.isPending || isOffline}>测试</button>
                        <button type="button" data-testid="admin-channel-configuration-channel-activate" onClick={() => activate.mutate(row)} disabled={activate.isPending || isOffline}>激活</button>
                        <button type="button" data-testid="admin-channel-configuration-channel-dependency-preview" onClick={() => { setDependenciesFor(row); loadDependencies.mutate(row); }}>依赖</button>
                        <button type="button" data-testid="admin-channel-configuration-channel-offline" onClick={() => setOfflineTarget(row)} disabled={isOffline}>下线</button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </section>

      {form && (
        <ModalDialog labelledBy="channel-configuration-form-title" onRequestClose={() => setForm(null)}>
          <h2 id="channel-configuration-form-title">{form.id ? '编辑通道配置' : '新建通道配置'}</h2>
          <div className="channel-configuration-grid">
            <label>通道名称<input data-testid="admin-channel-configuration-channel-form-name" value={form.channelName} maxLength={50} onChange={(event) => setForm({ ...form, channelName: event.target.value })} /></label>
            <label>协议<select data-testid="admin-channel-configuration-channel-form-protocol" value={form.protocol} onChange={(event) => setForm({ ...form, protocol: event.target.value as ChannelProtocol })}>{protocols.map((protocol) => <option key={protocol} value={protocol}>{protocol}</option>)}</select></label>
            <label>运营商<select data-testid="admin-channel-configuration-channel-form-operator" value={form.operator} onChange={(event) => setForm({ ...form, operator: event.target.value as ChannelOperator })}>{operators.map((operator) => <option key={operator} value={operator}>{operator}</option>)}</select></label>
            <label data-testid="admin-channel-configuration-channel-form-endpoint">主机<input data-testid="admin-channel-configuration-channel-form-host" value={form.host} onChange={(event) => setForm({ ...form, host: event.target.value })} /></label>
            <label>端口<input data-testid="admin-channel-configuration-channel-form-port" type="number" value={form.port} onChange={(event) => setForm({ ...form, port: event.target.value })} /></label>
            <label data-testid="admin-channel-configuration-channel-form-credential">账号<input data-testid="admin-channel-configuration-channel-form-account" value={form.account} placeholder={form.id ? '留空表示不修改' : undefined} onChange={(event) => setForm({ ...form, account: event.target.value })} /></label>
            <label>密码<input data-testid="admin-channel-configuration-channel-form-password" type="password" value={form.password} placeholder={form.id ? '留空表示不修改' : undefined} onChange={(event) => setForm({ ...form, password: event.target.value })} /></label>
            <label>SPID<input data-testid="admin-channel-configuration-channel-form-sp-id" value={form.spId} onChange={(event) => setForm({ ...form, spId: event.target.value })} /></label>
            <label>Service ID<input data-testid="admin-channel-configuration-channel-form-service-id" value={form.serviceId} onChange={(event) => setForm({ ...form, serviceId: event.target.value })} /></label>
            <label>源号码<input data-testid="admin-channel-configuration-channel-form-src-id" value={form.srcId} onChange={(event) => setForm({ ...form, srcId: event.target.value })} /></label>
            <label data-testid="admin-channel-configuration-channel-form-connection">最大连接<input data-testid="admin-channel-configuration-channel-form-max-connections" type="number" value={form.maxConnections} onChange={(event) => setForm({ ...form, maxConnections: event.target.value })} /></label>
            <label>窗口<input data-testid="admin-channel-configuration-channel-form-window-size" type="number" value={form.windowSize} onChange={(event) => setForm({ ...form, windowSize: event.target.value })} /></label>
            <label>TPS<input data-testid="admin-channel-configuration-channel-form-tps-limit" type="number" value={form.tpsLimit} onChange={(event) => setForm({ ...form, tpsLimit: event.target.value })} /></label>
            <label>单价<input data-testid="admin-channel-configuration-channel-form-price" value={form.price} onChange={(event) => setForm({ ...form, price: event.target.value })} /></label>
            <label>优先级<input data-testid="admin-channel-configuration-channel-form-priority" type="number" value={form.priority} onChange={(event) => setForm({ ...form, priority: event.target.value })} /></label>
            <label>可用性<input data-testid="admin-channel-configuration-channel-form-availability" value={form.availability} onChange={(event) => setForm({ ...form, availability: event.target.value })} /></label>
            <label>生效窗口<input data-testid="admin-channel-configuration-channel-form-active-window" value={form.activeWindow} onChange={(event) => setForm({ ...form, activeWindow: event.target.value })} /></label>
            <label className="channel-configuration-wide">扩展 JSON<input data-testid="admin-channel-configuration-channel-form-extra-config" value={form.extraConfig} onChange={(event) => setForm({ ...form, extraConfig: event.target.value })} /></label>
          </div>
          {formError && <p role="alert" className="channel-configuration-alert error">{formError}</p>}
          <div className="channel-configuration-dialog-actions">
            <button type="button" data-testid="admin-channel-configuration-channel-form-cancel" className="button-secondary" onClick={() => setForm(null)}>取消</button>
            <button type="button" data-testid="admin-channel-configuration-channel-form-save" onClick={() => save.mutate()} disabled={Boolean(formError) || save.isPending}>保存</button>
          </div>
        </ModalDialog>
      )}

      {dependenciesFor && (
        <ModalDialog labelledBy="channel-dependency-title" onRequestClose={() => { setDependenciesFor(null); setDependencyView(null); }}>
          <h2 id="channel-dependency-title">依赖迁移</h2>
          <div data-testid="admin-channel-configuration-channel-migration-wizard">
            {loadDependencies.isPending && <p>正在加载依赖…</p>}
            {dependencyView && (
              <>
                <p>未解决依赖：{dependencyView.unresolvedCount}</p>
                <dl className="channel-configuration-dependency-list">
                  {dependencyView.dependencies.map((item) => {
                    const dependencyKey = `${item.source}:${item.referenceId}`;
                    return (
                    <div key={dependencyKey} className="channel-configuration-dependency-item">
                      <div>
                        <dt>{item.source}</dt>
                        <dd>{item.referenceId}</dd>
                      </div>
                      <label>迁移到<select data-testid="admin-channel-configuration-channel-migration-target" value={destinations[dependencyKey] ?? ''} onChange={(event) => setDestinations({ ...destinations, [dependencyKey]: event.target.value })}><option value="">未选择</option>{targetOptions.map((row) => <option key={row.id} value={row.id}>{row.name}</option>)}</select></label>
                      <span>{item.state}</span>
                    </div>
                    );
                  })}
                </dl>
                {!dependencyView.dependencies.length && <p>当前通道没有依赖。</p>}
              </>
            )}
          </div>
          <div className="channel-configuration-dialog-actions">
            <button type="button" data-testid="admin-channel-configuration-channel-migration-close" className="button-secondary" onClick={() => { setDependenciesFor(null); setDependencyView(null); }}>关闭</button>
            <button type="button" data-testid="admin-channel-configuration-channel-migration-submit" onClick={() => migrate.mutate()} disabled={!dependencyView || migrate.isPending}>迁移依赖</button>
          </div>
        </ModalDialog>
      )}

      {offlineTarget && (
        <ModalDialog labelledBy="channel-offline-title" onRequestClose={() => setOfflineTarget(null)}>
          <h2 id="channel-offline-title">确认下线通道</h2>
          <p>下线前会重新检查依赖。仍有依赖时，服务端会阻断该操作。</p>
          <div className="channel-configuration-dialog-actions">
            <button type="button" data-testid="admin-channel-configuration-channel-offline-cancel" className="button-secondary" onClick={() => setOfflineTarget(null)}>取消</button>
            <button type="button" data-testid="admin-channel-configuration-channel-offline-confirm" onClick={() => takeOffline.mutate(offlineTarget)} disabled={takeOffline.isPending}>确认下线</button>
          </div>
        </ModalDialog>
      )}
    </section>
  );
}
