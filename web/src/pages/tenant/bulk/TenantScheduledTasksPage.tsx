import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { listTenantScheduledTasks, tenantControlTask, type BulkTaskView } from '@/api/bulkScheduledApi';
import { mutationErrorMessage } from '@/api/client';
import '@/styles/bulk-scheduled.css';

export default function TenantScheduledTasksPage() {
  const queryClient = useQueryClient();
  const [reason, setReason] = useState('租户操作确认');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const tasks = useQuery({ queryKey: ['tenant-scheduled-tasks'], queryFn: listTenantScheduledTasks, retry: false });
  const control = useMutation({
    mutationFn: ({ task, action }: { task: BulkTaskView; action: string }) => tenantControlTask(task.bulkId, action, reason),
    onSuccess: async (result) => {
      setMessage(`任务 ${result.batchKey} 已更新为 ${result.state}`);
      setError('');
      await queryClient.invalidateQueries({ queryKey: ['tenant-scheduled-tasks'] });
    },
    onError: (failure) => {
      setError(mutationErrorMessage(failure, '任务操作失败'));
      setMessage('');
    },
  });

  return (
    <section className="bulk-scheduled-page" data-testid="tenant-bulk-scheduled-scheduled-tasks-page">
      <nav aria-label="面包屑">发送管理 / 计划任务</nav>
      <header><h1>计划任务</h1><p className="page-description">查看批量和定时任务状态，并在允许边界内暂停、恢复、取消或重启。</p></header>
      {message && <p role="status" className="bulk-alert success" data-testid="tenant-bulk-scheduled-operation-message">{message}</p>}
      {error && <p role="alert" className="bulk-alert error" data-testid="tenant-bulk-scheduled-operation-error">{error}</p>}
      <label className="bulk-reason">操作原因<input data-testid="tenant-bulk-scheduled-scheduled-tasks-reason" value={reason} onChange={(event) => setReason(event.target.value)} /></label>
      <section className="card" data-testid="tenant-bulk-scheduled-scheduled-tasks-control">
        <table className="bulk-table"><thead><tr><th>批次</th><th>名称</th><th>优先级</th><th>状态</th><th>进度</th><th>计划</th><th>动作</th></tr></thead>
          <tbody>{(tasks.data ?? []).map((task) => (
            <tr key={task.bulkId} data-testid="tenant-bulk-scheduled-scheduled-tasks-row">
              <td>{task.batchKey}</td><td>{task.taskName}</td><td>{task.priority}</td><td data-testid="tenant-bulk-scheduled-scheduled-tasks-state">{task.state}</td>
              <td>{task.completedCount}/{task.totalCount}，失败 {task.failCount}</td><td>{task.scheduleAt ?? '-'}</td>
              <td>
                <button type="button" data-testid="tenant-bulk-scheduled-scheduled-tasks-pause" onClick={() => control.mutate({ task, action: 'pause' })}>暂停</button>
                <button type="button" data-testid="tenant-bulk-scheduled-scheduled-tasks-resume" onClick={() => control.mutate({ task, action: 'resume' })}>恢复</button>
                <button type="button" data-testid="tenant-bulk-scheduled-scheduled-tasks-cancel" onClick={() => control.mutate({ task, action: 'cancel' })}>取消</button>
                <button type="button" data-testid="tenant-bulk-scheduled-scheduled-tasks-restart" onClick={() => control.mutate({ task, action: 'restart' })}>重启</button>
              </td>
            </tr>
          ))}</tbody>
        </table>
      </section>
    </section>
  );
}
