import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  listTenantUnsubscribeKeywords,
  listTenantUnsubscribes,
  requestTenantUnsubscribeExport,
  saveTenantUnsubscribeKeyword,
} from '@/api/unsubscribeComplianceApi';
import { mutationErrorMessage } from '@/api/client';
import ModalDialog from '@/components/common/ModalDialog';
import { QueryField, QueryPanel } from '@/components/common/QueryPanel';
import '@/styles/unsubscribe-compliance.css';

const EMPTY_FILTERS = { mobile: '', keyword: '', outcome: '', signatureId: '', productCode: '', notificationState: '' };

export default function TenantUnsubscribesPage() {
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState({ ...EMPTY_FILTERS });
  const [filters, setFilters] = useState({ ...EMPTY_FILTERS });
  const [keyword, setKeyword] = useState('STOP');
  const [keywordOpen, setKeywordOpen] = useState(false);
  const [keywordError, setKeywordError] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const records = useQuery({ queryKey: ['tenant-unsubscribes', filters], queryFn: () => listTenantUnsubscribes(filters), retry: false });
  const keywords = useQuery({ queryKey: ['tenant-unsubscribe-keywords'], queryFn: listTenantUnsubscribeKeywords, retry: false });
  const saveKeyword = useMutation({
    mutationFn: () => saveTenantUnsubscribeKeyword({ keyword, scope: 'TENANT', status: 'ACTIVE' }),
    onSuccess: async () => {
      setMessage('租户退订关键词已保存');
      setError('');
      await queryClient.invalidateQueries({ queryKey: ['tenant-unsubscribe-keywords'] });
      setKeywordOpen(false);
      setKeywordError('');
      setKeyword('STOP');
    },
    onError: (failure) => {
      setMessage('');
      setKeywordError(mutationErrorMessage(failure, '租户退订关键词保存失败'));
    },
  });
  const exportRequest = useMutation({
    mutationFn: () => requestTenantUnsubscribeExport(filters),
    onSuccess: (result) => {
      setMessage(`导出任务已创建：${result.taskId}`);
      setError('');
    },
    onError: (failure) => {
      setMessage('');
      setError(mutationErrorMessage(failure, '退订证据导出请求失败'));
    },
  });
  const setField = (key: keyof typeof draft, value: string) => setDraft((current) => ({ ...current, [key]: value }));
  const resetFilters = () => {
    setDraft({ ...EMPTY_FILTERS });
    setFilters({ ...EMPTY_FILTERS });
  };

  return (
    <section className="unsubscribe-page" data-testid="tenant-unsubscribe-compliance-unsubscribes-page">
      <nav aria-label="面包屑">机构端 / 退订合规</nav>
      <header className="unsubscribe-header">
        <div>
          <h1>退订证据</h1>
          <p>查询本机构退订证据、通知状态和处理结果，并维护本机构扩展退订关键词。</p>
        </div>
        <div>
          <button type="button" data-testid="tenant-unsubscribe-compliance-refresh" onClick={() => void queryClient.invalidateQueries()}>刷新</button>
          <button type="button" data-testid="tenant-unsubscribe-compliance-export-request" onClick={() => exportRequest.mutate()}>请求导出</button>
        </div>
      </header>

      {message && <p role="status" className="unsubscribe-alert success" data-testid="tenant-unsubscribe-compliance-message">{message}</p>}
      {error && <p role="alert" className="unsubscribe-alert error" data-testid="tenant-unsubscribe-compliance-error">{error}</p>}

      <QueryPanel
        onSubmit={() => setFilters({ ...draft })}
        onReset={resetFilters}
        submitLegacyTestId="tenant-unsubscribe-compliance-search"
        result={(
          <section>
            <h2>退订证据列表</h2>
            {records.isLoading && <p>正在加载退订证据…</p>}
            {records.isError && <p role="alert">退订证据加载失败。</p>}
            <table className="unsubscribe-table" data-testid="tenant-unsubscribe-compliance-unsubscribes-table">
              <thead><tr><th>手机号</th><th>关键词</th><th>签名/产品</th><th>处理</th><th>通知</th><th>确认</th><th>上行</th><th>时间</th></tr></thead>
              <tbody>{(records.data ?? []).map((row) => (
                <tr key={row.id} data-testid="tenant-unsubscribe-compliance-unsubscribe-row">
                  <td>{row.maskedMobile ?? '-'}</td><td>{row.triggerKeyword ?? '-'}</td>
                  <td>{row.signatureId ?? '-'}/{row.productCode ?? '-'}</td><td>{row.handlingState}</td>
                  <td data-testid="tenant-unsubscribe-compliance-unsubscribes-notification-state">{row.notificationState}</td>
                  <td>{row.confirmationState}</td><td>{row.uplinkRecordId ?? '-'}</td><td>{row.unsubscribedAt ?? '-'}</td>
                </tr>
              ))}</tbody>
            </table>
          </section>
        )}
      >
        <QueryField name="mobile" label="手机号"><input data-testid="tenant-unsubscribe-compliance-filter-mobile" value={draft.mobile} onChange={(event) => setField('mobile', event.target.value)} /></QueryField>
        <QueryField name="keyword" label="关键词"><input data-testid="tenant-unsubscribe-compliance-filter-keyword" value={draft.keyword} onChange={(event) => setField('keyword', event.target.value)} /></QueryField>
        <QueryField name="outcome" label="结果"><input data-testid="tenant-unsubscribe-compliance-filter-outcome" value={draft.outcome} onChange={(event) => setField('outcome', event.target.value)} /></QueryField>
        <QueryField name="signature-id" label="签名ID"><input data-testid="tenant-unsubscribe-compliance-filter-signature" value={draft.signatureId} onChange={(event) => setField('signatureId', event.target.value)} /></QueryField>
        <QueryField name="product-code" label="产品"><input data-testid="tenant-unsubscribe-compliance-filter-product" value={draft.productCode} onChange={(event) => setField('productCode', event.target.value)} /></QueryField>
        <QueryField name="notification-state" label="通知状态"><input data-testid="tenant-unsubscribe-compliance-filter-notification" value={draft.notificationState} onChange={(event) => setField('notificationState', event.target.value)} /></QueryField>
      </QueryPanel>

      <section className="card" data-testid="tenant-unsubscribe-compliance-keywords-card">
        <h2>本机构扩展关键词</h2>
        <button type="button" data-testid="tenant-unsubscribe-compliance-keyword-create-open" onClick={() => { setKeywordError(''); setKeywordOpen(true); }}>新增关键词</button>
        {keywordOpen && (
          <ModalDialog labelledBy="tenant-unsubscribe-keyword-create-title" onRequestClose={() => { setKeywordOpen(false); setKeywordError(''); }}>
            <form className="unsubscribe-filters compact" data-testid="tenant-unsubscribe-compliance-keyword-create-dialog" onSubmit={(event) => { event.preventDefault(); saveKeyword.mutate(); }}>
              <h2 id="tenant-unsubscribe-keyword-create-title">新增退订关键词</h2>
              <label>关键词<input required data-testid="tenant-unsubscribe-compliance-keyword-input" value={keyword} onChange={(event) => setKeyword(event.target.value)} /></label>
              {keywordError && <p role="alert" data-testid="tenant-unsubscribe-compliance-keyword-create-error" className="unsubscribe-alert error">{keywordError}</p>}
              <div className="dialog-actions">
                <button type="button" className="button-secondary" data-testid="tenant-unsubscribe-compliance-keyword-create-cancel" onClick={() => { setKeywordOpen(false); setKeywordError(''); }}>取消</button>
                <button type="submit" data-testid="tenant-unsubscribe-compliance-keyword-save" disabled={saveKeyword.isPending}>保存关键词</button>
              </div>
            </form>
          </ModalDialog>
        )}
        <table className="unsubscribe-table" data-testid="tenant-unsubscribe-compliance-keywords-table">
          <thead><tr><th>关键词</th><th>归一化</th><th>范围</th><th>状态</th></tr></thead>
          <tbody>{(keywords.data ?? []).map((row) => (
            <tr key={row.id} data-testid="tenant-unsubscribe-compliance-keyword-row"><td>{row.keyword}</td><td>{row.keywordNormalized}</td><td>{row.scope}</td><td>{row.status}</td></tr>
          ))}</tbody>
        </table>
      </section>

    </section>
  );
}
