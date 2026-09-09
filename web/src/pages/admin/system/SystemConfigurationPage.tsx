import { useEffect, useMemo, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import axios from 'axios';
import {
  activateSystemConfiguration,
  getSystemConfiguration,
  rollbackSystemConfiguration,
  stageSystemConfiguration,
  SYSTEM_CONFIGURATION_PERMISSIONS,
  type ConfigurationHistoryItem,
  type ConfigurationSetting,
} from '@/api/systemConfiguration';
import { useIdentityAccess } from '@/pages/admin/identity/useIdentityAccess';
import { protectedQueryKey } from '@/store/authStore';

function displayTime(value: string | null): string {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '未激活';
}

function validationMessage(setting: ConfigurationSetting, value: string): string | null {
  if (setting.type === 'SECRET_REFERENCE' && value.trim() === '') return null;
  if (setting.type === 'INTEGER') {
    return /^(?:[3-9]|1\d|20)$/.test(value.trim()) ? null : '请输入 3 到 20 之间的整数';
  }
  if (setting.type === 'BOOLEAN') return value === 'true' || value === 'false' ? null : '请选择启用或停用';
  return /^env:[A-Z][A-Z0-9_]{1,63}$/.test(value) ? null : '请输入 env:UPPER_SNAKE_CASE 格式的密钥引用';
}

function mutationFailure(error: unknown): 'stale' | 'reload' | 'other' {
  if (!axios.isAxiosError(error)) return 'other';
  const response = error.response?.data as { data?: { errorCode?: string } } | undefined;
  const errorCode = response?.data?.errorCode;
  if (errorCode === 'RELOAD_REJECTED') return 'reload';
  return errorCode === 'STALE_VERSION' ? 'stale' : 'other';
}

export default function SystemConfigurationPage() {
  const access = useIdentityAccess();
  const canRead = access.can(SYSTEM_CONFIGURATION_PERMISSIONS.read);
  const canWrite = access.can(SYSTEM_CONFIGURATION_PERMISSIONS.write);
  const canActivate = access.can(SYSTEM_CONFIGURATION_PERMISSIONS.activate);
  const queryClient = useQueryClient();
  const [changes, setChanges] = useState<Record<string, string>>({});
  const [reason, setReason] = useState('');
  const [editing, setEditing] = useState<ConfigurationSetting | null>(null);
  const [editValue, setEditValue] = useState('');
  const [activateOpen, setActivateOpen] = useState(false);
  const [rollbackSource, setRollbackSource] = useState<ConfigurationHistoryItem | null>(null);
  const [rollbackReason, setRollbackReason] = useState('');
  const [stale, setStale] = useState(false);
  const [reloadError, setReloadError] = useState<string | null>(null);
  const [mutationError, setMutationError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const dialogRef = useRef<HTMLElement | null>(null);
  const returnFocusRef = useRef<HTMLElement | null>(null);
  const previouslyOpenRef = useRef(false);

  const configuration = useQuery({
    queryKey: protectedQueryKey('system-configuration'),
    queryFn: getSystemConfiguration,
    enabled: canRead,
    retry: false,
    networkMode: 'always',
  });

  const refresh = async () => {
    setStale(false);
    setReloadError(null);
    setMutationError(null);
    await configuration.refetch();
  };

  const recordMutationFailure = (error: unknown, fallback: string) => {
    const kind = mutationFailure(error);
    setStale(kind === 'stale');
    setReloadError(kind === 'reload' ? '运行时拒绝应用，当前版本保持不变。' : null);
    setMutationError(kind === 'other' ? fallback : null);
  };

  const stage = useMutation({
    mutationFn: () => stageSystemConfiguration({
      expectedActiveVersion: configuration.data!.active.version, changes, reason: reason.trim(),
    }),
    onSuccess: async (version) => {
      setChanges({});
      setMutationError(null);
      setSuccess(`草稿 v${version} 已保存`);
      await queryClient.invalidateQueries({ queryKey: protectedQueryKey('system-configuration') });
    },
    onError: (error) => {
      recordMutationFailure(error, '保存配置失败，未保存修改和变更原因已保留，请重试。');
    },
  });
  const activate = useMutation({
    mutationFn: () => activateSystemConfiguration(configuration.data!.draft!.version, {
      expectedActiveVersion: configuration.data!.active.version,
      reason: configuration.data!.draft!.reason,
    }),
    onSuccess: async (result) => {
      setActivateOpen(false);
      setReason('');
      setMutationError(null);
      setSuccess(`版本 v${result.activeVersion} 已激活，运行时状态：${result.reloadStatus}`);
      await queryClient.invalidateQueries({ queryKey: protectedQueryKey('system-configuration') });
    },
    onError: (error) => {
      recordMutationFailure(error, '激活配置失败，草稿保持不变，请重试。');
    },
  });
  const rollback = useMutation({
    mutationFn: () => rollbackSystemConfiguration(rollbackSource!.version, {
      expectedActiveVersion: configuration.data!.active.version, reason: rollbackReason.trim(),
    }),
    onSuccess: async (result) => {
      setRollbackSource(null);
      setRollbackReason('');
      setMutationError(null);
      setSuccess(`已创建并激活回滚版本 v${result.activeVersion}`);
      await queryClient.invalidateQueries({ queryKey: protectedQueryKey('system-configuration') });
    },
    onError: (error) => {
      recordMutationFailure(error, '回滚配置失败，回滚原因已保留，请重试。');
    },
  });

  const editError = editing ? validationMessage(editing, editValue) : null;
  const rejected = useMemo(() => configuration.data?.history.find((item) =>
    item.status === 'RELOAD_REJECTED'), [configuration.data?.history]);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return undefined;
    const focusable = () => Array.from(dialog.querySelectorAll<HTMLElement>(
      'button:not(:disabled), input:not(:disabled), select:not(:disabled), [tabindex]:not([tabindex="-1"])',
    ));
    focusable()[0]?.focus();
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        setEditing(null);
        setActivateOpen(false);
        setRollbackSource(null);
        requestAnimationFrame(() => returnFocusRef.current?.focus());
      }
      if (event.key === 'Tab') {
        const elements = focusable();
        if (!elements.length) return;
        const first = elements[0];
        const last = elements[elements.length - 1];
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault();
          last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault();
          first.focus();
        }
      }
    };
    dialog.addEventListener('keydown', onKeyDown);
    return () => dialog.removeEventListener('keydown', onKeyDown);
  }, [editing, activateOpen, rollbackSource]);

  useEffect(() => {
    const open = Boolean(editing || activateOpen || rollbackSource);
    if (previouslyOpenRef.current && !open) {
      requestAnimationFrame(() => returnFocusRef.current?.focus());
    }
    previouslyOpenRef.current = open;
  }, [editing, activateOpen, rollbackSource]);

  if (access.isLoading) return <p data-testid="admin-platform-system-configuration-access-loading">正在检查系统配置权限…</p>;
  if (access.isError) return <p role="alert" data-testid="admin-platform-system-configuration-access-error">系统配置权限加载失败，请重新登录后重试。</p>;
  if (!canRead) return <p role="alert" data-testid="admin-platform-system-configuration-access-denied">无权查看系统配置。</p>;

  const data = configuration.data;
  const changedKeys = Object.keys(changes);

  function openEditor(setting: ConfigurationSetting, trigger: HTMLElement) {
    returnFocusRef.current = trigger;
    setEditing(setting);
    setEditValue(setting.sensitive ? '' : (changes[setting.key] ?? setting.value));
  }

  function saveEditor() {
    if (!editing || editError) return;
    setChanges((current) => {
      const next = { ...current };
      const value = editValue.trim();
      if ((editing.sensitive && !value) || (!editing.sensitive && value === editing.value)) delete next[editing.key];
      else next[editing.key] = value;
      return next;
    });
    setEditing(null);
  }

  return (
    <section data-testid="admin-platform-system-configuration-page">
      <nav aria-label="面包屑" data-testid="admin-platform-system-configuration-breadcrumb">系统管理 / 系统配置</nav>
      <div className="configuration-header">
        <div><h1 data-testid="admin-platform-system-configuration-heading">系统配置</h1><p className="page-description">管理受类型和版本保护的平台运行时配置。</p></div>
        <button data-testid="admin-platform-system-configuration-refresh" type="button" onClick={() => void refresh()} disabled={configuration.isFetching}>刷新</button>
      </div>

      {configuration.isLoading && <p data-testid="admin-platform-system-configuration-loading">正在加载系统配置…</p>}
      {(configuration.isError || mutationError) && <div className="card configuration-alert error" role="alert" data-testid="admin-platform-system-configuration-error"><p>{mutationError ?? '系统配置加载失败。'}</p>{configuration.isError && <button data-testid="admin-platform-system-configuration-retry" type="button" onClick={() => void refresh()}>重试加载</button>}</div>}
      {stale && <div className="card configuration-alert error" role="alert" data-testid="admin-platform-system-configuration-stale-alert">配置已被其他管理员更新，请刷新后重试。</div>}
      {(reloadError || rejected) && <div className="card configuration-alert error" role="alert" data-testid="admin-platform-system-configuration-reload-error">{reloadError ?? `版本 v${rejected!.version} 未应用：${rejected!.reloadErrorCode ?? 'RELOAD_REJECTED'}`}</div>}
      {success && <p className="configuration-alert success" role="status" data-testid="admin-platform-system-configuration-success-status">{success}</p>}

      {data && <>
        {!canWrite && <p className="card" data-testid="admin-platform-system-configuration-write-denied">当前账号仅可查看，不能编辑或保存草稿。</p>}
        {!canActivate && <p className="card" data-testid="admin-platform-system-configuration-activate-denied">当前账号不能激活或回滚配置。</p>}

        <section className="card" data-testid="admin-platform-system-configuration-active-card">
          <h2>当前生效版本</h2>
          <dl className="configuration-summary">
            <div><dt>版本</dt><dd data-testid="admin-platform-system-configuration-active-version">v{data.active.version}</dd></div>
            <div><dt>校验和</dt><dd data-testid="admin-platform-system-configuration-active-checksum">{data.active.checksum.slice(0, 12)}</dd></div>
            <div><dt>激活人</dt><dd data-testid="admin-platform-system-configuration-active-actor">{data.active.actor ?? '系统默认值'}</dd></div>
            <div><dt>激活时间</dt><dd data-testid="admin-platform-system-configuration-active-time">{displayTime(data.active.activatedAt)}</dd></div>
            <div><dt>运行时</dt><dd data-testid="admin-platform-system-configuration-reload-status">{data.active.reloadStatus}</dd></div>
          </dl>
        </section>

        <section className="card configuration-table-wrap">
          <h2>已注册配置</h2>
          <table className="ratio-table" data-testid="admin-platform-system-configuration-settings-table">
            <caption className="visually-hidden">类型化系统配置</caption>
            <thead><tr><th>配置键</th><th>名称</th><th>类型</th><th>校验</th><th>敏感级别</th><th>默认值</th><th>当前/草稿值</th><th>操作</th></tr></thead>
            <tbody>{data.settings.map((setting) => <tr key={setting.key} data-setting-key={setting.key} data-testid="admin-platform-system-configuration-setting-row">
              <td data-testid="admin-platform-system-configuration-setting-key">{setting.key}</td>
              <td data-testid="admin-platform-system-configuration-setting-label">{setting.label}</td>
              <td data-testid="admin-platform-system-configuration-setting-type">{setting.type}</td>
              <td data-testid="admin-platform-system-configuration-setting-validation">{setting.validation}</td>
              <td data-testid="admin-platform-system-configuration-setting-sensitivity">{setting.sensitive ? '密钥引用' : '普通'}</td>
              <td data-testid="admin-platform-system-configuration-setting-default">{setting.defaultValue}</td>
              <td data-testid="admin-platform-system-configuration-setting-value">{changes[setting.key] ?? setting.value}</td>
              <td>{canWrite && <button type="button" data-testid="admin-platform-system-configuration-edit-open" onClick={(event) => openEditor(setting, event.currentTarget)}>编辑</button>}</td>
            </tr>)}</tbody>
          </table>
        </section>

        <section className="card" data-testid="admin-platform-system-configuration-draft-card">
          <div className="configuration-header"><h2>变更草稿</h2><strong data-testid="admin-platform-system-configuration-draft-status">{changedKeys.length ? `${changedKeys.length} 项未保存修改` : data.draft ? `草稿 v${data.draft.version} 已保存` : '无未保存修改'}</strong></div>
          {canWrite && <div className="configuration-draft-actions"><label>变更原因<input data-testid="admin-platform-system-configuration-draft-reason" value={reason} maxLength={256} onChange={(event) => setReason(event.target.value)} /></label><button className="button-secondary" type="button" data-testid="admin-platform-system-configuration-draft-discard" disabled={!changedKeys.length} onClick={() => { setChanges({}); setReason(''); }}>放弃未保存修改</button><button type="button" data-testid="admin-platform-system-configuration-draft-save" disabled={!changedKeys.length || !reason.trim() || stage.isPending} onClick={() => stage.mutate()}>保存草稿</button></div>}
          {canActivate && <button type="button" data-testid="admin-platform-system-configuration-activate-open" disabled={!data.draft || activate.isPending} onClick={(event) => { returnFocusRef.current = event.currentTarget; setActivateOpen(true); }}>激活已保存草稿</button>}
        </section>

        <section className="card configuration-table-wrap"><h2>版本历史</h2>
          <table className="ratio-table" data-testid="admin-platform-system-configuration-history-table"><caption className="visually-hidden">不可变配置版本</caption><thead><tr><th>版本</th><th>状态</th><th>操作人</th><th>原因</th><th>时间</th><th>变更键</th><th>操作</th></tr></thead><tbody>
            {data.history.map((item) => <tr key={item.version} data-version-id={item.version} data-testid="admin-platform-system-configuration-history-row">
              <td data-testid="admin-platform-system-configuration-history-version">v{item.version}</td><td data-testid="admin-platform-system-configuration-history-status">{item.status}</td><td data-testid="admin-platform-system-configuration-history-actor">{item.actor}</td><td data-testid="admin-platform-system-configuration-history-reason">{item.reason}</td><td data-testid="admin-platform-system-configuration-history-time">{displayTime(item.activatedAt ?? item.createdAt)}</td><td data-testid="admin-platform-system-configuration-history-changed-keys">{item.changedKeys.join(', ') || '系统默认值'}</td><td>{canActivate && item.status === 'SUPERSEDED' && <button type="button" data-testid="admin-platform-system-configuration-rollback-open" onClick={(event) => { returnFocusRef.current = event.currentTarget; setRollbackSource(item); }}>回滚到此版本</button>}</td>
            </tr>)}
            {!data.history.length && <tr data-testid="admin-platform-system-configuration-history-empty"><td colSpan={7}>暂无配置版本</td></tr>}
          </tbody></table>
        </section>
      </>}

      {editing && <section ref={dialogRef} className="card" role="dialog" aria-modal="true" aria-labelledby="configuration-edit-title" data-testid="admin-platform-system-configuration-edit-dialog"><h2 id="configuration-edit-title">编辑 {editing.label}</h2><label>配置值{editing.type === 'BOOLEAN' ? <select data-testid="admin-platform-system-configuration-edit-input" value={editValue} onChange={(event) => setEditValue(event.target.value)}><option value="true">启用</option><option value="false">停用</option></select> : <input data-testid="admin-platform-system-configuration-edit-input" type={editing.type === 'INTEGER' ? 'number' : 'text'} value={editValue} placeholder={editing.sensitive ? '留空表示不修改密钥引用' : undefined} onChange={(event) => setEditValue(event.target.value)} />}</label>{editError && <p role="alert" data-testid="admin-platform-system-configuration-validation-error">{editError}</p>}<div className="configuration-dialog-actions"><button className="button-secondary" type="button" data-testid="admin-platform-system-configuration-edit-cancel" onClick={() => setEditing(null)}>取消</button><button type="button" data-testid="admin-platform-system-configuration-edit-save" disabled={Boolean(editError)} onClick={saveEditor}>应用到草稿</button></div></section>}

      {activateOpen && data?.draft && <section ref={dialogRef} className="card" role="dialog" aria-modal="true" aria-labelledby="configuration-activate-title" data-testid="admin-platform-system-configuration-activate-dialog"><h2 id="configuration-activate-title">激活草稿 v{data.draft.version}</h2><p>将原子应用 {data.draft.changedKeys.join(', ')}。失败时当前版本保持不变。</p><div className="configuration-dialog-actions"><button className="button-secondary" type="button" data-testid="admin-platform-system-configuration-activate-cancel" onClick={() => setActivateOpen(false)}>取消</button><button type="button" data-testid="admin-platform-system-configuration-activate-confirm" disabled={activate.isPending} onClick={() => activate.mutate()}>确认激活</button></div></section>}

      {rollbackSource && <section ref={dialogRef} className="card" role="dialog" aria-modal="true" aria-labelledby="configuration-rollback-title" data-testid="admin-platform-system-configuration-rollback-dialog"><h2 id="configuration-rollback-title">根据 v{rollbackSource.version} 创建回滚版本</h2><label>回滚原因<input data-testid="admin-platform-system-configuration-rollback-reason" value={rollbackReason} maxLength={256} onChange={(event) => setRollbackReason(event.target.value)} /></label><div className="configuration-dialog-actions"><button className="button-secondary" type="button" data-testid="admin-platform-system-configuration-rollback-cancel" onClick={() => { setRollbackSource(null); setRollbackReason(''); }}>取消</button><button type="button" data-testid="admin-platform-system-configuration-rollback-confirm" disabled={!rollbackReason.trim() || rollback.isPending} onClick={() => rollback.mutate()}>确认创建并激活</button></div></section>}
    </section>
  );
}
