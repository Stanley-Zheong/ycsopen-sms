import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { approveShortLink, inspectShortLink, listShortLinkReview, rejectShortLink, type ShortLinkRow } from '@/api/shortLinkApi';
import { mutationErrorMessage } from '@/api/client';
import '@/styles/shortlink-safety.css';

function autoEvidence(row: ShortLinkRow): string {
  try {
    const parsed = JSON.parse(row.automatedResultJson);
    return `${parsed.verdict ?? '-'} / ${(parsed.checks ?? []).join('、')}`;
  } catch {
    return row.automatedResultJson;
  }
}

export default function AdminShortLinkReviewPage() {
  const queryClient = useQueryClient();
  const [opinion, setOpinion] = useState('自动审核通过，域名证据完整');
  const [observedTargetUrl, setObservedTargetUrl] = useState('https://example.com/campaign');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const queue = useQuery({ queryKey: ['admin-shortlink-review'], queryFn: () => listShortLinkReview('PENDING'), retry: false });
  const refresh = async () => queryClient.invalidateQueries({ queryKey: ['admin-shortlink-review'] });
  const onSuccess = async (text: string) => {
    setMessage(text);
    setError('');
    await refresh();
  };
  const onError = (failure: unknown, fallback: string) => {
    setMessage('');
    setError(mutationErrorMessage(failure, fallback));
  };
  const approve = useMutation({
    mutationFn: (row: ShortLinkRow) => approveShortLink(row.id, opinion),
    onSuccess: () => void onSuccess('短链已批准'),
    onError: (failure) => onError(failure, '短链批准失败'),
  });
  const reject = useMutation({
    mutationFn: (row: ShortLinkRow) => rejectShortLink(row.id, opinion),
    onSuccess: () => void onSuccess('短链已驳回'),
    onError: (failure) => onError(failure, '短链驳回失败'),
  });
  const inspect = useMutation({
    mutationFn: (row: ShortLinkRow) => inspectShortLink(row.id, observedTargetUrl),
    onSuccess: () => void onSuccess('巡检已完成'),
    onError: (failure) => onError(failure, '巡检失败'),
  });

  return (
    <section className="shortlink-page" data-testid="admin-shortlink-safety-shortlinks-review-page">
      <nav aria-label="面包屑">平台管理 / 工具管理 / 短链审核</nav>
      <header className="shortlink-header">
        <div>
          <h1>短链安全审核</h1>
          <p className="page-description">审核不可变目标版本、自动风险结果、域名证据，并控制批准、驳回和巡检下线。</p>
        </div>
        <button type="button" onClick={() => void refresh()} data-testid="admin-shortlink-safety-shortlinks-review-refresh">刷新</button>
      </header>

      {message && <p role="status" className="shortlink-message success" data-testid="admin-shortlink-safety-shortlinks-review-message">{message}</p>}
      {error && <p role="alert" className="shortlink-message error" data-testid="admin-shortlink-safety-shortlinks-review-error">{error}</p>}

      <section className="card shortlink-form">
        <label>审核意见<input data-testid="admin-shortlink-safety-shortlinks-opinion" value={opinion} onChange={(event) => setOpinion(event.target.value)} /></label>
        <label>巡检目标<input data-testid="admin-shortlink-safety-shortlinks-inspect-target" value={observedTargetUrl} onChange={(event) => setObservedTargetUrl(event.target.value)} /></label>
      </section>

      <section className="card">
        <h2>待审核短链</h2>
        <table className="shortlink-table" data-testid="admin-shortlink-safety-shortlinks-review-table">
          <thead><tr><th>机构</th><th>不可变目标</th><th>版本</th><th>证据</th><th>自动结果</th><th>风险</th><th>动作</th></tr></thead>
          <tbody>
            {(queue.data ?? []).map((row) => (
              <tr key={row.id} data-testid="admin-shortlink-safety-shortlinks-review-row">
                <td>{row.tenantId}</td>
                <td data-testid="admin-shortlink-safety-shortlinks-target-version">{row.targetUrl} / sha256:{row.immutableTargetSha256.slice(0, 8)}</td>
                <td>{row.targetVersion}</td>
                <td data-testid="admin-shortlink-safety-shortlinks-domain-evidence">{row.screenshotEvidenceRef ?? '-'} / {row.domainEvidenceJson ?? '-'}</td>
                <td data-testid="admin-shortlink-safety-shortlinks-auto-result">{autoEvidence(row)}</td>
                <td>{row.riskLevel}</td>
                <td>
                  <button type="button" data-testid="admin-shortlink-safety-shortlinks-approve" onClick={() => approve.mutate(row)}>批准</button>
                  <button type="button" data-testid="admin-shortlink-safety-shortlinks-reject" onClick={() => reject.mutate(row)}>驳回</button>
                  <button type="button" data-testid="admin-shortlink-safety-shortlinks-inspect" onClick={() => inspect.mutate(row)}>巡检</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </section>
  );
}
