import { useEffect, useState } from 'react';
import {
  createTenantCmpp,
  listTenantCmpp,
  revokeTenantCmpp,
  type TenantCmppCredential,
} from '@/api/tenantAccessApi';
import ModalDialog from '@/components/common/ModalDialog';
import '@/styles/tenant-access.css';

export default function TenantCmppAccessPage() {
  const [rows, setRows] = useState<TenantCmppCredential[]>([]);
  const [open, setOpen] = useState(false);
  const [handoff, setHandoff] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [host, setHost] = useState('');
  const [port, setPort] = useState('7890');
  const [spid, setSpid] = useState('tenant');
  const [account, setAccount] = useState('system');
  const [password, setPassword] = useState('');
  const [maxConnections, setMaxConnections] = useState('4');
  const [tpsLimit, setTpsLimit] = useState('100');
  const [windowSize, setWindowSize] = useState('8');
  const [success, setSuccess] = useState('');
  const [validationError, setValidationError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      setRows((await listTenantCmpp()) ?? []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const create = async () => {
    if (submitting) return;
    const parsedPolicy = {
      endpointPort: Number(port),
      maxConnections: Number(maxConnections),
      tpsLimit: Number(tpsLimit),
      windowSize: Number(windowSize),
    };
    const validPolicy = Number.isInteger(parsedPolicy.endpointPort)
      && parsedPolicy.endpointPort >= 1
      && parsedPolicy.endpointPort <= 65_535
      && Number.isInteger(parsedPolicy.maxConnections)
      && parsedPolicy.maxConnections >= 1
      && parsedPolicy.maxConnections <= 1_000
      && Number.isInteger(parsedPolicy.tpsLimit)
      && parsedPolicy.tpsLimit >= 1
      && parsedPolicy.tpsLimit <= 100_000
      && Number.isInteger(parsedPolicy.windowSize)
      && parsedPolicy.windowSize >= 1
      && parsedPolicy.windowSize <= 10_000;
    if (!host.trim() || !spid.trim() || !account.trim() || password.length < 8) {
      setValidationError('地址、SPID、账号不能为空，密码至少需要 8 个字符。');
      return;
    }
    if (!validPolicy) {
      setValidationError('连接策略必须使用后端允许范围内的正整数。');
      return;
    }
    setValidationError('');
    setSubmitting(true);
    try {
      const created = await createTenantCmpp({
        spid,
        endpointHost: host,
        endpointPort: parsedPolicy.endpointPort,
        account,
        password,
        ipWhitelist: null,
        maxConnections: parsedPolicy.maxConnections,
        tpsLimit: parsedPolicy.tpsLimit,
        windowSize: parsedPolicy.windowSize,
      });
      setOpen(false);
      setHandoff(created?.password ?? null);
      await load();
    } catch {
      setValidationError('CMPP 申请失败，请检查输入后重试。');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <section data-testid="tenant-tenant-access-cmpp-access-page">
      <div className="tenant-access-toolbar">
        <div>
          <h1>CMPP 下游接入</h1>
          <p className="page-description">仅管理接入元数据，不建立 CMPP 会话。</p>
        </div>
        <button type="button" data-testid="tenant-tenant-access-cmpp-request-form" onClick={() => setOpen(true)}>申请 CMPP 接入</button>
      </div>
      {loading && <p>正在加载…</p>}
      {!!rows.length && (
        <table className="ratio-table">
          <thead><tr><th>协议</th><th>账号</th><th>SPID</th><th>端点</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>{rows.map((row) => (
            <tr key={row.id} data-testid="tenant-tenant-access-cmpp-row">
              <td>{row.protocol}</td><td>{row.account}</td><td>{row.spid}</td>
              <td>{row.endpointHost}:{row.endpointPort}</td><td>{row.status}</td>
              <td>
                <button
                  type="button"
                  data-testid="tenant-tenant-access-cmpp-revoke"
                  onClick={async () => {
                    await revokeTenantCmpp(row.id);
                    setSuccess('已撤销');
                    await load();
                  }}
                >撤销</button>
              </td>
            </tr>
          ))}</tbody>
        </table>
      )}
      <p role="status">{success}</p>

      {open && (
        <ModalDialog labelledBy="cmpp-title" onRequestClose={() => setOpen(false)}>
          <form noValidate onSubmit={(event) => { event.preventDefault(); void create(); }}>
            <h2 id="cmpp-title">申请 CMPP 接入</h2>
            {validationError && <p role="alert" className="tenant-access-alert">{validationError}</p>}
            <div data-testid="tenant-tenant-access-cmpp-request-form">
              <label data-testid="tenant-tenant-access-cmpp-endpoint">地址<input data-testid="tenant-tenant-access-cmpp-host" required maxLength={255} value={host} onChange={(event) => setHost(event.target.value)} /></label>
              <label>端口<input data-testid="tenant-tenant-access-cmpp-port" required min="1" max="65535" type="number" value={port} onChange={(event) => setPort(event.target.value)} /></label>
              <fieldset data-testid="tenant-tenant-access-cmpp-connection-policy">
                <legend>连接策略</legend>
                <label>最大连接数<input data-testid="tenant-tenant-access-cmpp-max-connections" required min="1" max="1000" type="number" value={maxConnections} onChange={(event) => setMaxConnections(event.target.value)} /></label>
                <label>TPS 上限<input data-testid="tenant-tenant-access-cmpp-tps-limit" required min="1" max="100000" type="number" value={tpsLimit} onChange={(event) => setTpsLimit(event.target.value)} /></label>
                <label>窗口大小<input data-testid="tenant-tenant-access-cmpp-window-size" required min="1" max="10000" type="number" value={windowSize} onChange={(event) => setWindowSize(event.target.value)} /></label>
              </fieldset>
              <label>SPID<input data-testid="tenant-tenant-access-cmpp-spid" required maxLength={32} value={spid} onChange={(event) => setSpid(event.target.value)} /></label>
              <label>账号<input data-testid="tenant-tenant-access-cmpp-account" required maxLength={100} value={account} onChange={(event) => setAccount(event.target.value)} /></label>
              <label>密码<input data-testid="tenant-tenant-access-cmpp-password" required minLength={8} maxLength={100} type="password" value={password} onChange={(event) => setPassword(event.target.value)} /></label>
            </div>
            <button type="submit" disabled={submitting}>提交申请</button>
          </form>
        </ModalDialog>
      )}

      {handoff && (
        <ModalDialog labelledBy="cmpp-secret-title" onRequestClose={() => setHandoff(null)}>
          <div data-testid="tenant-tenant-access-cmpp-secret-once">
            <h2 id="cmpp-secret-title">密码（仅展示一次）</h2>
            <p className="tenant-access-handoff">{handoff}</p>
            <button type="button" onClick={() => setHandoff(null)}>我已保存</button>
          </div>
        </ModalDialog>
      )}
    </section>
  );
}
