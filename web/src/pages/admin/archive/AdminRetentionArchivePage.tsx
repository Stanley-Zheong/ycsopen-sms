import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  exportArchiveManifest,
  listArchiveManifests,
  listArchivePolicies,
  restoreArchiveManifest,
  saveArchivePolicy,
  scanArchive,
  verifyArchiveManifest,
  type ArchiveManifest,
} from '@/api/retentionArchiveApi';
import { mutationErrorMessage } from '@/api/client';
import '@/styles/retention-archive.css';

function display(value: string | null): string {
  if (!value) return '-';
  return new Date(value).toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' });
}

function statusLabel(value: string): string {
  return ({ COMPLETED: '已归档', FAILED: '失败', CORRUPTED: '已损坏', RESTORED: '已恢复' } as Record<string, string>)[value] ?? value;
}

export default function AdminRetentionArchivePage() {
  const queryClient = useQueryClient();
  const [dataDomain, setDataDomain] = useState('MESSAGE_TASKS');
  const [tenantId, setTenantId] = useState('');
  const [status, setStatus] = useState('');
  const [retentionDays, setRetentionDays] = useState(730);
  const [hotMonths, setHotMonths] = useState(3);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const policies = useQuery({ queryKey: ['archive-policies'], queryFn: listArchivePolicies, retry: false });
  const manifests = useQuery({
    queryKey: ['archive-manifests', dataDomain, tenantId, status],
    queryFn: () => listArchiveManifests({ dataDomain, tenantId, status }),
    retry: false,
  });
  const activePolicy = useMemo(() => policies.data?.find((item) => item.dataDomain === dataDomain), [policies.data, dataDomain]);
  const onSuccess = async (text: string) => {
    setMessage(text);
    setError('');
    await queryClient.invalidateQueries({ queryKey: ['archive-policies'] });
    await queryClient.invalidateQueries({ queryKey: ['archive-manifests'] });
  };
  const onError = (failure: unknown, fallback: string) => {
    setMessage('');
    setError(mutationErrorMessage(failure, fallback));
  };
  const save = useMutation({
    mutationFn: () => saveArchivePolicy(dataDomain, { retentionDays, hotMonths }),
    onSuccess: () => void onSuccess('归档策略已保存'),
    onError: (failure) => onError(failure, '归档策略保存失败'),
  });
  const scan = useMutation({
    mutationFn: () => scanArchive(dataDomain, tenantId),
    onSuccess: (manifest) => void onSuccess(`归档扫描完成：${manifest.id} / ${manifest.rowCount} 条`),
    onError: (failure) => onError(failure, '归档扫描失败'),
  });
  const verify = useMutation({
    mutationFn: (manifest: ArchiveManifest) => verifyArchiveManifest(manifest.id),
    onSuccess: (manifest) => void onSuccess(`归档校验完成：${statusLabel(manifest.archiveStatus)}`),
    onError: (failure) => onError(failure, '归档校验失败'),
  });
  const restore = useMutation({
    mutationFn: (manifest: ArchiveManifest) => restoreArchiveManifest(manifest.id),
    onSuccess: (job) => void onSuccess(`恢复任务完成：${job.restoredRecordCount} 条`),
    onError: (failure) => onError(failure, '归档恢复失败'),
  });
  const exportJob = useMutation({
    mutationFn: (manifest: ArchiveManifest) => exportArchiveManifest(manifest.id),
    onSuccess: (job) => void onSuccess(`归档导出任务已创建：${job.exportTaskId ?? job.id}`),
    onError: (failure) => onError(failure, '归档导出失败'),
  });
  const rows = manifests.data ?? [];

  return (
    <section className="retention-archive-page" data-testid="admin-retention-archive-page">
      <nav aria-label="面包屑">平台管理 / 保留归档</nav>
      <header className="retention-archive-header">
        <div>
          <h1>保留归档与恢复</h1>
          <p className="page-description">按数据域管理保留策略、热冷归档、加密清单、校验、恢复和归档导出。</p>
        </div>
      </header>

      {message && <p role="status" className="retention-archive-alert success" data-testid="admin-retention-archive-message">{message}</p>}
      {error && <p role="alert" className="retention-archive-alert error" data-testid="admin-retention-archive-error">{error}</p>}

      <section className="retention-archive-grid">
        <section className="card" data-testid="admin-retention-archive-policy-card">
          <h2>保留策略</h2>
          <label>数据域
            <select data-testid="admin-retention-archive-policy-domain" value={dataDomain} onChange={(event) => setDataDomain(event.target.value)}>
              {(policies.data ?? [{ dataDomain: 'MESSAGE_TASKS' }]).map((policy) => <option key={policy.dataDomain} value={policy.dataDomain}>{policy.dataDomain}</option>)}
            </select>
          </label>
          <label>保留天数<input type="number" data-testid="admin-retention-archive-policy-retention-days" value={retentionDays} onChange={(event) => setRetentionDays(Number(event.target.value))} /></label>
          <label>热数据月数<input type="number" data-testid="admin-retention-archive-policy-hot-months" value={hotMonths} onChange={(event) => setHotMonths(Number(event.target.value))} /></label>
          <p data-testid="admin-retention-archive-policy-summary">
            当前源表：{activePolicy?.sourceTable ?? '-'}；分区：按月；法务保留：{display(activePolicy?.legalHoldUntil ?? null)}
          </p>
          <button type="button" data-testid="admin-retention-archive-policy-save" onClick={() => save.mutate()} disabled={save.isPending}>保存策略</button>
        </section>

        <section className="card" data-testid="admin-retention-archive-scan-card">
          <h2>热冷归档扫描</h2>
          <label>机构ID<input data-testid="admin-retention-archive-filter-tenant" value={tenantId} onChange={(event) => setTenantId(event.target.value)} /></label>
          <label>清单状态<input data-testid="admin-retention-archive-filter-status" value={status} onChange={(event) => setStatus(event.target.value)} placeholder="COMPLETED" /></label>
          <button type="button" data-testid="admin-retention-archive-scan" onClick={() => scan.mutate()} disabled={scan.isPending}>扫描并归档</button>
        </section>
      </section>

      <section className="card">
        <h2>归档清单</h2>
        <table className="retention-archive-table" data-testid="admin-retention-archive-manifest-table">
          <thead>
            <tr>
              <th>清单ID</th><th>数据域</th><th>分区</th><th>状态</th><th>记录</th><th>校验和</th><th>保留到</th><th>删除资格</th><th>失败原因</th><th>动作</th>
            </tr>
          </thead>
          <tbody>{rows.map((manifest) => (
            <tr key={manifest.id} data-testid="admin-retention-archive-manifest-row">
              <td>{manifest.id}</td>
              <td>{manifest.dataDomain}</td>
              <td>{manifest.partitionKey}</td>
              <td>{statusLabel(manifest.archiveStatus)}</td>
              <td>{manifest.rowCount}</td>
              <td>{manifest.checksumSha256.slice(0, 12)}…</td>
              <td>{display(manifest.retentionUntil)}</td>
              <td data-testid="admin-retention-archive-deletion-eligibility">{manifest.deletionEligible ? '可删除' : '保留中'}</td>
              <td>{manifest.failureReason ?? '-'}</td>
              <td>
                <button type="button" data-testid="admin-retention-archive-manifest-verify" onClick={() => verify.mutate(manifest)}>校验</button>
                <button type="button" data-testid="admin-retention-archive-manifest-restore" disabled={manifest.archiveStatus === 'CORRUPTED'} onClick={() => restore.mutate(manifest)}>恢复</button>
                <button type="button" data-testid="admin-retention-archive-export" disabled={manifest.archiveStatus === 'CORRUPTED'} onClick={() => exportJob.mutate(manifest)}>归档导出</button>
              </td>
            </tr>
          ))}</tbody>
        </table>
      </section>
    </section>
  );
}
