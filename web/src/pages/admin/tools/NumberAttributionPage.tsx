import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  importPrefixes,
  listPortabilityRows,
  listPrefixVersions,
  lookupAttribution,
  NUMBER_ATTRIBUTION_PERMISSIONS,
  savePortability,
  type AttributionResult,
} from '@/api/numberAttributionApi';
import { mutationErrorMessage } from '@/api/client';
import { useIdentityAccess } from '@/pages/admin/identity/useIdentityAccess';
import { isPlatformRole, protectedQueryKey, useAuthStore } from '@/store/authStore';
import '@/styles/number-attribution.css';

const SAMPLE_ROWS = '139,MOBILE,广东,广州\n1391234,UNICOM,广东,深圳';

export default function NumberAttributionPage() {
  const userType = useAuthStore((state) => state.userType);
  const platformRole = isPlatformRole(userType);
  const access = useIdentityAccess(platformRole);
  const admin = userType === 'ADMIN';
  const canRead = admin || access.can(NUMBER_ATTRIBUTION_PERMISSIONS.read);
  const canImport = admin || access.can(NUMBER_ATTRIBUTION_PERMISSIONS.import);
  const canPortability = admin || access.can(NUMBER_ATTRIBUTION_PERMISSIONS.portability);
  const queryClient = useQueryClient();
  const versionKey = protectedQueryKey('number-prefix-versions');
  const portabilityKey = protectedQueryKey('number-portability');
  const versions = useQuery({ queryKey: versionKey, queryFn: listPrefixVersions, enabled: platformRole && canRead, retry: false });
  const portability = useQuery({ queryKey: portabilityKey, queryFn: listPortabilityRows, enabled: platformRole && canRead, retry: false });
  const [versionNo, setVersionNo] = useState('V20260909');
  const [updateType, setUpdateType] = useState('FULL');
  const [sourceName, setSourceName] = useState('官方号段');
  const [rowsText, setRowsText] = useState(SAMPLE_ROWS);
  const [mobile, setMobile] = useState('13912345678');
  const [forceFailure, setForceFailure] = useState(false);
  const [lookupResult, setLookupResult] = useState<AttributionResult | null>(null);
  const [portabilityMobile, setPortabilityMobile] = useState('13900000001');
  const [originalCarrier, setOriginalCarrier] = useState('MOBILE');
  const [currentCarrier, setCurrentCarrier] = useState('TELECOM');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  const onError = (failure: unknown, fallback: string) => {
    setError(mutationErrorMessage(failure, fallback));
    setMessage('');
  };
  const refresh = async () => Promise.all([
    queryClient.invalidateQueries({ queryKey: versionKey }),
    queryClient.invalidateQueries({ queryKey: portabilityKey }),
  ]);
  const importMutation = useMutation({
    mutationFn: () => importPrefixes({
      versionNo,
      updateType,
      sourceName,
      rows: rowsText.split(/\n+/).map((line) => {
        const [prefix, carrier, province, city] = line.split(',').map((part) => part.trim());
        return { prefix, carrier, province, city };
      }),
    }),
    onSuccess: async (result) => {
      setMessage(`号段版本 ${result.versionNo} 已导入：成功 ${result.success}，冲突 ${result.failed}`);
      setError('');
      await refresh();
    },
    onError: (failure) => onError(failure, '号段导入失败'),
  });
  const lookupMutation = useMutation({
    mutationFn: () => lookupAttribution(mobile, forceFailure),
    onSuccess: (result) => {
      setLookupResult(result);
      setMessage(`归属查询完成：${result.carrier} ${result.province}${result.city}`);
      setError('');
    },
    onError: (failure) => onError(failure, '归属查询失败'),
  });
  const portabilityMutation = useMutation({
    mutationFn: () => savePortability({
      mobile: portabilityMobile,
      originalCarrier,
      currentCarrier,
      portedAt: '2026-09-09',
      sourceName: '携转缓存',
      freshnessSeconds: 86400,
    }),
    onSuccess: async () => {
      setMessage('携号转网缓存已保存，手机号仅保留掩码和哈希。');
      setError('');
      await refresh();
    },
    onError: (failure) => onError(failure, '携号转网保存失败'),
  });

  if (!platformRole || (!canRead && !access.isLoading)) {
    return <p role="alert" data-testid="admin-number-attribution-access-denied">无权查看号码归属。</p>;
  }

  return (
    <section className="number-attribution-page" data-testid="admin-number-attribution-portability-attribution-page">
      <nav aria-label="面包屑">工具管理 / 号码归属 / 携号转网 / 号段版本</nav>
      <header className="number-attribution-header">
        <div>
          <h1>号码归属与携号转网</h1>
          <p className="page-description">按最长 3-7 位号段返回运营商、省份、城市；新鲜携转缓存可覆盖号段运营商，并保留来源和有效期。</p>
        </div>
      </header>
      {message && <p role="status" data-testid="admin-number-attribution-message" className="number-attribution-alert success">{message}</p>}
      {error && <p role="alert" data-testid="admin-number-attribution-error" className="number-attribution-alert error">{error}</p>}

      <section className="card" data-testid="admin-number-attribution-portability-prefixes-page">
        <h2>号段版本</h2>
        <div className="number-attribution-form">
          <label>版本号<input data-testid="admin-prefixes-version" value={versionNo} onChange={(event) => setVersionNo(event.target.value)} /></label>
          <label>更新类型<select data-testid="admin-prefixes-update-type" value={updateType} onChange={(event) => setUpdateType(event.target.value)}><option value="FULL">全量</option><option value="INCREMENTAL">增量</option></select></label>
          <label>来源<input data-testid="admin-prefixes-source" value={sourceName} onChange={(event) => setSourceName(event.target.value)} /></label>
          <label>CSV 行<textarea data-testid="admin-prefixes-import-input" value={rowsText} onChange={(event) => setRowsText(event.target.value)} /></label>
          <button type="button" data-testid="admin-prefixes-import" disabled={!canImport} onClick={() => importMutation.mutate()}>导入号段</button>
        </div>
        <table className="ratio-table" data-testid="admin-prefixes-version-table">
          <thead><tr><th>版本</th><th>类型</th><th>状态</th><th>来源</th><th>行数</th><th>冲突</th></tr></thead>
          <tbody>{(versions.data ?? []).map((row) => (
            <tr key={row.id} data-testid="admin-prefixes-version-row"><td>{row.versionNo}</td><td>{row.updateType}</td><td>{row.status}</td><td>{row.sourceName}</td><td>{row.totalRows}</td><td>{row.conflictCount}</td></tr>
          ))}</tbody>
        </table>
      </section>

      <section className="card" data-testid="admin-number-attribution-lookup-panel">
        <h2>归属查询</h2>
        <div className="number-attribution-lookup-form" data-testid="admin-number-attribution-lookup-form">
          <div className="number-attribution-lookup-field">
            <label htmlFor="admin-number-attribution-mobile-input" data-testid="admin-number-attribution-mobile-label">手机号</label>
            <input id="admin-number-attribution-mobile-input" data-testid="admin-number-attribution-mobile" value={mobile} onChange={(event) => setMobile(event.target.value)} />
          </div>
          <div className="number-attribution-lookup-choice">
            <label htmlFor="admin-number-attribution-force-provider-failure-input" data-testid="admin-number-attribution-force-provider-failure-label">模拟携转服务失败</label>
            <input id="admin-number-attribution-force-provider-failure-input" data-testid="admin-number-attribution-force-provider-failure" type="checkbox" checked={forceFailure} onChange={(event) => setForceFailure(event.target.checked)} />
          </div>
          <button type="button" data-testid="admin-number-attribution-lookup" disabled={!canRead} onClick={() => lookupMutation.mutate()}>查询归属</button>
        </div>
        {lookupResult && (
          <div data-testid="admin-number-attribution-result">
            <strong>{lookupResult.carrier}</strong> / {lookupResult.prefixCarrier} / {lookupResult.province}{lookupResult.city}
            <span data-testid="admin-number-attribution-fallback-source"> 来源：{lookupResult.source} {lookupResult.sourceName}</span>
          </div>
        )}
      </section>

      <section className="card" data-testid="admin-number-attribution-portability-portability-page">
        <h2>携号转网缓存</h2>
        <div className="number-attribution-form">
          <label>手机号<input data-testid="admin-number-portability-mobile" value={portabilityMobile} onChange={(event) => setPortabilityMobile(event.target.value)} /></label>
          <label>原运营商<select data-testid="admin-number-portability-original-carrier" value={originalCarrier} onChange={(event) => setOriginalCarrier(event.target.value)}><option value="MOBILE">移动</option><option value="UNICOM">联通</option><option value="TELECOM">电信</option></select></label>
          <label>当前运营商<select data-testid="admin-number-portability-current-carrier" value={currentCarrier} onChange={(event) => setCurrentCarrier(event.target.value)}><option value="MOBILE">移动</option><option value="UNICOM">联通</option><option value="TELECOM">电信</option></select></label>
          <button type="button" data-testid="admin-number-portability-save" disabled={!canPortability} onClick={() => portabilityMutation.mutate()}>保存携转缓存</button>
        </div>
        <table className="ratio-table" data-testid="admin-number-portability-table">
          <thead><tr><th>手机号</th><th>原运营商</th><th>当前运营商</th><th>来源</th><th>有效期</th><th>状态</th></tr></thead>
          <tbody>{(portability.data ?? []).map((row) => (
            <tr key={row.id} data-testid="admin-number-portability-row"><td>{row.maskedMobile}</td><td>{row.originalCarrier}</td><td>{row.currentCarrier}</td><td>{row.sourceName}</td><td>{row.freshnessExpiresAt}</td><td>{row.status}</td></tr>
          ))}</tbody>
        </table>
      </section>
    </section>
  );
}
