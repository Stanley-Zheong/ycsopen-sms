import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import ModalDialog from '@/components/common/ModalDialog';
import { QueryField, QueryPanel } from '@/components/common/QueryPanel';
import { decideTemplate, listTemplateReviewQueue, type TemplateRecord } from '@/api/templateLifecycleApi';
import { mutationErrorMessage } from '@/api/client';
import { isPlatformRole, useAuthStore } from '@/store/authStore';
import '@/styles/template-lifecycle.css';

export default function TemplateReviewPage() {
  const userType = useAuthStore((state) => state.userType);
  const canReview = userType === 'ADMIN' || userType === 'OPERATOR';
  const [keywordDraft, setKeywordDraft] = useState('');
  const [keyword, setKeyword] = useState('');
  const [decisionTarget, setDecisionTarget] = useState<TemplateRecord | null>(null);
  const [decision, setDecision] = useState<'APPROVE' | 'REJECT' | 'AMENDMENT_REQUIRED'>('APPROVE');
  const [opinion, setOpinion] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const queryClient = useQueryClient();

  const queue = useQuery({
    queryKey: ['template-review', keyword],
    queryFn: () => listTemplateReviewQueue(keyword),
    retry: false,
    enabled: isPlatformRole(userType) && canReview,
  });

  const mutation = useMutation({
    mutationFn: (decisionOverride?: 'APPROVE' | 'REJECT' | 'AMENDMENT_REQUIRED') => {
      if (!decisionTarget) throw new Error('未选择模板');
      return decideTemplate(decisionTarget.id, decisionOverride ?? decision, opinion);
    },
    onSuccess: async () => {
      setDecisionTarget(null);
      setDecision('APPROVE');
      setOpinion('');
      setMessage('模板审核结果已保存。');
      setError('');
      await queryClient.invalidateQueries({ queryKey: ['template-review'] });
    },
    onError: (failure) => setError(mutationErrorMessage(failure, '模板审核失败')),
  });

  if (!isPlatformRole(userType) || !canReview) {
    return <p role="alert">无权查看模板审核。</p>;
  }

  const summary = queue.data?.summary;
  const rows = queue.data?.items ?? [];

  return (
    <section data-testid="admin-template-lifecycle-template-review-page">
      <nav aria-label="面包屑">审核中心 / 模板审核</nav>
      <header className="template-lifecycle-header">
        <div>
          <h1>模板审核</h1>
          <p className="page-description">按模板名称、机构、状态和类型筛选，审核变量与签名绑定。</p>
        </div>
      </header>
      {message && <p role="status" className="template-lifecycle-alert success">{message}</p>}
      {error && <p role="alert" className="template-lifecycle-alert error">{error}</p>}

      <section className="template-lifecycle-stats" data-testid="admin-template-lifecycle-template-review-stats">
        <span>总数 {summary?.total ?? 0}</span>
        <span>待审核 {summary?.pending ?? 0}</span>
        <span>已通过 {summary?.approved ?? 0}</span>
        <span>已拒绝 {summary?.rejected ?? 0}</span>
        <span>需修改 {summary?.amendmentRequired ?? 0}</span>
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
            {queue.isError && <p role="alert">模板审核数据加载失败。</p>}
            {!queue.isLoading && !queue.isError && rows.length === 0 && <p>暂无模板审核数据。</p>}
            {rows.length > 0 && (
              <table className="ratio-table">
                <thead><tr><th>模板</th><th>机构</th><th>类型</th><th>签名</th><th>变量</th><th>状态</th><th>操作</th></tr></thead>
                <tbody>
                  {rows.map((row) => (
                    <tr key={row.id} data-testid="admin-template-lifecycle-template-review-row">
                      <td>{row.templateName}<br />{row.content}</td>
                      <td>{row.tenantId}</td>
                      <td>{row.templateType}</td>
                      <td>{row.signatureId}</td>
                      <td>{row.variableNames.join(', ') || '无变量'}</td>
                      <td>{row.auditStatus}</td>
                      <td><button type="button" data-testid="admin-template-lifecycle-template-review-decision-open" onClick={() => setDecisionTarget(row)}>审核</button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </>
        )}
      >
        <QueryField name="keyword" label="筛选模板">
          <input data-testid="admin-template-lifecycle-template-review-filters" value={keywordDraft} onChange={(event) => setKeywordDraft(event.target.value)} />
        </QueryField>
      </QueryPanel>

      {decisionTarget && (
        <ModalDialog labelledBy="template-review-decision-title" onRequestClose={() => setDecisionTarget(null)}>
          <h2 id="template-review-decision-title">审核模板：{decisionTarget.templateName}</h2>
          <label>审核结论
            <select data-testid="admin-template-lifecycle-template-review-decision-status" value={decision} onChange={(event) => setDecision(event.target.value as 'APPROVE' | 'REJECT' | 'AMENDMENT_REQUIRED')}>
              <option value="APPROVE">通过</option>
              <option value="REJECT">驳回</option>
              <option value="AMENDMENT_REQUIRED">要求修改</option>
            </select>
          </label>
          <label>审核意见
            <textarea data-testid="admin-template-lifecycle-template-review-decision-opinion" value={opinion} onChange={(event) => setOpinion(event.target.value)} />
          </label>
          <button type="button" data-testid="admin-template-lifecycle-template-review-decision" onClick={() => mutation.mutate()} disabled={mutation.isPending}>保存模板审核</button>
          <button type="button" data-testid="admin-template-lifecycle-template-review-approve" onClick={() => mutation.mutate('APPROVE')} disabled={mutation.isPending}>快速通过</button>
          <button type="button" data-testid="admin-template-lifecycle-template-review-reject" onClick={() => mutation.mutate('REJECT')} disabled={mutation.isPending}>快速驳回</button>
        </ModalDialog>
      )}
    </section>
  );
}
