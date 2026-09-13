import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminControlTask, listAdminBulkTasks, type BulkTaskView } from '@/api/bulkScheduledApi';
import { mutationErrorMessage } from '@/api/client';
import ActionReasonDialog from '@/components/common/ActionReasonDialog';
import { QueryField, QueryPanel } from '@/components/common/QueryPanel';
import '@/styles/bulk-scheduled.css';

const EMPTY_FILTER = { tenantId: '', state: '' };
type TaskAction = 'pause' | 'resume' | 'cancel' | 'restart';
type PendingAction = { action: TaskAction; task: BulkTaskView };

const ACTION_LABELS: Record<TaskAction, string> = {
  pause: '暂停任务',
  resume: '恢复任务',
  cancel: '取消任务',
  restart: '重启任务',
};

export default function AdminSendJobsPage() {
  const queryClient = useQueryClient();
  const [draftFilter, setDraftFilter] = useState(EMPTY_FILTER);
  const [appliedFilter, setAppliedFilter] = useState(EMPTY_FILTER);
  const [pendingAction, setPendingAction] = useState<PendingAction | null>(null);
  const [reason, setReason] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const filter = useMemo(() => ({ ...appliedFilter }), [appliedFilter]);
  const tasks = useQuery({ queryKey: ['admin-send-jobs', filter], queryFn: () => listAdminBulkTasks(filter), retry: false });
  const control = useMutation({
    mutationFn: ({ task, action, actionReason }: { task: BulkTaskView; action: TaskAction; actionReason: string }) => adminControlTask(task.bulkId, action, actionReason),
    onSuccess: async (result) => {
      setPendingAction(null);
      setReason('');
      setMessage(`任务 ${result.batchKey} 已更新为 ${result.state}`);
      setError('');
      await queryClient.invalidateQueries({ queryKey: ['admin-send-jobs'] });
    },
    onError: (failure) => {
      setError(mutationErrorMessage(failure, '任务操作失败'));
      setMessage('');
    },
  });
  const openAction = (action: TaskAction, task: BulkTaskView) => {
    setPendingAction({ action, task });
    setReason('');
    setError('');
  };
  const closeAction = () => {
    setPendingAction(null);
    setReason('');
  };

  return (
    <section className="bulk-scheduled-page" data-testid="admin-bulk-scheduled-send-jobs-page">
      <nav aria-label="面包屑">运营工具 / 发送任务检索</nav>
      <header><h1>发送任务检索</h1><p className="page-description">统一检索 immediate、bulk、scheduled 任务并执行状态边界内操作。</p></header>
      {message && <p role="status" className="bulk-alert success" data-testid="admin-bulk-scheduled-operation-message">{message}</p>}
      {error && <p role="alert" className="bulk-alert error" data-testid="admin-bulk-scheduled-operation-error">{error}</p>}
      <QueryPanel
        legacyPanelTestId="admin-bulk-scheduled-send-jobs-filter"
        onSubmit={() => setAppliedFilter({ ...draftFilter })}
        onReset={() => {
          setDraftFilter(EMPTY_FILTER);
          setAppliedFilter(EMPTY_FILTER);
        }}
        result={(
          <section className="card" data-testid="admin-bulk-scheduled-send-jobs-control">
            {tasks.isLoading && <p>正在加载发送任务…</p>}
            {tasks.isError && <p role="alert">发送任务加载失败。</p>}
            {!tasks.isLoading && !tasks.isError && (tasks.data ?? []).length === 0 && <p>暂无发送任务。</p>}
            <table className="bulk-table"><thead><tr><th>批次</th><th>租户</th><th>名称</th><th>类型</th><th>状态</th><th>进度</th><th>动作</th></tr></thead>
              <tbody>{(tasks.data ?? []).map((task) => <tr key={task.bulkId} data-testid="admin-bulk-scheduled-send-jobs-row"><td>{task.batchKey}</td><td>{task.tenantId}</td><td>{task.taskName}</td><td>{task.messageType}</td><td>{task.state}</td><td>{task.completedCount}/{task.totalCount}</td><td>
                <button type="button" data-testid="admin-bulk-scheduled-send-jobs-pause" onClick={() => openAction('pause', task)}>暂停</button>
                <button type="button" data-testid="admin-bulk-scheduled-send-jobs-resume" onClick={() => openAction('resume', task)}>恢复</button>
                <button type="button" data-testid="admin-bulk-scheduled-send-jobs-cancel" onClick={() => openAction('cancel', task)}>取消</button>
                <button type="button" data-testid="admin-bulk-scheduled-send-jobs-restart" onClick={() => openAction('restart', task)}>重启</button>
              </td></tr>)}</tbody>
            </table>
          </section>
        )}
      >
        <QueryField name="tenant-id" label="租户ID">
          <input data-testid="admin-bulk-scheduled-send-jobs-filter-tenant" value={draftFilter.tenantId} onChange={(event) => setDraftFilter((current) => ({ ...current, tenantId: event.target.value }))} />
        </QueryField>
        <QueryField name="status" label="状态">
          <input data-testid="admin-bulk-scheduled-send-jobs-filter-state" value={draftFilter.state} onChange={(event) => setDraftFilter((current) => ({ ...current, state: event.target.value }))} />
        </QueryField>
      </QueryPanel>

      {pendingAction && (
        <ActionReasonDialog
          idPrefix="admin-bulk-scheduled-send-jobs-action"
          title={`确认${ACTION_LABELS[pendingAction.action]}`}
          target={`任务 ${pendingAction.task.batchKey} · 机构 ${pendingAction.task.tenantId}`}
          consequence={pendingAction.action === 'pause'
            ? '确认后任务将暂停派发新的子消息，已完成记录保持不变。'
            : pendingAction.action === 'resume'
              ? '确认后任务将从当前进度恢复派发。'
              : pendingAction.action === 'cancel'
                ? '确认后任务将进入取消流程，未派发的子消息不会继续发送。'
                : '确认后将按现有任务数据重新启动允许重试的处理。'}
          reasonLabel={`${ACTION_LABELS[pendingAction.action]}原因`}
          reasonTestId="admin-bulk-scheduled-send-jobs-reason"
          reason={reason}
          placeholder="请填写本次任务操作的复核依据"
          confirmLabel={`确认${ACTION_LABELS[pendingAction.action]}`}
          pending={control.isPending}
          onReasonChange={setReason}
          onCancel={closeAction}
          onConfirm={() => control.mutate({ ...pendingAction, actionReason: reason.trim() })}
        />
      )}
    </section>
  );
}
