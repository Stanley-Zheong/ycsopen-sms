import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createExemptionPolicy,
  listExemptionPolicies,
  listExemptionUsageHistory,
  previewExemptionPolicy,
  revokeExemptionPolicy,
  EXEMPTION_PERMISSIONS,
  type ExemptionPolicyPayload,
  type ExemptionPolicyRecord,
  type ExemptionPreviewPayload,
  type ExemptionPreviewResult,
} from '@/api/exemptionPolicyApi';
import { mutationErrorMessage } from '@/api/client';
import ModalDialog from '@/components/common/ModalDialog';
import { useIdentityAccess } from '@/pages/admin/identity/useIdentityAccess';
import { isPlatformRole, protectedQueryKey, useAuthStore } from '@/store/authStore';
import '@/styles/exemption-policy.css';

const INITIAL_FORM: ExemptionPolicyPayload = {
  tenantId: 42,
  exemptionType: 'SIGNATURE',
  resourceId: 'sig-1201',
  productCode: 'DOMESTIC_SMS',
  scopeExpression: 'LOGIN',
  approvalStatus: 'APPROVED',
  validFrom: '2026-09-01T00:00:00',
  validUntil: '2026-10-01T00:00:00',
  reason: '临时业务豁免',
};

const INITIAL_PREVIEW: ExemptionPreviewPayload = {
  tenantId: 42,
  exemptionType: 'SIGNATURE',
  resourceId: 'sig-1201',
  productCode: 'DOMESTIC_SMS',
  scopeExpression: 'LOGIN',
  controlCode: 'SIGNATURE_REVIEW',
  reason: '范围验证',
};

export default function ExemptionPolicyPage() {
  const userType = useAuthStore((state) => state.userType);
  const platformRole = isPlatformRole(userType);
  const access = useIdentityAccess(platformRole);
  const administrator = userType === 'ADMIN';
  const canRead = administrator || access.can(EXEMPTION_PERMISSIONS.read);
  const canWrite = administrator || access.can(EXEMPTION_PERMISSIONS.write);
  const canAudit = administrator || access.can(EXEMPTION_PERMISSIONS.audit);
  const canManage = administrator || canRead || canWrite || canAudit;
  const policyKey = protectedQueryKey('exemption-policies');
  const usageKey = protectedQueryKey('exemption-usage-history');
  const [form, setForm] = useState(INITIAL_FORM);
  const [previewForm, setPreviewForm] = useState(INITIAL_PREVIEW);
  const [previewResult, setPreviewResult] = useState<ExemptionPreviewResult | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [createError, setCreateError] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const queryClient = useQueryClient();

  const policies = useQuery({
    queryKey: policyKey,
    queryFn: listExemptionPolicies,
    retry: false,
    enabled: platformRole && canRead,
  });
  const usage = useQuery({
    queryKey: usageKey,
    queryFn: listExemptionUsageHistory,
    retry: false,
    enabled: platformRole && canAudit,
  });

  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: policyKey }),
      queryClient.invalidateQueries({ queryKey: usageKey }),
    ]);
  };

  const createMutation = useMutation({
    mutationFn: createExemptionPolicy,
    onSuccess: async () => {
      setMessage('豁免策略已保存。');
      setError('');
      await refresh();
      setCreateOpen(false);
      setCreateError('');
      setForm(INITIAL_FORM);
    },
    onError: (failure) => {
      setMessage('');
      setCreateError(mutationErrorMessage(failure, '豁免策略保存失败'));
    },
  });

  const previewMutation = useMutation({
    mutationFn: previewExemptionPolicy,
    onSuccess: async (result) => {
      setPreviewResult(result);
      setMessage('豁免生效预览已刷新。');
      setError('');
      await refresh();
    },
    onError: (failure) => setError(mutationErrorMessage(failure, '豁免预览失败')),
  });

  const revokeMutation = useMutation({
    mutationFn: (policy: ExemptionPolicyRecord) => revokeExemptionPolicy(policy.id, '人工撤销临时豁免'),
    onSuccess: async () => {
      setMessage('豁免策略已撤销。');
      setError('');
      await refresh();
    },
    onError: (failure) => setError(mutationErrorMessage(failure, '豁免撤销失败')),
  });

  if (!platformRole || (!canManage && !access.isLoading)) {
    return <p role="alert" data-testid="admin-auditable-exemption-exemption-policy-access-denied">无权查看豁免策略。</p>;
  }

  const rows = policies.data ?? [];
  const usageRows = usage.data ?? [];

  return (
    <section data-testid="admin-auditable-exemption-exemption-policy-page">
      <nav aria-label="面包屑">审核中心 / 豁免策略</nav>
      <header className="exemption-policy-header">
        <div>
          <h1>豁免策略</h1>
          <p className="page-description">配置有边界、有审批、有有效期的签名、内容、账号豁免，并保留每次决策和使用证据。</p>
        </div>
        {canWrite && <button type="button" data-testid="admin-auditable-exemption-exemption-policy-create-open" onClick={() => { setCreateError(''); setCreateOpen(true); }}>新增豁免</button>}
      </header>
      {message && <p role="status" className="exemption-policy-alert success" data-testid="admin-auditable-exemption-exemption-policy-message">{message}</p>}
      {error && <p role="alert" className="exemption-policy-alert error" data-testid="admin-auditable-exemption-exemption-policy-error">{error}</p>}

      {createOpen && (
        <ModalDialog labelledBy="exemption-policy-create-title" onRequestClose={() => { setCreateOpen(false); setCreateError(''); }}>
          <form className="exemption-policy-form" data-testid="admin-auditable-exemption-exemption-policy-create-dialog" onSubmit={(event) => { event.preventDefault(); createMutation.mutate(form); }}>
            <h2 id="exemption-policy-create-title">新增豁免</h2>
            <label>租户
              <input required data-testid="admin-auditable-exemption-exemption-policy-tenant" type="number" value={form.tenantId} onChange={(event) => setForm({ ...form, tenantId: Number(event.target.value) })} />
            </label>
            <label>类型
              <select data-testid="admin-auditable-exemption-exemption-policy-type" value={form.exemptionType} onChange={(event) => setForm({ ...form, exemptionType: event.target.value })}>
                <option value="SIGNATURE">签名</option><option value="CONTENT">内容</option><option value="ACCOUNT">账号</option>
              </select>
            </label>
            <label>资源<input required data-testid="admin-auditable-exemption-exemption-policy-resource" value={form.resourceId} onChange={(event) => setForm({ ...form, resourceId: event.target.value })} /></label>
            <label>产品<input required data-testid="admin-auditable-exemption-exemption-policy-product" value={form.productCode} onChange={(event) => setForm({ ...form, productCode: event.target.value })} /></label>
            <label>范围<input required data-testid="admin-auditable-exemption-exemption-policy-scope" value={form.scopeExpression} onChange={(event) => setForm({ ...form, scopeExpression: event.target.value })} /></label>
            <label>审批
              <select data-testid="admin-auditable-exemption-exemption-policy-approval" value={form.approvalStatus} onChange={(event) => setForm({ ...form, approvalStatus: event.target.value })}>
                <option value="APPROVED">已批准</option><option value="PENDING">待批准</option><option value="REJECTED">已拒绝</option>
              </select>
            </label>
            <label>生效时间<input required data-testid="admin-auditable-exemption-exemption-policy-valid-from" value={form.validFrom} onChange={(event) => setForm({ ...form, validFrom: event.target.value })} /></label>
            <label>失效时间<input required data-testid="admin-auditable-exemption-exemption-policy-valid-until" value={form.validUntil} onChange={(event) => setForm({ ...form, validUntil: event.target.value })} /></label>
            <label>原因<textarea required data-testid="admin-auditable-exemption-exemption-policy-reason" value={form.reason} onChange={(event) => setForm({ ...form, reason: event.target.value })} /></label>
            {createError && <p role="alert" data-testid="admin-auditable-exemption-exemption-policy-create-error" className="exemption-policy-alert error">{createError}</p>}
            <div className="dialog-actions">
              <button type="button" className="button-secondary" data-testid="admin-auditable-exemption-exemption-policy-create-cancel" onClick={() => { setCreateOpen(false); setCreateError(''); }}>取消</button>
              <button type="submit" data-testid="admin-auditable-exemption-exemption-policy-save" disabled={createMutation.isPending}>保存豁免</button>
            </div>
          </form>
        </ModalDialog>
      )}

      <section className="card" data-testid="admin-auditable-exemption-exemption-effective-preview">
        <h2>生效预览</h2>
        <div className="exemption-policy-form compact">
          <label>租户
            <input data-testid="admin-auditable-exemption-exemption-preview-tenant" type="number" value={previewForm.tenantId} onChange={(event) => setPreviewForm({ ...previewForm, tenantId: Number(event.target.value) })} />
          </label>
          <label>类型
            <select data-testid="admin-auditable-exemption-exemption-preview-type" value={previewForm.exemptionType} onChange={(event) => setPreviewForm({ ...previewForm, exemptionType: event.target.value })}>
              <option value="SIGNATURE">签名</option>
              <option value="CONTENT">内容</option>
              <option value="ACCOUNT">账号</option>
            </select>
          </label>
          <label>资源
            <input data-testid="admin-auditable-exemption-exemption-preview-resource" value={previewForm.resourceId} onChange={(event) => setPreviewForm({ ...previewForm, resourceId: event.target.value })} />
          </label>
          <label>产品
            <input data-testid="admin-auditable-exemption-exemption-preview-product" value={previewForm.productCode} onChange={(event) => setPreviewForm({ ...previewForm, productCode: event.target.value })} />
          </label>
          <label>范围
            <input data-testid="admin-auditable-exemption-exemption-preview-scope" value={previewForm.scopeExpression} onChange={(event) => setPreviewForm({ ...previewForm, scopeExpression: event.target.value })} />
          </label>
          <label>控制点
            <input data-testid="admin-auditable-exemption-exemption-preview-control" value={previewForm.controlCode} onChange={(event) => setPreviewForm({ ...previewForm, controlCode: event.target.value })} />
          </label>
          <label>原因
            <input data-testid="admin-auditable-exemption-exemption-preview-reason" value={previewForm.reason} onChange={(event) => setPreviewForm({ ...previewForm, reason: event.target.value })} />
          </label>
          <button type="button" data-testid="admin-auditable-exemption-exemption-preview-run" onClick={() => previewMutation.mutate(previewForm)} disabled={!canRead || previewMutation.isPending}>预览生效结果</button>
        </div>
        {previewResult && (
          <dl data-testid="admin-auditable-exemption-exemption-preview-result" className="exemption-policy-result">
            <dt>结果</dt><dd>{previewResult.result}</dd>
            <dt>命中规则</dt><dd>{previewResult.exemptionRuleId ?? '无'}</dd>
            <dt>版本</dt><dd>{previewResult.versionNo ?? '无'}</dd>
            <dt>原因</dt><dd>{previewResult.reason}</dd>
          </dl>
        )}
      </section>

      <section className="card">
        <h2>策略列表</h2>
        {policies.isLoading && <p>正在加载…</p>}
        {!policies.isLoading && rows.length === 0 && <p>暂无豁免策略。</p>}
        {rows.length > 0 && (
          <table className="ratio-table" data-testid="admin-auditable-exemption-exemption-policy-table">
            <thead><tr><th>租户</th><th>类型</th><th>资源</th><th>产品/范围</th><th>审批</th><th>版本</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id} data-testid="admin-auditable-exemption-exemption-policy-row">
                  <td>{row.tenantId}</td>
                  <td>{row.exemptionType}</td>
                  <td>{row.resourceId}</td>
                  <td>{row.productCode}<br />{row.scopeExpression}</td>
                  <td>{row.approvalStatus}</td>
                  <td>{row.versionNo}</td>
                  <td>{row.revoked ? '已撤销' : '有效配置'}</td>
                  <td><button type="button" data-testid="admin-auditable-exemption-exemption-policy-revoke" onClick={() => revokeMutation.mutate(row)} disabled={!canWrite || row.revoked || revokeMutation.isPending}>撤销</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section className="card" data-testid="admin-auditable-exemption-exemption-usage-history">
        <h2>使用审计</h2>
        {usageRows.length === 0 && <p>暂无使用审计。</p>}
        {usageRows.length > 0 && (
          <table className="ratio-table">
            <thead><tr><th>规则</th><th>主体</th><th>控制点</th><th>操作人</th><th>原因</th><th>结果</th></tr></thead>
            <tbody>
              {usageRows.map((row) => (
                <tr key={row.id} data-testid="admin-auditable-exemption-exemption-usage-row">
                  <td>{row.exemptionRuleId ?? '无'} / v{row.versionNo ?? '-'}</td>
                  <td>{row.subjectType}:{row.subjectId}</td>
                  <td>{row.controlCode}</td>
                  <td>{row.actor}</td>
                  <td>{row.reason}</td>
                  <td>{row.result}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </section>
  );
}
