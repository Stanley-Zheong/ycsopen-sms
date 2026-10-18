import { useEffect, useRef, useState } from 'react';
import {
  createTenantApiKey,
  listTenantApiKeyAudits,
  listTenantApiKeys,
  revokeTenantApiKey,
  type TenantApiKey,
  type TenantApiKeyAudit,
} from '@/api/tenantAccessApi';
import ModalDialog from '@/components/common/ModalDialog';
import { useAuthStore } from '@/store/authStore';
import '@/styles/tenant-access.css';

const DEFAULT_RATES = { perSecond: '10', perMinute: '100', perHour: '1000', perDay: '10000' };

function responseStatus(error: unknown): number | null {
  const status = (error as { response?: { status?: unknown } })?.response?.status;
  return typeof status === 'number' ? status : null;
}

function displayTime(value: string | null): string {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' }) : '—';
}

export default function TenantApiKeysPage() {
  const userType = useAuthStore((state) => state.userType);
  const roleDenied = userType === 'TENANT_USER';
  const [serverDenied, setServerDenied] = useState(false);
  const denied = roleDenied || serverDenied;
  const deniedRef = useRef(roleDenied);

  const [rows, setRows] = useState<TenantApiKey[]>([]);
  const [loading, setLoading] = useState(!roleDenied);
  const [listError, setListError] = useState(false);
  const [audits, setAudits] = useState<TenantApiKeyAudit[]>([]);
  const [auditLoading, setAuditLoading] = useState(!roleDenied);
  const [auditError, setAuditError] = useState(false);

  const [open, setOpen] = useState(false);
  const [handoff, setHandoff] = useState<string | null>(null);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [expireTime, setExpireTime] = useState('');
  const [ip, setIp] = useState('');
  const [rates, setRates] = useState(DEFAULT_RATES);
  const [success, setSuccess] = useState('');
  const [validationError, setValidationError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [createOutcomeUnknown, setCreateOutcomeUnknown] = useState(false);
  const [unresolvedCreateNames, setUnresolvedCreateNames] = useState<string[]>([]);
  const createLatch = useRef(false);

  const [revokeTarget, setRevokeTarget] = useState<TenantApiKey | null>(null);
  const [revokePending, setRevokePending] = useState(false);
  const [revokeError, setRevokeError] = useState('');
  const revokeLatch = useRef(false);
  const authorizationReadsRef = useRef(0);
  const [authorizationReadCount, setAuthorizationReadCount] = useState(0);
  const authorizationPending = loading || auditLoading || authorizationReadCount > 0;

  function beginAuthorizationRead() {
    authorizationReadsRef.current += 1;
    setAuthorizationReadCount(authorizationReadsRef.current);
  }

  function endAuthorizationRead() {
    authorizationReadsRef.current = Math.max(0, authorizationReadsRef.current - 1);
    setAuthorizationReadCount(authorizationReadsRef.current);
  }

  function enterDeniedState() {
    deniedRef.current = true;
    createLatch.current = false;
    revokeLatch.current = false;
    setServerDenied(true);
    setRows([]);
    setAudits([]);
    setOpen(false);
    setHandoff(null);
    setRevokeTarget(null);
    setListError(false);
    setAuditError(false);
    setValidationError('');
    setRevokeError('');
    setUnresolvedCreateNames([]);
    setSuccess('');
    setLoading(false);
    setAuditLoading(false);
    setSubmitting(false);
    setRevokePending(false);
  }

  const loadKeys = async (showLoading = true) => {
    if (deniedRef.current) return;
    beginAuthorizationRead();
    if (showLoading) setLoading(true);
    try {
      const loaded = (await listTenantApiKeys()) ?? [];
      if (!deniedRef.current) {
        setRows(loaded);
        setListError(false);
      }
    } catch (failure) {
      if (responseStatus(failure) === 403) enterDeniedState();
      else if (!deniedRef.current) setListError(true);
    } finally {
      setLoading(false);
      endAuthorizationRead();
    }
  };

  const loadAudits = async (showLoading = true) => {
    if (deniedRef.current) return;
    beginAuthorizationRead();
    if (showLoading) setAuditLoading(true);
    try {
      const loaded = (await listTenantApiKeyAudits()) ?? [];
      if (!deniedRef.current) {
        setAudits(loaded);
        setAuditError(false);
      }
    } catch (failure) {
      if (responseStatus(failure) === 403) enterDeniedState();
      else if (!deniedRef.current) setAuditError(true);
    } finally {
      setAuditLoading(false);
      endAuthorizationRead();
    }
  };

  const refresh = () => {
    if (denied || authorizationPending || authorizationReadsRef.current > 0) return;
    void Promise.all([loadKeys(), loadAudits()]);
  };

  useEffect(() => {
    if (roleDenied) {
      deniedRef.current = true;
      setLoading(false);
      setAuditLoading(false);
      return;
    }
    void Promise.all([loadKeys(), loadAudits()]);
  // The authenticated role is stable for the mounted console session.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roleDenied]);

  function resetCreateForm() {
    setName('');
    setDescription('');
    setExpireTime('');
    setIp('');
    setRates(DEFAULT_RATES);
    setValidationError('');
    setCreateOutcomeUnknown(false);
  }

  function closeCreate() {
    if (submitting) return;
    createLatch.current = false;
    setOpen(false);
    resetCreateForm();
  }

  const create = async () => {
    if (deniedRef.current || authorizationPending || authorizationReadsRef.current > 0
      || createLatch.current || createOutcomeUnknown) return;
    const requestedName = name.trim();
    const parsedRates = {
      perSecond: Number(rates.perSecond),
      perMinute: Number(rates.perMinute),
      perHour: Number(rates.perHour),
      perDay: Number(rates.perDay),
    };
    const positiveIntegers = Object.values(parsedRates).every((value) => Number.isInteger(value) && value > 0);
    const ordered = parsedRates.perSecond <= parsedRates.perMinute
      && parsedRates.perMinute <= parsedRates.perHour
      && parsedRates.perHour <= parsedRates.perDay;
    if (!requestedName || name.length > 64) {
      setValidationError('名称不能为空且不能超过 64 个字符。');
      return;
    }
    if (unresolvedCreateNames.includes(requestedName)) {
      setValidationError('上一次同名创建结果仍未知。请先检查列表并撤销可能已创建的密钥，或使用新名称。');
      return;
    }
    if (description.length > 255) {
      setValidationError('描述不能超过 255 个字符。');
      return;
    }
    if (!positiveIntegers || !ordered) {
      setValidationError('速率必须为正整数，并按每秒、每分钟、每小时、每天依次不减。');
      return;
    }
    if (expireTime && new Date(expireTime).getTime() <= Date.now()) {
      setValidationError('过期时间必须晚于当前时间。');
      return;
    }
    setValidationError('');
    createLatch.current = true;
    setSubmitting(true);
    try {
      const created = await createTenantApiKey({
        name: requestedName,
        description: description.trim(),
        expireTime: expireTime || null,
        ipWhitelist: ip.trim() || null,
        ...parsedRates,
      });
      if (deniedRef.current) return;
      if (!created?.appSecret) throw new Error('missing create-only secret');
      setHandoff(created.appSecret);
      setOpen(false);
      resetCreateForm();
      setSuccess(`API Key ${created.name} 已创建，请立即保存 App Secret。`);
      createLatch.current = false;
      void Promise.all([loadKeys(false), loadAudits(false)]);
    } catch (failure) {
      if (deniedRef.current) return;
      const status = responseStatus(failure);
      if (status === 403) {
        enterDeniedState();
        return;
      }
      const definiteRejection = status !== null && status >= 400 && status < 500 && status !== 408;
      if (definiteRejection) {
        createLatch.current = false;
        setValidationError('创建请求被拒绝，请检查输入后重试。');
      } else {
        setUnresolvedCreateNames((current) => current.includes(requestedName)
          ? current
          : [...current, requestedName]);
        setCreateOutcomeUnknown(true);
        setValidationError('创建结果未知，系统不会自动重试。请关闭窗口并检查列表；如出现同名密钥，请先撤销后再以新名称创建。');
        void loadKeys(false);
      }
    } finally {
      setSubmitting(false);
    }
  };

  function closeRevoke() {
    if (revokeLatch.current) return;
    setRevokeTarget(null);
    setRevokeError('');
  }

  const revoke = async () => {
    if (deniedRef.current || authorizationPending || authorizationReadsRef.current > 0
      || !revokeTarget || revokeLatch.current) return;
    revokeLatch.current = true;
    setRevokePending(true);
    setRevokeError('');
    try {
      await revokeTenantApiKey(revokeTarget.id);
      if (deniedRef.current) return;
      setSuccess(`API Key ${revokeTarget.name} 已撤销。`);
      setRevokeTarget(null);
      await Promise.all([loadKeys(false), loadAudits(false)]);
    } catch (failure) {
      if (deniedRef.current) return;
      if (responseStatus(failure) === 403) enterDeniedState();
      else setRevokeError('撤销失败，凭证状态未确认，请重试。');
    } finally {
      revokeLatch.current = false;
      setRevokePending(false);
    }
  };

  return (
    <section data-testid="tenant-tenant-access-api-keys-page">
      <div className="tenant-access-toolbar">
        <div>
          <h1 data-testid="tenant-tenant-access-api-keys-heading">HTTP API Key</h1>
          <p className="page-description">App Secret 只在创建成功后展示一次，列表仅显示掩码。</p>
        </div>
        {!denied && <div className="tenant-access-actions">
          <button type="button" className="button-secondary" data-testid="tenant-tenant-access-api-keys-refresh" disabled={authorizationPending} onClick={refresh}>刷新</button>
          <button type="button" data-testid="tenant-tenant-access-api-keys-create-dialog" disabled={authorizationPending} onClick={() => { if (!authorizationPending && authorizationReadsRef.current === 0) { resetCreateForm(); setOpen(true); } }}>创建 API Key</button>
        </div>}
      </div>

      {denied && <p className="tenant-access-alert" data-testid="tenant-tenant-access-api-keys-access-denied">当前账号无权管理 API Key。</p>}
      <div className="tenant-access-table-wrap" data-testid="data-table">
        <table className="ratio-table" data-testid="tenant-tenant-access-api-keys-table">
          <thead><tr><th>App Key</th><th>名称</th><th>状态</th><th>App Secret</th><th>有效期</th><th>最近使用</th><th>IP 白名单</th><th>限流（秒/分/时/日）</th><th>操作</th></tr></thead>
          <tbody>
            {denied && <tr data-testid="table-empty"><td colSpan={9}>无权查看凭证。</td></tr>}
            {!denied && loading && <tr data-testid="tenant-tenant-access-api-keys-loading"><td colSpan={9}>正在加载 API Key…</td></tr>}
            {!denied && !loading && listError && <tr><td colSpan={9}><span className="tenant-access-alert" data-testid="tenant-tenant-access-api-keys-error">API Key 加载失败。</span><button type="button" data-testid="tenant-tenant-access-api-keys-retry" onClick={() => void loadKeys()}>重试</button></td></tr>}
            {!denied && !loading && !listError && rows.length === 0 && <tr data-testid="table-empty"><td colSpan={9} data-testid="tenant-tenant-access-api-keys-empty">暂无 API Key</td></tr>}
            {!denied && !loading && !listError && rows.map((row) => (
              <tr key={row.id} data-testid="tenant-tenant-access-api-keys-row">
                <td title={row.appKey}>{row.appKey}</td>
                <td title={row.name}>{row.name}</td>
                <td>{row.status}</td>
                <td data-testid="tenant-tenant-access-api-keys-secret-mask">{row.appSecretMask}</td>
                <td>{displayTime(row.expireTime)}</td>
                <td data-testid="tenant-tenant-access-api-keys-last-used-time">{displayTime(row.lastUsedTime)}</td>
                <td title={row.ipWhitelist ?? undefined}>{row.ipWhitelist ?? '—'}</td>
                <td>{row.perSecond}/{row.perMinute}/{row.perHour}/{row.perDay}</td>
                <td>{row.status === 'ACTIVE' ? <button
                  type="button"
                  data-testid="tenant-tenant-access-api-keys-revoke"
                  disabled={authorizationPending}
                  onClick={() => { if (!authorizationPending && authorizationReadsRef.current === 0) { setRevokeError(''); setRevokeTarget(row); } }}
                >撤销</button> : '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <section className="tenant-access-card" data-testid="tenant-tenant-access-api-keys-audits-section">
        <h2>API Key 操作审计</h2>
        <div className="tenant-access-table-wrap" data-testid="data-table">
          <table className="ratio-table" data-testid="tenant-tenant-access-api-keys-audits-table">
            <thead><tr><th>时间</th><th>操作人</th><th>操作</th><th>凭证 ID</th><th>结果</th></tr></thead>
            <tbody>
              {denied && <tr data-testid="table-empty"><td colSpan={5}>无权查看审计记录。</td></tr>}
              {!denied && auditLoading && <tr data-testid="tenant-tenant-access-api-keys-audits-loading"><td colSpan={5}>正在加载审计记录…</td></tr>}
              {!denied && !auditLoading && auditError && <tr><td colSpan={5}><span className="tenant-access-alert" data-testid="tenant-tenant-access-api-keys-audits-error">审计记录加载失败。</span><button type="button" data-testid="tenant-tenant-access-api-keys-audits-retry" onClick={() => void loadAudits()}>重试</button></td></tr>}
              {!denied && !auditLoading && !auditError && audits.length === 0 && <tr data-testid="table-empty"><td colSpan={5} data-testid="tenant-tenant-access-api-keys-audits-empty">暂无 API Key 操作记录</td></tr>}
              {!denied && !auditLoading && !auditError && audits.map((audit) => (
                <tr key={audit.id} data-testid="tenant-tenant-access-api-keys-audits-row">
                  <td>{displayTime(audit.occurredAt)}</td><td>{audit.actor}</td><td>{audit.operation}</td><td>{audit.resourceId}</td><td>{audit.result}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
      <p role="status" aria-live="polite" data-testid="tenant-tenant-access-api-keys-status">{success}</p>
      {unresolvedCreateNames.length > 0 && <p
        role="alert"
        className="tenant-access-alert"
        data-testid="tenant-tenant-access-api-keys-create-unknown-guard"
      >API Key {unresolvedCreateNames.join('、')} 的创建结果仍未知。本页不会再次提交这些同名请求；请检查列表，若已出现请先撤销，或使用新名称。</p>}

      {open && (
        <ModalDialog labelledBy="api-key-title" onRequestClose={closeCreate} dismissible={!submitting}>
          <form data-testid="entity-form" noValidate onSubmit={(event) => { event.preventDefault(); void create(); }}>
            <h2 id="api-key-title">创建 API Key</h2>
            {validationError && <p role="alert" className="tenant-access-alert" data-testid={createOutcomeUnknown ? 'tenant-tenant-access-api-keys-create-unknown' : undefined}>{validationError}</p>}
            <label data-testid="tenant-tenant-access-api-keys-name-field">名称<input data-testid="tenant-tenant-access-api-keys-name" required maxLength={64} value={name} onChange={(event) => setName(event.target.value)} /></label>
            <label>描述<input data-testid="tenant-tenant-access-api-keys-description" maxLength={255} value={description} onChange={(event) => setDescription(event.target.value)} /></label>
            <label data-testid="tenant-tenant-access-api-keys-expiry-field">过期时间<input data-testid="tenant-tenant-access-api-keys-expiry" type="datetime-local" value={expireTime} onChange={(event) => setExpireTime(event.target.value)} /></label>
            <label data-testid="tenant-tenant-access-api-keys-ip-allow-list-field">IP 白名单<input data-testid="tenant-tenant-access-api-keys-ip-allow-list" value={ip} onChange={(event) => setIp(event.target.value)} /></label>
            <fieldset data-testid="tenant-tenant-access-api-keys-rate-policy-field">
              <legend>限流</legend>
              <span data-testid="tenant-frequency-api-api-keys-rate-limits">秒/分/时/日 {rates.perSecond}/{rates.perMinute}/{rates.perHour}/{rates.perDay}</span>
              <label>每秒<input data-testid="tenant-tenant-access-api-keys-rate-second" min="1" type="number" value={rates.perSecond} onChange={(event) => setRates({ ...rates, perSecond: event.target.value })} /></label>
              <label>每分钟<input data-testid="tenant-tenant-access-api-keys-rate-minute" min="1" type="number" value={rates.perMinute} onChange={(event) => setRates({ ...rates, perMinute: event.target.value })} /></label>
              <label>每小时<input data-testid="tenant-tenant-access-api-keys-rate-hour" min="1" type="number" value={rates.perHour} onChange={(event) => setRates({ ...rates, perHour: event.target.value })} /></label>
              <label>每天<input data-testid="tenant-tenant-access-api-keys-rate-day" min="1" type="number" value={rates.perDay} onChange={(event) => setRates({ ...rates, perDay: event.target.value })} /></label>
            </fieldset>
            <div className="tenant-access-actions">
              <button data-testid="form-cancel" className="button-secondary" type="button" disabled={submitting} onClick={closeCreate}>{createOutcomeUnknown ? '关闭并检查列表' : '取消'}</button>
              <button data-testid="form-submit" type="submit" disabled={submitting || createOutcomeUnknown}>{submitting ? '创建中…' : '创建'}</button>
            </div>
          </form>
        </ModalDialog>
      )}

      {handoff && (
        <ModalDialog labelledBy="secret-title" onRequestClose={() => undefined} dismissible={false}>
          <div data-testid="tenant-tenant-access-api-keys-secret-dialog">
            <h2 id="secret-title">App Secret（仅展示一次）</h2>
            <p>离开或刷新页面后无法恢复，请立即安全保存。</p>
            <p className="tenant-access-handoff" data-testid="tenant-tenant-access-api-keys-secret-once">{handoff}</p>
            <button data-testid="tenant-tenant-access-api-keys-secret-acknowledge" type="button" onClick={() => setHandoff(null)}>我已保存</button>
          </div>
        </ModalDialog>
      )}

      {revokeTarget && (
        <ModalDialog labelledBy="api-key-revoke-title" onRequestClose={closeRevoke} dismissible={!revokePending}>
          <div data-testid="tenant-tenant-access-api-keys-revoke-dialog">
            <h2 id="api-key-revoke-title">撤销 API Key</h2>
            <p data-testid="tenant-tenant-access-api-keys-revoke-target">目标：{revokeTarget.name}（{revokeTarget.appKey}）</p>
            <p data-testid="tenant-tenant-access-api-keys-revoke-consequence">撤销后该凭证立即停止鉴权，且不能恢复。</p>
            {revokeError && <p role="alert" className="tenant-access-alert" data-testid="tenant-tenant-access-api-keys-revoke-error">{revokeError}</p>}
            <div className="tenant-access-actions">
              <button data-testid="tenant-tenant-access-api-keys-revoke-cancel" className="button-secondary" type="button" disabled={revokePending} onClick={closeRevoke}>取消</button>
              <button data-testid="tenant-tenant-access-api-keys-revoke-confirm" className="button-danger" type="button" disabled={revokePending} onClick={() => void revoke()}>{revokePending ? '撤销中…' : '确认撤销'}</button>
            </div>
          </div>
        </ModalDialog>
      )}
    </section>
  );
}
