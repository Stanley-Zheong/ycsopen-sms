import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import ModalDialog from '@/components/common/ModalDialog';
import { QueryField, QueryPanel } from '@/components/common/QueryPanel';
import {
  getResourceReviewHistoryDetail,
  listResourceReviewHistory,
  REVIEW_HISTORY_PERMISSIONS,
  type ResourceReviewHistoryFilters,
  type ResourceReviewHistoryItem,
} from '@/api/resourceReviewHistoryApi';
import { isPlatformRole, protectedQueryKey, useAuthStore } from '@/store/authStore';
import { useIdentityAccess } from '@/pages/admin/identity/useIdentityAccess';
import '@/styles/resource-review-history.css';

const EMPTY_FILTERS: ResourceReviewHistoryFilters = {
  resourceType: '',
  tenantId: '',
  decisionState: '',
  actor: '',
  riskLevel: '',
  keyword: '',
  createdFrom: '',
  createdTo: '',
  page: 0,
  pageSize: 50,
};

export default function ResourceReviewHistoryPage() {
  const userType = useAuthStore((state) => state.userType);
  const roleAllowed = userType === 'ADMIN' || userType === 'OPERATOR';
  const access = useIdentityAccess(isPlatformRole(userType) && roleAllowed);
  const canRead = userType === 'ADMIN' || (userType === 'OPERATOR' && access.can(REVIEW_HISTORY_PERMISSIONS.read));
  const [draft, setDraft] = useState<ResourceReviewHistoryFilters>(EMPTY_FILTERS);
  const [filters, setFilters] = useState<ResourceReviewHistoryFilters>(EMPTY_FILTERS);
  const [detailTarget, setDetailTarget] = useState<ResourceReviewHistoryItem | null>(null);

  const rowsQuery = useQuery({
    queryKey: protectedQueryKey('resource-review-history', filters),
    queryFn: () => listResourceReviewHistory(filters),
    enabled: roleAllowed && canRead,
    retry: false,
  });
  const detailQuery = useQuery({
    queryKey: protectedQueryKey('resource-review-history-detail', detailTarget?.decisionId ?? ''),
    queryFn: () => getResourceReviewHistoryDetail(detailTarget!.decisionId),
    enabled: Boolean(detailTarget) && canRead,
    retry: false,
  });
  const rows = rowsQuery.data ?? [];
  const detail = detailQuery.data ?? detailTarget;
  const setFilter = (key: keyof Omit<ResourceReviewHistoryFilters, 'page' | 'pageSize'>, value: string) => {
    setDraft({ ...draft, [key]: value });
  };
  const canGoPrevious = filters.page > 0;
  const canGoNext = rows.length === filters.pageSize;

  if (!roleAllowed || (!canRead && !access.isLoading)) {
    return <p role="alert">无权查看统一审核历史。</p>;
  }

  return (
    <section data-testid="admin-resource-review-history-review-page" className="review-history-page">
      <nav aria-label="面包屑">审核中心 / 统一审核历史</nav>
      <header className="review-history-header">
        <div>
          <h1>统一审核历史</h1>
          <p className="page-description">从签名、模板、豁免策略历史中只读重放审核决定、版本、操作人、原因和证据。</p>
        </div>
      </header>

      <QueryPanel
        legacyPanelTestId="admin-resource-review-history-review-filters"
        onSubmit={() => setFilters({ ...draft, page: 0, pageSize: 50 })}
        onReset={() => { setDraft(EMPTY_FILTERS); setFilters(EMPTY_FILTERS); }}
        result={<>
          {rowsQuery.isLoading && <p role="status">正在加载统一审核历史…</p>}
          {rowsQuery.isError && <p role="alert">统一审核历史加载失败。</p>}
          {!rowsQuery.isLoading && rows.length === 0 && <p>暂无审核历史。</p>}
          {rows.length > 0 && (
            <>
              <table data-testid="admin-resource-review-history-review-table" className="ratio-table">
              <thead>
                <tr><th>决定</th><th>资源</th><th>机构</th><th>状态</th><th>审核人</th><th>原因</th><th>时间</th><th>操作</th></tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={row.decisionId} data-testid="admin-resource-review-history-review-row">
                    <td>{row.decisionId}</td>
                    <td>{row.resourceType} / {row.resourceCode} / {row.resourceVersion}</td>
                    <td>{row.tenantId}</td>
                    <td>{row.decisionState}</td>
                    <td>{row.actor}</td>
                    <td>{row.reason}</td>
                    <td>{row.createdAt}</td>
                    <td><button type="button" data-testid="admin-resource-review-history-review-detail-open" onClick={() => setDetailTarget(row)}>详情</button></td>
                  </tr>
                ))}
              </tbody>
              </table>
              <div className="review-history-pagination" data-testid="admin-resource-review-history-review-pagination">
              <button
                type="button"
                data-testid="admin-resource-review-history-review-page-prev"
                disabled={!canGoPrevious}
                onClick={() => setFilters({ ...filters, page: Math.max(0, filters.page - 1) })}
              >
                上一页
              </button>
              <span>第 {filters.page + 1} 页，每页 {filters.pageSize} 条</span>
              <button
                type="button"
                data-testid="admin-resource-review-history-review-page-next"
                disabled={!canGoNext}
                onClick={() => setFilters({ ...filters, page: filters.page + 1 })}
              >
                下一页
              </button>
              </div>
            </>
          )}
        </>}
      >
        <QueryField name="resource-type" label="资源类型"><select value={draft.resourceType} onChange={(event) => setFilter('resourceType', event.target.value)}>
          <option value="">全部</option><option value="SIGNATURE">签名</option><option value="TEMPLATE">模板</option><option value="EXEMPTION">豁免</option>
        </select></QueryField>
        <QueryField name="tenant-id" label="机构"><input value={draft.tenantId} onChange={(event) => setFilter('tenantId', event.target.value)} /></QueryField>
        <QueryField name="state" label="状态"><input value={draft.decisionState} onChange={(event) => setFilter('decisionState', event.target.value)} /></QueryField>
        <QueryField name="reviewed-by" label="审核人"><input value={draft.actor} onChange={(event) => setFilter('actor', event.target.value)} /></QueryField>
        <QueryField name="risk-level" label="风险"><select value={draft.riskLevel} onChange={(event) => setFilter('riskLevel', event.target.value)}>
          <option value="">全部</option><option value="LOW">低</option><option value="MEDIUM">中</option><option value="HIGH">高</option>
        </select></QueryField>
        <QueryField name="keyword" label="关键词"><input value={draft.keyword} onChange={(event) => setFilter('keyword', event.target.value)} /></QueryField>
        <QueryField name="created-from" label="开始时间"><input type="datetime-local" value={draft.createdFrom} onChange={(event) => setFilter('createdFrom', event.target.value)} /></QueryField>
        <QueryField name="created-to" label="结束时间"><input type="datetime-local" value={draft.createdTo} onChange={(event) => setFilter('createdTo', event.target.value)} /></QueryField>
      </QueryPanel>

      {detailTarget && detail && (
        <ModalDialog labelledBy="review-history-detail-title" onRequestClose={() => setDetailTarget(null)}>
          <div data-testid="admin-resource-review-history-review-detail-drawer" className="review-history-detail">
            <h2 id="review-history-detail-title">审核历史详情 {detail.decisionId}</h2>
            <dl>
              <div><dt>资源</dt><dd>{detail.resourceType} #{detail.resourceId}</dd></div>
              <div><dt>版本</dt><dd>{detail.resourceVersion}</dd></div>
              <div><dt>决定</dt><dd>{detail.decisionState}</dd></div>
              <div><dt>审核人</dt><dd>{detail.actor}</dd></div>
              <div><dt>风险</dt><dd>{detail.riskLevel ?? '不适用'}</dd></div>
              <div><dt>证据</dt><dd>{detail.evidenceRef ?? '无'}</dd></div>
              <div><dt>提交快照</dt><dd>{detail.submittedSnapshot}</dd></div>
              <div><dt>生命周期链接</dt><dd>{detail.lifecycleLink}</dd></div>
              <div><dt>原因</dt><dd>{detail.reason}</dd></div>
            </dl>
            {detailQuery.isLoading && <p role="status">正在校验不可变详情…</p>}
            <button type="button" className="button-secondary" onClick={() => setDetailTarget(null)}>关闭</button>
          </div>
        </ModalDialog>
      )}
    </section>
  );
}
