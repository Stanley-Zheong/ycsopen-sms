import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  evaluateUnsubscribeAlerts,
  listAdminUnsubscribeKeywords,
  listAdminUnsubscribes,
  listUnsubscribeStatistics,
  saveAdminUnsubscribeKeyword,
} from '@/api/unsubscribeComplianceApi';
import { mutationErrorMessage } from '@/api/client';
import '@/styles/unsubscribe-compliance.css';

export default function AdminUnsubscribesPage() {
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState({ tenantId: '', mobile: '', keyword: '', outcome: '', signatureId: '', productCode: '', notificationState: '' });
  const [filters, setFilters] = useState(draft);
  const [keywordTenant, setKeywordTenant] = useState('');
  const [keywordText, setKeywordText] = useState('TD');
  const [keywordScope, setKeywordScope] = useState('GLOBAL');
  const [thresholdRate, setThresholdRate] = useState('0.03');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const applied = useMemo(() => filters, [filters]);
  const records = useQuery({ queryKey: ['admin-unsubscribes', applied], queryFn: () => listAdminUnsubscribes(applied), retry: false });
  const keywords = useQuery({ queryKey: ['admin-unsubscribe-keywords', keywordTenant], queryFn: () => listAdminUnsubscribeKeywords(keywordTenant), retry: false });
  const statistics = useQuery({ queryKey: ['admin-unsubscribe-statistics', applied], queryFn: () => listUnsubscribeStatistics(applied), retry: false });

  const saveKeyword = useMutation({
    mutationFn: () => saveAdminUnsubscribeKeyword({
      keyword: keywordText,
      scope: keywordScope,
      tenantId: keywordTenant ? Number(keywordTenant) : null,
    }),
    onSuccess: async () => {
      setMessage('退订关键词已保存');
      setError('');
      await queryClient.invalidateQueries({ queryKey: ['admin-unsubscribe-keywords'] });
    },
    onError: (failure) => {
      setMessage('');
      setError(mutationErrorMessage(failure, '退订关键词保存失败'));
    },
  });
  const evaluateAlerts = useMutation({
    mutationFn: () => evaluateUnsubscribeAlerts({ ...filters, thresholdRate: Number(thresholdRate) }),
    onSuccess: (rows) => {
      setMessage(`告警评估完成：${rows.length} 条`);
      setError('');
    },
    onError: (failure) => {
      setMessage('');
      setError(mutationErrorMessage(failure, '告警评估失败'));
    },
  });

  const setField = (key: keyof typeof draft, value: string) => setDraft((current) => ({ ...current, [key]: value }));

  return (
    <section className="unsubscribe-page" data-testid="admin-unsubscribe-compliance-page">
      <nav aria-label="面包屑">平台管理 / 退订合规</nav>
      <header className="unsubscribe-header">
        <div>
          <h1>退订合规</h1>
          <p>维护全局/租户退订关键词，查看退订证据，并用真实证据和最终发送量计算退订率。</p>
        </div>
        <button type="button" data-testid="admin-unsubscribe-compliance-refresh" onClick={() => void queryClient.invalidateQueries()}>刷新</button>
      </header>

      {message && <p role="status" className="unsubscribe-alert success" data-testid="admin-unsubscribe-compliance-message">{message}</p>}
      {error && <p role="alert" className="unsubscribe-alert error" data-testid="admin-unsubscribe-compliance-error">{error}</p>}

      <section className="card unsubscribe-filters">
        <label>租户ID<input data-testid="admin-unsubscribe-compliance-filter-tenant" value={draft.tenantId} onChange={(event) => setField('tenantId', event.target.value)} /></label>
        <label>手机号<input data-testid="admin-unsubscribe-compliance-filter-mobile" value={draft.mobile} onChange={(event) => setField('mobile', event.target.value)} /></label>
        <label>关键词<input data-testid="admin-unsubscribe-compliance-filter-keyword" value={draft.keyword} onChange={(event) => setField('keyword', event.target.value)} /></label>
        <label>结果<input data-testid="admin-unsubscribe-compliance-filter-outcome" value={draft.outcome} onChange={(event) => setField('outcome', event.target.value)} /></label>
        <label>签名ID<input data-testid="admin-unsubscribe-compliance-filter-signature" value={draft.signatureId} onChange={(event) => setField('signatureId', event.target.value)} /></label>
        <label>产品<input data-testid="admin-unsubscribe-compliance-filter-product" value={draft.productCode} onChange={(event) => setField('productCode', event.target.value)} /></label>
        <label>通知状态<input data-testid="admin-unsubscribe-compliance-filter-notification" value={draft.notificationState} onChange={(event) => setField('notificationState', event.target.value)} /></label>
        <button type="button" data-testid="admin-unsubscribe-compliance-search" onClick={() => setFilters(draft)}>查询</button>
      </section>

      <section className="card" data-testid="admin-unsubscribe-compliance-keywords-page">
        <h2>退订关键词库</h2>
        <div className="unsubscribe-filters compact">
          <label>关键词<input data-testid="admin-unsubscribe-compliance-keyword-input" value={keywordText} onChange={(event) => setKeywordText(event.target.value)} /></label>
          <label>范围<select data-testid="admin-unsubscribe-compliance-keyword-scope" value={keywordScope} onChange={(event) => setKeywordScope(event.target.value)}><option value="GLOBAL">GLOBAL</option><option value="TENANT">TENANT</option></select></label>
          <label>租户ID<input data-testid="admin-unsubscribe-compliance-keyword-tenant" value={keywordTenant} onChange={(event) => setKeywordTenant(event.target.value)} /></label>
          <button type="button" data-testid="admin-unsubscribe-compliance-keyword-save" onClick={() => saveKeyword.mutate()}>保存关键词</button>
        </div>
        <table className="unsubscribe-table" data-testid="admin-unsubscribe-compliance-keywords-table">
          <thead><tr><th>关键词</th><th>归一化</th><th>范围</th><th>租户</th><th>状态</th></tr></thead>
          <tbody>{(keywords.data ?? []).map((row) => (
            <tr key={row.id} data-testid="admin-unsubscribe-compliance-keyword-row"><td>{row.keyword}</td><td>{row.keywordNormalized}</td><td>{row.scope}</td><td>{row.tenantId ?? '-'}</td><td>{row.status}</td></tr>
          ))}</tbody>
        </table>
      </section>

      <section className="card" data-testid="admin-unsubscribe-compliance-unsubscribes-page">
        <h2>退订证据</h2>
        {records.isLoading && <p>正在加载退订证据…</p>}
        {records.isError && <p role="alert">退订证据加载失败。</p>}
        <table className="unsubscribe-table" data-testid="admin-unsubscribe-compliance-unsubscribes-table">
          <thead><tr><th>租户</th><th>手机号</th><th>关键词</th><th>签名/产品</th><th>处理</th><th>通知</th><th>上行</th><th>时间</th></tr></thead>
          <tbody>{(records.data ?? []).map((row) => (
            <tr key={row.id} data-testid="admin-unsubscribe-compliance-unsubscribe-row">
              <td>{row.tenantId}</td><td>{row.maskedMobile ?? '-'}</td><td>{row.triggerKeyword ?? '-'}</td>
              <td>{row.signatureId ?? '-'}/{row.productCode ?? '-'}</td><td>{row.handlingState}</td>
              <td data-testid="admin-unsubscribe-compliance-unsubscribes-notification-state">{row.notificationState}</td>
              <td>{row.uplinkRecordId ?? '-'}</td><td>{row.unsubscribedAt ?? '-'}</td>
            </tr>
          ))}</tbody>
        </table>
      </section>

      <section className="card" data-testid="admin-unsubscribe-compliance-statistics-page">
        <header className="unsubscribe-subheader">
          <div>
            <h2>退订统计与告警</h2>
            <p>退订率 = unsubscribe_count / final_sent_count，final_sent_count 只统计 SENT/DELIVERED。</p>
          </div>
          <label>阈值<input data-testid="admin-unsubscribe-compliance-alert-threshold" value={thresholdRate} onChange={(event) => setThresholdRate(event.target.value)} /></label>
          <button type="button" data-testid="admin-unsubscribe-compliance-alert-evaluate" onClick={() => evaluateAlerts.mutate()}>评估告警</button>
        </header>
        <table className="unsubscribe-table" data-testid="admin-unsubscribe-compliance-statistics-table">
          <thead><tr><th>租户</th><th>签名/产品</th><th>退订数</th><th>最终发送数</th><th>退订率</th></tr></thead>
          <tbody>{(statistics.data ?? []).map((row) => (
            <tr key={`${row.tenantId}-${row.signatureId}-${row.productCode}`} data-testid="admin-unsubscribe-compliance-statistics-row">
              <td>{row.tenantId}</td><td>{row.signatureId ?? '-'}/{row.productCode ?? '-'}</td><td>{row.unsubscribeCount}</td><td>{row.finalSentCount}</td><td>{(row.rate * 100).toFixed(2)}%</td>
            </tr>
          ))}</tbody>
        </table>
      </section>
    </section>
  );
}
