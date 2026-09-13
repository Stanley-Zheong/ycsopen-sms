import { useEffect, useState } from 'react';
import {
  createTenantApiKey,
  listTenantApiKeys,
  revokeTenantApiKey,
  type TenantApiKey,
} from '@/api/tenantAccessApi';
import ModalDialog from '@/components/common/ModalDialog';
import '@/styles/tenant-access.css';

const DEFAULT_RATES = { perSecond: '10', perMinute: '100', perHour: '1000', perDay: '10000' };

export default function TenantApiKeysPage() {
  const [rows, setRows] = useState<TenantApiKey[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
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

  const load = async () => {
    setLoading(true);
    try {
      setRows((await listTenantApiKeys()) ?? []);
      setError(false);
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const create = async () => {
    if (submitting) return;
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
    if (!name.trim() || name.length > 64) {
      setValidationError('名称不能为空且不能超过 64 个字符。');
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
    setSubmitting(true);
    try {
      const created = await createTenantApiKey({
        name,
        description,
        expireTime: expireTime || null,
        ipWhitelist: ip || null,
        ...parsedRates,
      });
      setOpen(false);
      setHandoff(created?.secret ?? null);
      await load();
    } catch {
      setValidationError('创建 API Key 失败，请检查输入后重试。');
    } finally {
      setSubmitting(false);
    }
  };

  if (error) {
    return (
      <section data-testid="tenant-tenant-access-api-keys-page">
        <p className="tenant-access-alert">加载失败 <button onClick={() => void load()}>重试</button></p>
      </section>
    );
  }

  return (
    <section data-testid="tenant-tenant-access-api-keys-page">
      <div className="tenant-access-toolbar">
        <div>
          <h1>HTTP API Key</h1>
          <p className="page-description">密钥只在创建成功时展示一次。</p>
        </div>
        <button type="button" data-testid="tenant-tenant-access-api-keys-create-dialog" onClick={() => setOpen(true)}>创建 API Key</button>
      </div>
      {loading && <p>正在加载…</p>}
      {!loading && !rows.length && <p>暂无 API Key</p>}
      {!!rows.length && (
        <table className="ratio-table">
          <thead><tr><th>App Key</th><th>名称</th><th>状态</th><th>限流</th><th>操作</th></tr></thead>
          <tbody>{rows.map((row) => (
            <tr key={row.id} data-testid="tenant-tenant-access-api-keys-row">
              <td>{row.appKey}</td><td>{row.name}</td><td>{row.status}</td>
              <td>{row.perSecond}/{row.perMinute}/{row.perHour}/{row.perDay}</td>
              <td>
                <button
                  type="button"
                  data-testid="tenant-tenant-access-api-keys-revoke"
                  onClick={async () => {
                    if (confirm('确认撤销？')) {
                      await revokeTenantApiKey(row.id);
                      setSuccess('已撤销');
                      await load();
                    }
                  }}
                >撤销</button>
              </td>
            </tr>
          ))}</tbody>
        </table>
      )}
      <p role="status">{success}</p>

      {open && (
        <ModalDialog labelledBy="api-key-title" onRequestClose={() => setOpen(false)}>
          <form noValidate onSubmit={(event) => { event.preventDefault(); void create(); }}>
            <h2 id="api-key-title">创建 API Key</h2>
            {validationError && <p role="alert" className="tenant-access-alert">{validationError}</p>}
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
            <button type="submit" disabled={submitting}>创建</button>
          </form>
        </ModalDialog>
      )}

      {handoff && (
        <ModalDialog labelledBy="secret-title" onRequestClose={() => setHandoff(null)}>
          <div data-testid="tenant-tenant-access-api-keys-secret-dialog">
            <h2 id="secret-title">App Secret（仅展示一次）</h2>
            <p className="tenant-access-handoff" data-testid="tenant-tenant-access-api-keys-secret-once">{handoff}</p>
            <button type="button" onClick={() => setHandoff(null)}>我已保存</button>
          </div>
        </ModalDialog>
      )}
    </section>
  );
}
