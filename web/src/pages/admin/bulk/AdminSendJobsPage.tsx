import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminControlTask, listAdminBulkTasks, type BulkTaskView } from '@/api/bulkScheduledApi';
import { mutationErrorMessage } from '@/api/client';
import '@/styles/bulk-scheduled.css';

export default function AdminSendJobsPage() {
  const queryClient = useQueryClient();
  const [tenantId, setTenantId] = useState('');
  const [state, setState] = useState('');
  const [reason, setReason] = useState('运营复核确认');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const filter = useMemo(() => ({ tenantId, state }), [tenantId, state]);
  const tasks = useQuery({ queryKey: ['admin-send-jobs', filter], queryFn: () => listAdminBulkTasks(filter), retry: false });
  const control = useMutation({
    mutationFn: ({ task, action }: { task: BulkTaskView; action: string }) => adminControlTask(task.bulkId, action, reason),
    onSuccess: async (result) => {
      setMessage(`任务 ${result.batchKey} 已更新为 ${result.state}`);
      setError('');
      await queryClient.invalidateQueries({ queryKey: ['admin-send-jobs'] });
    },
    onError: (failure) => {
      setError(mutationErrorMessage(failure, '任务操作失败'));
      setMessage('');
    },
  });

  return (
    <section className="bulk-scheduled-page" data-testid="admin-bulk-scheduled-send-jobs-page">
      <nav aria-label="面包屑">运营工具 / 发送任务检索</nav>
      <header><h1>发送任务检索</h1><p className="page-description">统一检索 immediate、bulk、scheduled 任务并执行状态边界内操作。</p></header>
      {message && <p role="status" className="bulk-alert success" data-testid="admin-bulk-scheduled-operation-message">{message}</p>}
      {error && <p role="alert" className="bulk-alert error" data-testid="admin-bulk-scheduled-operation-error">{error}</p>}
      <section className="card bulk-form" data-testid="admin-bulk-scheduled-send-jobs-filter">
        <label>租户ID<input data-testid="admin-bulk-scheduled-send-jobs-filter-tenant" value={tenantId} onChange={(event) => setTenantId(event.target.value)} /></label>
        <label>状态<input data-testid="admin-bulk-scheduled-send-jobs-filter-state" value={state} onChange={(event) => setState(event.target.value)} /></label>
        <label>动作原因<input data-testid="admin-bulk-scheduled-send-jobs-reason" value={reason} onChange={(event) => setReason(event.target.value)} /></label>
      </section>
      <section className="card" data-testid="admin-bulk-scheduled-send-jobs-control">
        <table className="bulk-table"><thead><tr><th>批次</th><th>租户</th><th>名称</th><th>类型</th><th>状态</th><th>进度</th><th>动作</th></tr></thead>
          <tbody>{(tasks.data ?? []).map((task) => <tr key={task.bulkId} data-testid="admin-bulk-scheduled-send-jobs-row"><td>{task.batchKey}</td><td>{task.tenantId}</td><td>{task.taskName}</td><td>{task.messageType}</td><td>{task.state}</td><td>{task.completedCount}/{task.totalCount}</td><td>
            <button type="button" data-testid="admin-bulk-scheduled-send-jobs-pause" onClick={() => control.mutate({ task, action: 'pause' })}>暂停</button>
            <button type="button" data-testid="admin-bulk-scheduled-send-jobs-resume" onClick={() => control.mutate({ task, action: 'resume' })}>恢复</button>
            <button type="button" data-testid="admin-bulk-scheduled-send-jobs-cancel" onClick={() => control.mutate({ task, action: 'cancel' })}>取消</button>
            <button type="button" data-testid="admin-bulk-scheduled-send-jobs-restart" onClick={() => control.mutate({ task, action: 'restart' })}>重启</button>
          </td></tr>)}</tbody>
        </table>
      </section>
    </section>
  );
}
