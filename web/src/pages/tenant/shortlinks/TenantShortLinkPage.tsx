import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createShortLink, listTenantShortLinks, shortLinkAnalytics, type ShortLinkRow } from '@/api/shortLinkApi';
import { mutationErrorMessage } from '@/api/client';
import '@/styles/shortlink-safety.css';

function defaultDate(): string {
  const date = new Date();
  date.setDate(date.getDate() + 30);
  return date.toISOString().slice(0, 10);
}

function statusLabel(status: string): string {
  return ({ PENDING: '待审核', APPROVED: '已批准', REJECTED: '已拒绝', EXPIRED: '已过期', OFFLINE: '已下线', TAKEN_DOWN: '已下线' } as Record<string, string>)[status] ?? status;
}

function evidence(row: ShortLinkRow): string {
  try {
    const parsed = JSON.parse(row.automatedResultJson);
    return `${parsed.verdict ?? '-'} / ${(parsed.checks ?? []).join('、')}`;
  } catch {
    return row.automatedResultJson;
  }
}

export default function TenantShortLinkPage() {
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState({ originalUrl: 'https://example.com/campaign', customDomain: 's.ycsopen.test', validUntil: defaultDate() });
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const links = useQuery({ queryKey: ['tenant-shortlinks'], queryFn: listTenantShortLinks, retry: false });
  const analytics = useQuery({ queryKey: ['tenant-shortlink-analytics'], queryFn: shortLinkAnalytics, retry: false });
  const create = useMutation({
    mutationFn: () => createShortLink({ originalUrl: draft.originalUrl, customDomain: draft.customDomain, validUntil: draft.validUntil }),
    onSuccess: async () => {
      setMessage('短链已提交，等待自动审核与人工审核');
      setError('');
      await queryClient.invalidateQueries({ queryKey: ['tenant-shortlinks'] });
    },
    onError: (failure) => {
      setMessage('');
      setError(mutationErrorMessage(failure, '短链创建失败'));
    },
  });

  return (
    <section className="shortlink-page" data-testid="tenant-shortlink-safety-shortlinks-page">
      <nav aria-label="面包屑">机构端 / 短链管理</nav>
      <header className="shortlink-header">
        <div>
          <h1>短链创建与安全审核</h1>
          <p className="page-description">短链必须先完成自动风险检查和人工审核；未批准、拒绝、过期或下线状态不会跳转。</p>
        </div>
      </header>

      {message && <p role="status" className="shortlink-message success" data-testid="tenant-shortlink-safety-shortlinks-message">{message}</p>}
      {error && <p role="alert" className="shortlink-message error" data-testid="tenant-shortlink-safety-shortlinks-error">{error}</p>}

      <section className="card shortlink-form" data-testid="tenant-shortlink-safety-shortlinks-form">
        <label>原始URL
          <input data-testid="tenant-shortlink-safety-shortlinks-form-url" value={draft.originalUrl} maxLength={2000} onChange={(event) => setDraft({ ...draft, originalUrl: event.target.value })} />
        </label>
        <label>短链域名
          <input data-testid="tenant-shortlink-safety-shortlinks-form-domain" value={draft.customDomain} onChange={(event) => setDraft({ ...draft, customDomain: event.target.value })} />
        </label>
        <label>有效期
          <input type="date" data-testid="tenant-shortlink-safety-shortlinks-form-validity" value={draft.validUntil} onChange={(event) => setDraft({ ...draft, validUntil: event.target.value })} />
        </label>
        <button type="button" data-testid="tenant-shortlink-safety-shortlinks-create" onClick={() => create.mutate()} disabled={create.isPending}>提交短链</button>
      </section>

      <section className="card">
        <h2>短链列表</h2>
        <table className="shortlink-table" data-testid="tenant-shortlink-safety-shortlinks-list">
          <thead><tr><th>原始链接</th><th>短链</th><th>有效期</th><th>状态</th><th>点击</th><th>自动审核</th></tr></thead>
          <tbody>
            {(links.data ?? []).map((row) => (
              <tr key={row.id} data-testid="tenant-shortlink-safety-shortlinks-row">
                <td>{row.targetUrl}</td>
                <td>{row.shortUrl}</td>
                <td>{row.validUntil}</td>
                <td data-testid="tenant-shortlink-safety-shortlinks-status">{statusLabel(row.status)}</td>
                <td data-testid="tenant-shortlink-safety-shortlinks-click-count">{row.clickCount}</td>
                <td>{evidence(row)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section className="card" data-testid="tenant-shortlink-safety-analytics-page">
        <h2>点击分析</h2>
        <p data-testid="tenant-shortlink-safety-shortlinks-analytics-total">去重点击：{analytics.data?.uniqueClicks ?? 0}</p>
        <p data-testid="tenant-shortlink-safety-shortlinks-analytics-region">地域：{(analytics.data?.regions ?? []).map((item) => `${item.label} ${item.count}`).join(' / ') || '-'}</p>
        <p data-testid="tenant-shortlink-safety-shortlinks-analytics-device">设备：{(analytics.data?.devices ?? []).map((item) => `${item.label} ${item.count}`).join(' / ') || '-'}</p>
      </section>
    </section>
  );
}
