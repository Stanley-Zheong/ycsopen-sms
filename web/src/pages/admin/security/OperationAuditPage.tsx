import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  AUDIT_PERMISSIONS,
  getOperationAudits,
  type OperationAuditFilters,
  type OperationAuditItem,
} from '@/api/audit';
import ModalDialog from '@/components/common/ModalDialog';
import { QueryField, QueryPanel } from '@/components/common/QueryPanel';
import { useIdentityAccess } from '@/pages/admin/identity/useIdentityAccess';
import { protectedQueryKey } from '@/store/authStore';

const EMPTY_FILTERS: OperationAuditFilters = {
  actor: '', operation: '', result: '', from: '', to: '', page: 0, size: 20,
};

function displayTime(value: string): string {
  return new Date(value).toLocaleString('zh-CN', { hour12: false });
}

export default function OperationAuditPage() {
  const access = useIdentityAccess();
  const canRead = access.can(AUDIT_PERMISSIONS.operationsRead);
  const [draft, setDraft] = useState<OperationAuditFilters>(EMPTY_FILTERS);
  const [filters, setFilters] = useState<OperationAuditFilters>(EMPTY_FILTERS);
  const [selected, setSelected] = useState<OperationAuditItem | null>(null);
  const audits = useQuery({
    queryKey: protectedQueryKey('operation-audits', filters),
    queryFn: () => getOperationAudits(filters),
    enabled: canRead,
  });

  function submit() {
    setFilters({ ...draft, page: 0, size: 20 });
  }

  function resetFilters() {
    setDraft(EMPTY_FILTERS);
    setFilters(EMPTY_FILTERS);
  }

  if (access.isLoading) return <div className="card">加载操作日志权限…</div>;
  if (access.isError) return <div className="card" role="alert">权限范围加载失败，请稍后重试</div>;
  if (!canRead) return <div className="card" role="alert">无权查看操作日志</div>;

  const result = audits.data;
  const hasPrevious = (result?.page ?? 0) > 0;
  const hasNext = Boolean(result && (result.page + 1) * result.size < result.totalElements);

  return (
    <section data-testid="admin-privileged-data-system-logs-page">
      <div className="card">
        <h1 data-testid="admin-privileged-data-system-logs-heading">操作日志</h1>
        <p className="page-description">查询后台管理操作的结构化留痕；详情仅显示已脱敏字段。</p>
      </div>
      <QueryPanel
        legacyPanelTestId="admin-privileged-data-system-logs-filter"
        submitLegacyTestId="admin-privileged-data-system-logs-query"
        resetLegacyTestId="admin-privileged-data-system-logs-reset"
        onSubmit={submit}
        onReset={resetFilters}
        result={<>
          {audits.isLoading && <p data-testid="admin-privileged-data-system-logs-loading">加载操作日志…</p>}
          {audits.isError && <div data-testid="admin-privileged-data-system-logs-error" role="alert">
            <p>操作日志加载失败，请保留筛选条件后重试。</p>
            <button data-testid="admin-privileged-data-system-logs-retry" type="button" onClick={() => void audits.refetch()}>重试加载操作日志</button>
          </div>}
          {!audits.isLoading && !audits.isError && (
            <table className="ratio-table" data-testid="admin-privileged-data-system-logs-table">
            <caption className="visually-hidden">后台操作审计日志</caption>
            <thead><tr><th>时间</th><th>操作人</th><th>租户</th><th>操作</th><th>资源</th><th>结果</th><th>IP</th><th>耗时</th><th>问题编号</th><th>详情</th></tr></thead>
            <tbody>
              {(result?.items ?? []).map((item) => (
                <tr data-testid="admin-privileged-data-system-logs-row" key={item.id} data-row-key={item.id}>
                  <td>{displayTime(item.occurredAt)}</td><td>{item.actor}</td><td>{item.tenantId ?? '平台'}</td>
                  <td>{item.operation}</td><td>{item.resource}</td><td><span className={`status-text status-${item.result.toLowerCase().replace(/_/g, '-')}`}>{item.result}</span></td>
                  <td>{item.ipAddress}</td><td>{item.latencyMs} ms</td><td>{item.traceId ?? '—'}</td>
                  <td><button data-testid="admin-privileged-data-system-logs-detail-open" type="button" aria-label="查看审计详情" onClick={() => setSelected(item)}>查看</button></td>
                </tr>
              ))}
              {(result?.items.length ?? 0) === 0 && <tr data-testid="admin-privileged-data-system-logs-empty"><td colSpan={10}>暂无操作日志</td></tr>}
            </tbody>
            </table>
          )}
          {result && <div className="pagination-row">
            <button data-testid="admin-privileged-data-system-logs-previous" type="button" className="button-secondary" disabled={!hasPrevious} onClick={() => setFilters({ ...filters, page: result.page - 1 })}>上一页</button>
            <span data-testid="admin-privileged-data-system-logs-page-status">第 {result.page + 1} 页，共 {result.totalElements} 条</span>
            <button data-testid="admin-privileged-data-system-logs-next" type="button" className="button-secondary" disabled={!hasNext} onClick={() => setFilters({ ...filters, page: result.page + 1 })}>下一页</button>
          </div>}
        </>}
      >
        <QueryField name="actor" label="操作人"><input data-testid="admin-privileged-data-system-logs-actor-input" value={draft.actor ?? ''} onChange={(event) => setDraft({ ...draft, actor: event.target.value })} /></QueryField>
        <QueryField name="operation" label="操作"><input data-testid="admin-privileged-data-system-logs-operation-input" value={draft.operation ?? ''} onChange={(event) => setDraft({ ...draft, operation: event.target.value })} /></QueryField>
        <QueryField name="result" label="结果"><select data-testid="admin-privileged-data-system-logs-result-select" value={draft.result ?? ''} onChange={(event) => setDraft({ ...draft, result: event.target.value })}>
          <option value="">全部</option><option value="STARTED">待终结</option><option value="SUCCESS">成功</option><option value="CLIENT_FAILURE">请求失败</option><option value="SERVER_FAILURE">服务失败</option><option value="DENIED">拒绝</option>
        </select></QueryField>
        <QueryField name="from" label="开始时间"><input data-testid="admin-privileged-data-system-logs-from-input" type="datetime-local" value={draft.from ?? ''} onChange={(event) => setDraft({ ...draft, from: event.target.value })} /></QueryField>
        <QueryField name="to" label="结束时间"><input data-testid="admin-privileged-data-system-logs-to-input" type="datetime-local" value={draft.to ?? ''} onChange={(event) => setDraft({ ...draft, to: event.target.value })} /></QueryField>
      </QueryPanel>

      {selected && (
        <ModalDialog labelledBy="operation-audit-detail-title" onRequestClose={() => setSelected(null)}>
          <div data-testid="admin-privileged-data-system-logs-detail-drawer" className="audit-detail-content">
            <h2 id="operation-audit-detail-title">审计详情</h2>
            <dl data-testid="admin-privileged-data-system-logs-detail-fields" className="audit-detail-list">
              <div><dt>操作</dt><dd>{selected.operation}</dd></div>
              <div><dt>资源</dt><dd>{selected.resource ?? '—'}</dd></div>
              <div><dt>参数摘要</dt><dd>{selected.requestSummary || '无参数'}</dd></div>
              <div><dt>结果</dt><dd>{selected.result}</dd></div>
              <div><dt>耗时</dt><dd>{selected.latencyMs} ms</dd></div>
              <div><dt>问题编号</dt><dd>{selected.traceId ?? '—'}</dd></div>
              <div><dt>发生时间</dt><dd>{displayTime(selected.occurredAt)}</dd></div>
            </dl>
            <button data-testid="admin-privileged-data-system-logs-detail-close" type="button" onClick={() => setSelected(null)}>关闭审计详情</button>
          </div>
        </ModalDialog>
      )}
    </section>
  );
}
