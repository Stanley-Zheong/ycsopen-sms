import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  listTenantSignatures,
  listUsableChannels,
  submitSignatureApplication,
  type SignatureFiling,
  type SignatureRecord,
} from '@/api/signatureLifecycleApi';
import { mutationErrorMessage } from '@/api/client';
import { useAuthStore, isPlatformRole } from '@/store/authStore';
import '@/styles/signature-lifecycle.css';

const emptyForm = {
  signContent: '',
  signType: 'ENTERPRISE',
  usageType: 'SELF',
  evidenceRef: '',
  applicantName: '',
  applicantPhone: '',
};

export default function SignatureLifecyclePage() {
  const userType = useAuthStore((state) => state.userType);
  const canUse = userType === 'TENANT_ADMIN' || userType === 'TENANT_DEV';
  const queryClient = useQueryClient();
  const [form, setForm] = useState(emptyForm);
  const [selected, setSelected] = useState<SignatureRecord | null>(null);
  const [usable, setUsable] = useState<SignatureFiling[] | null>(null);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  const signatures = useQuery({
    queryKey: ['tenant-signatures'],
    queryFn: listTenantSignatures,
    retry: false,
    enabled: canUse,
  });
  const rows = signatures.data ?? [];
  const active = selected ?? rows[0] ?? null;

  const submit = useMutation({
    mutationFn: () => submitSignatureApplication(form),
    onSuccess: async () => {
      setMessage('签名申请已提交。');
      setError('');
      setForm(emptyForm);
      await queryClient.invalidateQueries({ queryKey: ['tenant-signatures'] });
    },
    onError: (failure) => setError(mutationErrorMessage(failure, '签名申请提交失败')),
  });

  const loadUsable = async () => {
    if (!active) return;
    try {
      setUsable(await listUsableChannels(active.id));
      setMessage('');
      setError('');
    } catch (failure) {
      setError(mutationErrorMessage(failure, '可用通道加载失败'));
    }
  };

  if (!canUse || isPlatformRole(userType)) {
    return <p role="alert">无权查看签名管理。</p>;
  }

  return (
    <section data-testid="tenant-signature-lifecycle-signatures-page">
      <nav aria-label="面包屑">模板签名 / 签名管理</nav>
      <header className="signature-lifecycle-header">
        <div>
          <h1>签名管理</h1>
          <p className="page-description">提交签名申请，查看审核反馈和已报备可用通道。</p>
        </div>
      </header>
      {message && <p role="status" className="signature-lifecycle-alert success">{message}</p>}
      {error && <p role="alert" className="signature-lifecycle-alert error">{error}</p>}

      <section className="card signature-lifecycle-form" data-testid="tenant-signature-lifecycle-signatures-application-form">
        <h2>提交签名申请</h2>
        <label>签名内容
          <input value={form.signContent} onChange={(event) => setForm({ ...form, signContent: event.target.value })} />
        </label>
        <label>签名类型
          <select value={form.signType} onChange={(event) => setForm({ ...form, signType: event.target.value })}>
            <option value="ENTERPRISE">企业</option>
            <option value="APP">App</option>
            <option value="TRADEMARK">商标</option>
            <option value="INSTITUTION">事业单位</option>
            <option value="GOVERNMENT">政府</option>
          </select>
        </label>
        <label>使用类型
          <select value={form.usageType} onChange={(event) => setForm({ ...form, usageType: event.target.value })}>
            <option value="SELF">自用</option>
            <option value="OTHER">他用</option>
          </select>
        </label>
        <label>证明材料
          <input value={form.evidenceRef} onChange={(event) => setForm({ ...form, evidenceRef: event.target.value })} placeholder="pobj-proof-..." />
        </label>
        <label>申请人
          <input value={form.applicantName} onChange={(event) => setForm({ ...form, applicantName: event.target.value })} />
        </label>
        <label>联系电话
          <input value={form.applicantPhone} onChange={(event) => setForm({ ...form, applicantPhone: event.target.value })} />
        </label>
        <button type="button" data-testid="tenant-signature-lifecycle-signatures-application-submit" onClick={() => submit.mutate()} disabled={submit.isPending}>
          提交申请
        </button>
      </section>

      <section className="card">
        <h2>签名列表</h2>
        {signatures.isLoading && <p>正在加载…</p>}
        {!signatures.isLoading && rows.length === 0 && <p>暂无签名申请。</p>}
        {rows.length > 0 && (
          <table className="ratio-table">
            <thead><tr><th>签名</th><th>类型</th><th>风险</th><th>状态</th><th>材料</th><th>操作</th></tr></thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id}>
                  <td>{row.signContent}</td>
                  <td>{row.signType} / {row.usageType}</td>
                  <td>{row.riskLevel}</td>
                  <td>{row.auditStatus}</td>
                  <td>{row.evidenceRef ?? '未提交'}</td>
                  <td><button type="button" onClick={() => { setSelected(row); setUsable(null); }}>查看</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {active && (
        <section className="card signature-lifecycle-detail">
          <h2>审核反馈：{active.signContent}</h2>
          <div data-testid="tenant-signature-lifecycle-signatures-history">
            {active.history.map((item) => (
              <p key={`${item.eventType}-${item.createdAt}`}>{item.eventType} — {item.opinion} — {item.actor}</p>
            ))}
          </div>
          <button type="button" data-testid="tenant-signature-lifecycle-signatures-usable-channels" onClick={() => void loadUsable()}>
            查看可用通道
          </button>
          {usable && (
            <div className="signature-lifecycle-usable">
              {usable.length === 0 ? <p>暂无可用通道</p> : usable.map((row) => (
                <p key={row.channelId}>{row.channelName} / {row.status} / {row.channelEligibilityReason}</p>
              ))}
            </div>
          )}
        </section>
      )}
    </section>
  );
}
