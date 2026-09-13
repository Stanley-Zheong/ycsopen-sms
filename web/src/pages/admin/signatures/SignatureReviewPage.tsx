import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import ModalDialog from '@/components/common/ModalDialog';
import { QueryField, QueryPanel } from '@/components/common/QueryPanel';
import {
  decideSignature,
  listSignatureFilings,
  listSignatureReviewQueue,
  recordSignatureFilingResult,
  requestSignatureFiling,
  type SignatureFiling,
  type SignatureRecord,
} from '@/api/signatureLifecycleApi';
import { mutationErrorMessage } from '@/api/client';
import { isPlatformRole, useAuthStore } from '@/store/authStore';
import '@/styles/signature-lifecycle.css';

export default function SignatureReviewPage() {
  const userType = useAuthStore((state) => state.userType);
  const canReview = userType === 'ADMIN' || userType === 'OPERATOR';
  const [keywordDraft, setKeywordDraft] = useState('');
  const [keyword, setKeyword] = useState('');
  const [decisionTarget, setDecisionTarget] = useState<SignatureRecord | null>(null);
  const [decisionValue, setDecisionValue] = useState<'APPROVE' | 'REJECT' | 'SUPPLEMENT_REQUIRED'>('APPROVE');
  const [opinion, setOpinion] = useState('');
  const [filingTarget, setFilingTarget] = useState<SignatureRecord | null>(null);
  const [filings, setFilings] = useState<SignatureFiling[]>([]);
  const [filingResultStatus, setFilingResultStatus] = useState<'REGISTERED' | 'FAILED'>('REGISTERED');
  const [resultMessage, setResultMessage] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  const queue = useQuery({
    queryKey: ['signature-review', keyword],
    queryFn: () => listSignatureReviewQueue(keyword),
    retry: false,
    enabled: isPlatformRole(userType) && canReview,
  });
  const summary = queue.data?.summary;
  const rows = queue.data?.items ?? [];

  const decide = useMutation({
    mutationFn: () => {
      if (!decisionTarget) throw new Error('未选择签名');
      return decideSignature(decisionTarget.id, decisionValue, opinion);
    },
    onSuccess: () => {
      setDecisionTarget(null);
      setDecisionValue('APPROVE');
      setOpinion('');
      setMessage('审核结果已保存。');
      setError('');
    },
    onError: (failure) => setError(failure instanceof Error ? failure.message : mutationErrorMessage(failure, '审核保存失败')),
  });

  const openFilings = async (signature: SignatureRecord) => {
    setFilingTarget(signature);
    setFilings(await listSignatureFilings(signature.id));
    setMessage('');
    setError('');
  };

  const filingRequest = useMutation({
    mutationFn: async () => {
      if (!filingTarget) throw new Error('未选择签名');
      const row = filings.find((item) => item.status === 'NONE' || item.status === 'FAILED');
      if (!row) throw new Error('没有可报备通道');
      return requestSignatureFiling(filingTarget.id, row.channelId);
    },
    onSuccess: (updated) => {
      setFilings((current) => current.map((row) => row.channelId === updated.channelId ? updated : row));
      setMessage('报备请求已提交。');
      setError('');
    },
    onError: (failure) => setError(failure instanceof Error ? failure.message : mutationErrorMessage(failure, '报备请求失败')),
  });

  const filingResult = useMutation({
    mutationFn: async (statusOverride?: 'REGISTERED' | 'FAILED') => {
      if (!filingTarget) throw new Error('未选择签名');
      const row = filings.find((item) => item.status === 'REGISTERING');
      if (!row) throw new Error('没有进行中的报备请求');
      return recordSignatureFilingResult(filingTarget.id, row.channelId, statusOverride ?? filingResultStatus, resultMessage);
    },
    onSuccess: (updated) => {
      setFilings((current) => current.map((row) => row.channelId === updated.channelId ? updated : row));
      setMessage('报备结果已记录。');
      setError('');
    },
    onError: (failure) => setError(failure instanceof Error ? failure.message : mutationErrorMessage(failure, '报备结果保存失败')),
  });

  if (!isPlatformRole(userType) || !canReview) {
    return <p role="alert">无权查看签名审核。</p>;
  }

  return (
    <section data-testid="admin-signature-lifecycle-signature-review-page">
      <nav aria-label="面包屑">审核中心 / 签名审核</nav>
      <header className="signature-lifecycle-header">
        <div>
          <h1>签名审核</h1>
          <p className="page-description">审核签名申请并跟踪各上游通道报备结果。</p>
        </div>
      </header>
      {message && <p role="status" className="signature-lifecycle-alert success">{message}</p>}
      {error && <p role="alert" className="signature-lifecycle-alert error">{error}</p>}

      <section className="signature-lifecycle-stats" data-testid="admin-signature-lifecycle-signature-review-stats">
        <span>总数 {summary?.total ?? 0}</span>
        <span>待审核 {summary?.pending ?? 0}</span>
        <span>已通过 {summary?.approved ?? 0}</span>
        <span>已拒绝 {summary?.rejected ?? 0}</span>
        <span>需补充 {summary?.supplementRequired ?? 0}</span>
        <span>高风险 {summary?.highRisk ?? 0}</span>
      </section>

      <QueryPanel
        onSubmit={() => setKeyword(keywordDraft)}
        onReset={() => {
          setKeywordDraft('');
          setKeyword('');
        }}
        result={(
          <>
            {queue.isLoading && <p>正在加载…</p>}
            {queue.isError && <p role="alert">签名审核数据加载失败。</p>}
            {!queue.isLoading && !queue.isError && rows.length === 0 && <p>暂无签名审核数据。</p>}
            {rows.length > 0 && (
              <table className="ratio-table">
                <thead><tr><th>签名</th><th>机构</th><th>类型</th><th>风险</th><th>状态</th><th>材料</th><th>操作</th></tr></thead>
                <tbody>
                  {rows.map((row) => (
                    <tr key={row.id} data-testid="admin-signature-lifecycle-signature-review-row">
                      <td>{row.signContent}</td>
                      <td>{row.tenantId}</td>
                      <td>{row.signType} / {row.usageType}</td>
                      <td>{row.riskLevel}</td>
                      <td>{row.auditStatus}</td>
                      <td>{row.evidenceRef ?? '未提交'}</td>
                      <td>
                        <button type="button" data-testid="admin-signature-lifecycle-signature-review-decision-open" onClick={() => setDecisionTarget(row)}>审核</button>
                        <button type="button" data-testid="admin-signature-lifecycle-signature-filing-matrix-open" onClick={() => void openFilings(row)}>报备</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </>
        )}
      >
        <QueryField name="keyword" label="筛选签名">
          <input data-testid="admin-signature-lifecycle-signature-review-filters" value={keywordDraft} onChange={(event) => setKeywordDraft(event.target.value)} />
        </QueryField>
      </QueryPanel>

      {decisionTarget && (
        <ModalDialog labelledBy="signature-review-decision-title" onRequestClose={() => setDecisionTarget(null)}>
          <h2 id="signature-review-decision-title">审核签名：{decisionTarget.signContent}</h2>
          <label>审核结论
            <select
              data-testid="admin-signature-lifecycle-signature-review-decision-status"
              value={decisionValue}
              onChange={(event) => setDecisionValue(event.target.value as 'APPROVE' | 'REJECT' | 'SUPPLEMENT_REQUIRED')}
            >
              <option value="APPROVE">通过</option>
              <option value="REJECT">驳回</option>
              <option value="SUPPLEMENT_REQUIRED">要求补充材料</option>
            </select>
          </label>
          <label>审核意见
            <textarea data-testid="admin-signature-lifecycle-signature-review-decision-opinion" value={opinion} onChange={(event) => setOpinion(event.target.value)} />
          </label>
          <button type="button" data-testid="admin-signature-lifecycle-signature-review-decision" onClick={() => decide.mutate()} disabled={decide.isPending}>保存审核结果</button>
        </ModalDialog>
      )}

      {filingTarget && (
        <ModalDialog labelledBy="signature-filing-title" onRequestClose={() => setFilingTarget(null)}>
          <h2 id="signature-filing-title">通道报备：{filingTarget.signContent}</h2>
          <div data-testid="admin-signature-lifecycle-signature-filing-matrix">
            {filings.map((row) => (
              <p key={row.channelId}>{row.channelName} / {row.status} / {row.attemptCount} / {row.channelEligibilityReason}</p>
            ))}
          </div>
          <button type="button" data-testid="admin-signature-lifecycle-signature-filing-retry" onClick={() => filingRequest.mutate()} disabled={filingRequest.isPending}>提交/重试报备</button>
          <label>报备结果
            <select
              data-testid="admin-signature-lifecycle-signature-filing-result-status"
              value={filingResultStatus}
              onChange={(event) => setFilingResultStatus(event.target.value as 'REGISTERED' | 'FAILED')}
            >
              <option value="REGISTERED">已报备</option>
              <option value="FAILED">报备失败</option>
            </select>
          </label>
          <label>结果说明
            <input data-testid="admin-signature-lifecycle-signature-filing-result-message" value={resultMessage} onChange={(event) => setResultMessage(event.target.value)} />
          </label>
          <button type="button" data-testid="admin-signature-lifecycle-signature-filing-result-registered" onClick={() => filingResult.mutate('REGISTERED')} disabled={filingResult.isPending}>记录已报备</button>
          <button type="button" data-testid="admin-signature-lifecycle-signature-filing-result-failed" onClick={() => filingResult.mutate('FAILED')} disabled={filingResult.isPending}>记录失败</button>
        </ModalDialog>
      )}
    </section>
  );
}
