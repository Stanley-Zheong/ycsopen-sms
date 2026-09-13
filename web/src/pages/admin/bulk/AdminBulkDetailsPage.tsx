import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getAdminBulkTaskDetail, listAdminBulkTasks } from '@/api/bulkScheduledApi';
import { QueryField, QueryPanel } from '@/components/common/QueryPanel';
import '@/styles/bulk-scheduled.css';

export default function AdminBulkDetailsPage() {
  const [draftTenantId, setDraftTenantId] = useState('');
  const [appliedTenantId, setAppliedTenantId] = useState('');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const filter = useMemo(() => ({ tenantId: appliedTenantId }), [appliedTenantId]);
  const tasks = useQuery({ queryKey: ['admin-bulk-details', filter], queryFn: () => listAdminBulkTasks(filter), retry: false });
  const selected = selectedId ?? tasks.data?.[0]?.bulkId ?? null;
  const detail = useQuery({ queryKey: ['admin-bulk-detail', selected], queryFn: () => getAdminBulkTaskDetail(selected as number), enabled: selected !== null, retry: false });

  return (
    <section className="bulk-scheduled-page" data-testid="admin-bulk-scheduled-bulk-details-page">
      <nav aria-label="面包屑">数据详单 / 批量任务详情</nav>
      <header><h1>批量任务详情</h1><p className="page-description">按任务和子项核对总数、进度、成功率、成本和收件明细状态。</p></header>
      <QueryPanel
        onSubmit={() => {
          setSelectedId(null);
          setAppliedTenantId(draftTenantId);
        }}
        onReset={() => {
          setDraftTenantId('');
          setAppliedTenantId('');
          setSelectedId(null);
        }}
        result={(
          <>
            {tasks.isLoading && <p>正在加载批量任务…</p>}
            {tasks.isError && <p role="alert">批量任务加载失败。</p>}
            {!tasks.isLoading && !tasks.isError && (tasks.data ?? []).length === 0 && <p>暂无批量任务。</p>}
            <section className="bulk-cards" data-testid="admin-bulk-scheduled-bulk-details-cards">
              <article><span>总任务</span><strong>{tasks.data?.length ?? 0}</strong></article>
              <article><span>总消息</span><strong>{tasks.data?.reduce((sum, task) => sum + task.totalCount, 0) ?? 0}</strong></article>
              <article><span>总成本</span><strong>{tasks.data?.reduce((sum, task) => sum + Number(task.totalCost), 0).toFixed(4) ?? '0.0000'}</strong></article>
            </section>
            <section className="card">
              <table className="bulk-table" data-testid="admin-bulk-scheduled-bulk-details-table"><thead><tr><th>任务ID</th><th>批次</th><th>名称</th><th>租户</th><th>总数</th><th>进度</th><th>状态</th><th>优先级</th><th>成本</th></tr></thead>
                <tbody>{(tasks.data ?? []).map((task) => <tr key={task.bulkId} data-testid="admin-bulk-scheduled-bulk-details-row" onClick={() => setSelectedId(task.bulkId)}><td>{task.bulkId}</td><td>{task.batchKey}</td><td>{task.taskName}</td><td>{task.tenantId}</td><td>{task.totalCount}</td><td>{task.completedCount}/{task.totalCount}</td><td>{task.state}</td><td>{task.priority}</td><td>{task.totalCost}</td></tr>)}</tbody>
              </table>
            </section>
            <section className="card" data-testid="admin-bulk-scheduled-bulk-details-items">
              <h2>收件明细</h2>
              {detail.isLoading && <p>正在加载收件明细…</p>}
              {detail.isError && <p role="alert">收件明细加载失败。</p>}
              <table className="bulk-table"><thead><tr><th>行号</th><th>跟踪ID</th><th>消息ID</th><th>状态</th><th>成本</th></tr></thead>
                <tbody>{(detail.data?.items ?? []).map((item) => <tr key={item.itemId}><td>{item.rowNo}</td><td>{item.itemTrackingId}</td><td>{item.messageId}</td><td>{item.sendStatus}</td><td>{item.cost}</td></tr>)}</tbody>
              </table>
            </section>
          </>
        )}
      >
        <QueryField name="tenant-id" label="租户ID">
          <input data-testid="admin-bulk-scheduled-bulk-details-filter-tenant" value={draftTenantId} onChange={(event) => setDraftTenantId(event.target.value)} />
        </QueryField>
      </QueryPanel>
    </section>
  );
}
