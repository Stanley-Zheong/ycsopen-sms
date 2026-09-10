import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  getTenantUplinkAutoReply,
  listTenantUplinks,
  saveTenantUplinkAutoReply,
} from '@/api/uplinkNormalizationApi';
import { mutationErrorMessage } from '@/api/client';
import '@/styles/uplink-normalization.css';

export default function TenantUplinksPage() {
  const queryClient = useQueryClient();
  const [draftFilters, setDraftFilters] = useState({ phoneNumber: '', keyword: '', carrier: '', pushState: '' });
  const [filters, setFilters] = useState(draftFilters);
  const [enabled, setEnabled] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [templateId, setTemplateId] = useState('');
  const [responseContent, setResponseContent] = useState('');
  const [loopGuardMinutes, setLoopGuardMinutes] = useState(30);
  const [auditReason, setAuditReason] = useState('租户配置上行自动回复');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const uplinkFilter = useMemo(() => filters, [filters]);
  const uplinks = useQuery({ queryKey: ['tenant-uplinks', uplinkFilter], queryFn: () => listTenantUplinks(uplinkFilter), retry: false });
  const config = useQuery({
    queryKey: ['tenant-uplink-auto-reply'],
    queryFn: getTenantUplinkAutoReply,
    retry: false,
    refetchOnWindowFocus: false,
  });

  useEffect(() => {
    if (!config.data) return;
    setEnabled(config.data.enabled);
    setKeyword(config.data.keyword ?? '');
    setTemplateId(config.data.templateId ?? '');
    setResponseContent(config.data.responseContent ?? '');
    setLoopGuardMinutes(config.data.loopGuardMinutes);
    setAuditReason(config.data.auditReason ?? '租户配置上行自动回复');
  }, [config.data]);

  const save = useMutation({
    mutationFn: () => saveTenantUplinkAutoReply({ enabled, keyword, templateId, responseContent, loopGuardMinutes, auditReason }),
    onSuccess: async () => {
      setMessage('自动回复配置已保存');
      setError('');
      await queryClient.invalidateQueries({ queryKey: ['tenant-uplink-auto-reply'] });
    },
    onError: (failure) => {
      setError(mutationErrorMessage(failure, '自动回复配置保存失败'));
      setMessage('');
    },
  });

  const setFilter = (key: keyof typeof draftFilters, value: string) => setDraftFilters((current) => ({ ...current, [key]: value }));

  return (
    <section className="uplink-page" data-testid="tenant-uplinks">
      <nav aria-label="面包屑">机构端 / 上行消息</nav>
      <header className="uplink-header">
        <div>
          <h1>上行消息查询</h1>
          <p className="page-description">仅展示当前租户的上行消息，可按号码、关键词、运营商和推送状态查询。</p>
        </div>
        <button type="button" data-testid="tenant-uplink-normalization-uplinks-refresh" onClick={() => void queryClient.invalidateQueries({ queryKey: ['tenant-uplinks'] })}>刷新</button>
      </header>

      {message && <p role="status" className="uplink-alert success" data-testid="tenant-uplink-normalization-operation-message">{message}</p>}
      {error && <p role="alert" className="uplink-alert error" data-testid="tenant-uplink-normalization-operation-error">{error}</p>}

      <section className="card uplink-filters">
        <label>手机号<input data-testid="tenant-uplink-normalization-uplinks-filter-number" value={draftFilters.phoneNumber} onChange={(event) => setFilter('phoneNumber', event.target.value)} /></label>
        <label>关键词<input data-testid="tenant-uplink-normalization-uplinks-filter-keyword" value={draftFilters.keyword} onChange={(event) => setFilter('keyword', event.target.value)} /></label>
        <label>运营商<input data-testid="tenant-uplink-normalization-uplinks-filter-carrier" value={draftFilters.carrier} onChange={(event) => setFilter('carrier', event.target.value)} /></label>
        <label>推送状态<select data-testid="tenant-uplink-normalization-uplinks-filter-push-state" value={draftFilters.pushState} onChange={(event) => setFilter('pushState', event.target.value)}><option value="">全部</option><option value="PENDING">PENDING</option><option value="DELIVERED">DELIVERED</option><option value="PUSH_FAILED">PUSH_FAILED</option><option value="NOT_CONFIGURED">NOT_CONFIGURED</option></select></label>
        <button type="button" data-testid="tenant-uplink-normalization-uplinks-search" onClick={() => setFilters(draftFilters)}>查询</button>
      </section>

      <section className="card">
        <h2>上行记录</h2>
        {uplinks.isLoading && <p>正在加载上行记录…</p>}
        {uplinks.isError && <p role="alert">上行记录加载失败。</p>}
        <table className="uplink-table" data-testid="tenant-uplink-normalization-uplinks-table">
          <thead>
            <tr><th>来源</th><th>手机号</th><th>内容关键词</th><th>状态</th><th>运营商</th><th>目的地</th><th>位置</th><th>接收时间</th></tr>
          </thead>
          <tbody>
            {(uplinks.data ?? []).map((row) => (
              <tr key={row.id} data-testid="tenant-uplink-normalization-uplinks-row">
                <td>{row.sourceProtocol}/{row.sourceConnector}</td>
                <td>{row.phoneMasked}</td>
                <td>{row.contentKeyword ?? '-'}</td>
                <td>{row.state}/{row.pushState}</td>
                <td>{row.carrier ?? '-'}</td>
                <td>{row.destination ?? '-'}</td>
                <td>{row.province ?? '-'}/{row.city ?? '-'}</td>
                <td>{row.receiveTime ?? '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section className="card uplink-auto-reply" data-testid="tenant-uplink-normalization-uplinks-auto-reply-config">
        <h2>上行自动回复</h2>
        <p>自动回复仅在当前租户范围内生效，必须设置防循环窗口并记录变更原因。</p>
        <label className="inline"><input data-testid="tenant-uplink-normalization-auto-reply-enabled" type="checkbox" checked={enabled} onChange={(event) => setEnabled(event.target.checked)} />启用自动回复</label>
        <label>触发关键词<input data-testid="tenant-uplink-normalization-auto-reply-keyword" value={keyword} onChange={(event) => setKeyword(event.target.value)} /></label>
        <label>模板ID<input data-testid="tenant-uplink-normalization-auto-reply-template" value={templateId} onChange={(event) => setTemplateId(event.target.value)} /></label>
        <label>回复内容<textarea data-testid="tenant-uplink-normalization-auto-reply-content" value={responseContent} onChange={(event) => setResponseContent(event.target.value)} /></label>
        <label>防循环分钟<input data-testid="tenant-uplink-normalization-auto-reply-loop-guard" type="number" value={loopGuardMinutes} onChange={(event) => setLoopGuardMinutes(Number(event.target.value))} /></label>
        <label>变更原因<input data-testid="tenant-uplink-normalization-auto-reply-audit-reason" value={auditReason} onChange={(event) => setAuditReason(event.target.value)} /></label>
        <button type="button" data-testid="tenant-uplink-normalization-auto-reply-save" onClick={() => save.mutate()} disabled={save.isPending}>保存自动回复</button>
      </section>
    </section>
  );
}
