import { type FormEvent, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  AUDIT_PERMISSIONS,
  getSecurityEvents,
  type SecurityEventFilters,
  type SecurityEventType,
} from '@/api/audit';
import { useIdentityAccess } from '@/pages/admin/identity/useIdentityAccess';
import { protectedQueryKey } from '@/store/authStore';

const EMPTY_FILTERS: SecurityEventFilters = {
  eventType: '', actor: '', result: '', from: '', to: '', page: 0, size: 20,
};

const EVENT_LABELS: Record<SecurityEventType, string> = {
  UNUSUAL_LOGIN: '异常登录',
  REPEATED_LOGIN_FAILURE: '连续登录失败',
  BULK_EXPORT: '大批量导出',
};

function displayTime(value: string): string {
  return new Date(value).toLocaleString('zh-CN', { hour12: false });
}

export default function SecurityEventsPage() {
  const access = useIdentityAccess();
  const canRead = access.can(AUDIT_PERMISSIONS.securityEventsRead);
  const [draft, setDraft] = useState<SecurityEventFilters>(EMPTY_FILTERS);
  const [filters, setFilters] = useState<SecurityEventFilters>(EMPTY_FILTERS);
  const events = useQuery({
    queryKey: protectedQueryKey('security-events', filters),
    queryFn: () => getSecurityEvents(filters),
    enabled: canRead,
  });

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFilters({ ...draft, page: 0, size: 20 });
  }

  function resetFilters() {
    setDraft(EMPTY_FILTERS);
    setFilters(EMPTY_FILTERS);
  }

  if (access.isLoading) return <div className="card">加载安全事件权限…</div>;
  if (access.isError) return <div className="card" role="alert">权限范围加载失败，请稍后重试</div>;
  if (!canRead) return <div className="card" role="alert">无权查看安全事件</div>;

  const result = events.data;
  const hasPrevious = (result?.page ?? 0) > 0;
  const hasNext = Boolean(result && (result.page + 1) * result.size < result.totalElements);

  return (
    <section data-testid="admin-privileged-data-security-events-page">
      <div className="card">
        <h1 data-testid="admin-privileged-data-security-events-heading">安全事件</h1>
        <p className="page-description">查看可归因并已去重的异常登录、连续登录失败和大批量导出事件。</p>
        <form data-testid="admin-privileged-data-security-events-filter" className="audit-filter-grid" onSubmit={submit}>
          <label>事件类型
            <select data-testid="admin-privileged-data-security-events-type-select" value={draft.eventType ?? ''} onChange={(event) => setDraft({ ...draft, eventType: event.target.value as SecurityEventType | '' })}>
              <option value="">全部</option>
              <option value="UNUSUAL_LOGIN">异常登录</option>
              <option value="REPEATED_LOGIN_FAILURE">连续登录失败</option>
              <option value="BULK_EXPORT">大批量导出</option>
            </select>
          </label>
          <label>操作人
            <input data-testid="admin-privileged-data-security-events-actor-input" value={draft.actor ?? ''} onChange={(event) => setDraft({ ...draft, actor: event.target.value })} />
          </label>
          <label>结果
            <select data-testid="admin-privileged-data-security-events-result-select" value={draft.result ?? ''} onChange={(event) => setDraft({ ...draft, result: event.target.value })}>
              <option value="">全部</option><option value="DETECTED">已检测</option><option value="BLOCKED">已阻断</option>
              <option value="SUCCESS">成功</option><option value="FAILURE">失败</option>
            </select>
          </label>
          <label>开始时间
            <input data-testid="admin-privileged-data-security-events-from-input" type="datetime-local" value={draft.from ?? ''} onChange={(event) => setDraft({ ...draft, from: event.target.value })} />
          </label>
          <label>结束时间
            <input data-testid="admin-privileged-data-security-events-to-input" type="datetime-local" value={draft.to ?? ''} onChange={(event) => setDraft({ ...draft, to: event.target.value })} />
          </label>
          <div className="audit-filter-actions">
            <button data-testid="admin-privileged-data-security-events-query" type="submit">查询</button>
            <button data-testid="admin-privileged-data-security-events-reset" type="button" className="button-secondary" onClick={resetFilters}>重置</button>
          </div>
        </form>
      </div>

      <div className="card">
        {events.isLoading && <p data-testid="admin-privileged-data-security-events-loading">加载安全事件…</p>}
        {events.isError && <div data-testid="admin-privileged-data-security-events-error" role="alert">
          <p>安全事件加载失败，请保留筛选条件后重试。</p>
          <button data-testid="admin-privileged-data-security-events-retry" type="button" onClick={() => void events.refetch()}>重试加载安全事件</button>
        </div>}
        {!events.isLoading && !events.isError && (
          <table className="ratio-table" data-testid="admin-privileged-data-security-events-table">
            <caption className="visually-hidden">平台安全事件</caption>
            <thead><tr><th>时间</th><th>事件</th><th>操作人</th><th>租户</th><th>结果</th><th>IP</th><th>问题编号</th><th>摘要</th></tr></thead>
            <tbody>
              {(result?.items ?? []).map((item) => (
                <tr data-testid="admin-privileged-data-security-events-row" key={item.id} data-row-key={item.id}>
                  <td>{displayTime(item.detectedAt)}</td><td>{EVENT_LABELS[item.eventType]}</td><td>{item.actor}</td>
                  <td>{item.tenantId ?? '平台'}</td><td><span className={`status-text status-${item.result.toLowerCase().replace(/_/g, '-')}`}>{item.result}</span></td>
                  <td>{item.ipAddress ?? '—'}</td><td>{item.traceId ?? '—'}</td><td>{item.summary}</td>
                </tr>
              ))}
              {(result?.items.length ?? 0) === 0 && <tr data-testid="admin-privileged-data-security-events-empty"><td colSpan={8}>暂无安全事件</td></tr>}
            </tbody>
          </table>
        )}
        {result && <div className="pagination-row">
          <button data-testid="admin-privileged-data-security-events-previous" type="button" className="button-secondary" disabled={!hasPrevious} onClick={() => setFilters({ ...filters, page: result.page - 1 })}>上一页</button>
          <span data-testid="admin-privileged-data-security-events-page-status">第 {result.page + 1} 页，共 {result.totalElements} 条</span>
          <button data-testid="admin-privileged-data-security-events-next" type="button" className="button-secondary" disabled={!hasNext} onClick={() => setFilters({ ...filters, page: result.page + 1 })}>下一页</button>
        </div>}
      </div>
    </section>
  );
}
