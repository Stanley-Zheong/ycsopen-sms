import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  importStatusMappings,
  listStatusMappings,
  listStatusVersions,
  normalizeStatus,
  PROVIDER_STATUS_PERMISSIONS,
  requestStatusExport,
  type NormalizedStatus,
} from '@/api/providerStatusApi';
import { mutationErrorMessage } from '@/api/client';
import { useIdentityAccess } from '@/pages/admin/identity/useIdentityAccess';
import { isPlatformRole, protectedQueryKey, useAuthStore } from '@/store/authStore';
import '@/styles/provider-status.css';

export default function ProviderStatusPage() {
  const userType = useAuthStore((state) => state.userType);
  const platformRole = isPlatformRole(userType);
  const access = useIdentityAccess(platformRole);
  const admin = userType === 'ADMIN';
  const canRead = admin || access.can(PROVIDER_STATUS_PERMISSIONS.read);
  const canImport = admin || access.can(PROVIDER_STATUS_PERMISSIONS.import);
  const canExport = admin || access.can(PROVIDER_STATUS_PERMISSIONS.export);
  const queryClient = useQueryClient();
  const versionKey = protectedQueryKey('provider-status-versions');
  const mappingKey = protectedQueryKey('provider-status-mappings');
  const versions = useQuery({ queryKey: versionKey, queryFn: listStatusVersions, enabled: platformRole && canRead, retry: false });
  const mappings = useQuery({ queryKey: mappingKey, queryFn: listStatusMappings, enabled: platformRole && canRead, retry: false });
  const [versionNo, setVersionNo] = useState('ST20260909');
  const [sourceName, setSourceName] = useState('供应商文档');
  const [rowsText, setRowsText] = useState('YTO,HTTP,DELIVRD,SUCCESS,true,true,false,INFO,确认送达');
  const [providerName, setProviderName] = useState('YTO');
  const [protocol, setProtocol] = useState('HTTP');
  const [providerCode, setProviderCode] = useState('DELIVRD');
  const [normalized, setNormalized] = useState<NormalizedStatus | null>(null);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  const refresh = async () => Promise.all([
    queryClient.invalidateQueries({ queryKey: versionKey }),
    queryClient.invalidateQueries({ queryKey: mappingKey }),
  ]);
  const onError = (failure: unknown, fallback: string) => {
    setError(mutationErrorMessage(failure, fallback));
    setMessage('');
  };
  const importMutation = useMutation({
    mutationFn: () => importStatusMappings({
      versionNo,
      sourceName,
      effectiveAt: null,
      rows: rowsText.split(/\n+/).map((line) => {
        const [provider, proto, code, category, finalState, billable, retryable, severity, advice] = line.split(',').map((part) => part.trim());
        return {
          providerName: provider,
          protocol: proto,
          providerCode: code,
          platformCategory: category,
          finalState: finalState === 'true',
          billable: billable === 'true',
          retryable: retryable === 'true',
          severity,
          advice,
        };
      }),
    }),
    onSuccess: async (result) => {
      setMessage(`状态码版本 ${result.versionNo} 已导入：成功 ${result.success}，冲突 ${result.failed}`);
      setError('');
      await refresh();
    },
    onError: (failure) => onError(failure, '状态码导入失败'),
  });
  const normalizeMutation = useMutation({
    mutationFn: () => normalizeStatus(providerName, protocol, providerCode),
    onSuccess: (result) => {
      setNormalized(result);
      setMessage(`归一化完成：${result.platformCategory} / final=${result.finalState} / billable=${result.billable} / retry=${result.retryable}`);
      setError('');
    },
    onError: (failure) => onError(failure, '归一化失败'),
  });
  const exportMutation = useMutation({
    mutationFn: () => requestStatusExport(providerName, protocol),
    onSuccess: (result) => {
      setMessage(`导出请求已登记：${result.requestId}，匹配 ${result.matchedRows} 条`);
      setError('');
    },
    onError: (failure) => onError(failure, '导出请求失败'),
  });

  if (!platformRole || (!canRead && !access.isLoading)) {
    return <p role="alert" data-testid="admin-provider-status-taxonomy-access-denied">无权查看状态码映射。</p>;
  }

  return (
    <section className="provider-status-page" data-testid="admin-provider-status-taxonomy-status-codes-page">
      <nav aria-label="面包屑">工具管理 / 状态码映射</nav>
      <header className="provider-status-header">
        <div>
          <h1>供应商状态码归一化</h1>
          <p className="page-description">一个有效版本同时提供最终态、计费、重试、严重级别和处理建议，供 HTTP/CMPP、回执、账务、重试和统计复用。</p>
        </div>
      </header>
      {message && <p role="status" data-testid="admin-provider-status-taxonomy-message" className="provider-status-alert success">{message}</p>}
      {error && <p role="alert" data-testid="admin-provider-status-taxonomy-error" className="provider-status-alert error">{error}</p>}

      <section className="card">
        <h2>导入映射</h2>
        <div className="provider-status-form">
          <label>版本号<input data-testid="admin-provider-status-taxonomy-status-codes-version" value={versionNo} onChange={(event) => setVersionNo(event.target.value)} /></label>
          <label>来源<input data-testid="admin-provider-status-taxonomy-status-codes-source" value={sourceName} onChange={(event) => setSourceName(event.target.value)} /></label>
          <label>CSV 行<textarea data-testid="admin-provider-status-taxonomy-status-codes-import-input" value={rowsText} onChange={(event) => setRowsText(event.target.value)} /></label>
          <button type="button" data-testid="admin-provider-status-taxonomy-status-codes-import" disabled={!canImport} onClick={() => importMutation.mutate()}>导入状态码</button>
          <button type="button" data-testid="admin-provider-status-taxonomy-status-codes-export" disabled={!canExport} onClick={() => exportMutation.mutate()}>请求导出</button>
        </div>
      </section>

      <section className="card">
        <h2>归一化试算</h2>
        <div className="provider-status-form">
          <label>供应商<input data-testid="admin-provider-status-taxonomy-provider" value={providerName} onChange={(event) => setProviderName(event.target.value)} /></label>
          <label>协议<select data-testid="admin-provider-status-taxonomy-protocol" value={protocol} onChange={(event) => setProtocol(event.target.value)}><option value="HTTP">HTTP</option><option value="CMPP">CMPP</option><option value="SGIP">SGIP</option><option value="SMGP">SMGP</option></select></label>
          <label>状态码<input data-testid="admin-provider-status-taxonomy-provider-code" value={providerCode} onChange={(event) => setProviderCode(event.target.value)} /></label>
          <button type="button" data-testid="admin-provider-status-taxonomy-normalize" disabled={!canRead} onClick={() => normalizeMutation.mutate()}>归一化</button>
        </div>
        {normalized && (
          <div data-testid="admin-provider-status-taxonomy-normalized-result">
            {normalized.platformCategory} / final={String(normalized.finalState)} / billable={String(normalized.billable)} / retry={String(normalized.retryable)}
            <span data-testid="admin-provider-status-taxonomy-unknown-fallback"> 来源：{normalized.source} {normalized.advice}</span>
          </div>
        )}
      </section>

      <section className="card" data-testid="admin-provider-status-status-codes-version-history">
        <h2>版本历史</h2>
        <table className="ratio-table">
          <thead><tr><th>版本</th><th>状态</th><th>来源</th><th>冲突</th><th>生效时间</th></tr></thead>
          <tbody>{(versions.data ?? []).map((row) => (
            <tr key={row.id} data-testid="admin-provider-status-taxonomy-status-codes-version-row"><td>{row.versionNo}</td><td>{row.status}</td><td>{row.sourceName}</td><td>{row.conflictCount}</td><td>{row.effectiveAt}</td></tr>
          ))}</tbody>
        </table>
      </section>

      <section className="card">
        <h2>当前映射</h2>
        <table className="ratio-table" data-testid="admin-provider-status-taxonomy-status-codes-table">
          <thead><tr><th>供应商</th><th>协议</th><th>状态码</th><th>平台分类</th><th>最终</th><th>计费</th><th>重试</th><th>建议</th></tr></thead>
          <tbody>{(mappings.data ?? []).map((row) => (
            <tr key={`${row.providerName}-${row.protocol}-${row.providerCode}`} data-testid="admin-provider-status-taxonomy-status-codes-row"><td>{row.providerName}</td><td>{row.protocol}</td><td>{row.providerCode}</td><td>{row.platformCategory}</td><td>{String(row.finalState)}</td><td>{String(row.billable)}</td><td>{String(row.retryable)}</td><td>{row.advice}</td></tr>
          ))}</tbody>
        </table>
      </section>
    </section>
  );
}
