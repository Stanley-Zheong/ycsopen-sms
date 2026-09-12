import { FormEvent, useMemo, useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { createBulk, previewBulk, type BulkCommandPayload, type PreviewResult } from '@/api/bulkScheduledApi';
import { mutationErrorMessage } from '@/api/client';
import ModalDialog from '@/components/common/ModalDialog';
import '@/styles/bulk-scheduled.css';

function defaultBatchKey() {
  return `BULK-${Date.now()}`;
}

function parseRows(text: string) {
  return text.split(/\n+/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [phoneNumber, code = '1234'] = line.split(',').map((part) => part.trim());
      return { phoneNumber, variables: { code } };
    });
}

export default function TenantBulkSendPage() {
  const [batchKey, setBatchKey] = useState(defaultBatchKey);
  const [taskName, setTaskName] = useState('批量发送任务');
  const [priority, setPriority] = useState('NORMAL');
  const [scheduleAt, setScheduleAt] = useState('');
  const [rowsText, setRowsText] = useState('13800138000,2468\n13800138000,1357\nbad-phone,0000');
  const [sourceFileName, setSourceFileName] = useState('contacts.csv');
  const [sourceFileSize, setSourceFileSize] = useState(rowsText.length);
  const [preview, setPreview] = useState<PreviewResult | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [createError, setCreateError] = useState('');
  const [previewMessage, setPreviewMessage] = useState('');
  const [message, setMessage] = useState('');
  const items = useMemo(() => parseRows(rowsText), [rowsText]);
  const payload: BulkCommandPayload = {
    batchKey,
    taskName,
    messageType: 'NOTIFY',
    priority,
    templateId: '8101',
    signId: '9101',
    scheduleAt: scheduleAt || null,
    sourceFileName,
    sizeBytes: Math.max(sourceFileSize, rowsText.length, 1),
    malwareVerdict: 'CLEAN',
    items,
  };

  const previewMutation = useMutation({
    mutationFn: () => previewBulk(payload),
    onSuccess: (result) => {
      setPreview(result);
      setPreviewMessage(`校验完成：有效 ${result.valid}，无效 ${result.invalid}`);
      setCreateError('');
    },
    onError: (failure) => {
      setCreateError(mutationErrorMessage(failure, '批量校验失败'));
      setPreviewMessage('');
    },
  });
  const createMutation = useMutation({
    mutationFn: () => createBulk(payload),
    onSuccess: (result) => {
      setMessage(`批任务已创建：${result.batchKey} / ${result.state}`);
      setCreateError('');
      setBatchKey(defaultBatchKey());
      setPreview(null);
      setPreviewMessage('');
      setCreateOpen(false);
    },
    onError: (failure) => {
      setCreateError(mutationErrorMessage(failure, '批任务创建失败'));
      setPreviewMessage('');
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    createMutation.mutate();
  }

  function chooseFile(file: File | undefined) {
    if (!file) {
      return;
    }
    setSourceFileName(file.name);
    setSourceFileSize(file.size);
    if (file.name.toLowerCase().endsWith('.csv')) {
      void file.text().then(setRowsText);
    }
  }

  return (
    <section className="bulk-scheduled-page" data-testid="tenant-bulk-scheduled-bulk-send-page">
      <nav aria-label="面包屑">发送管理 / 批量发送</nav>
      <header><div><h1>批量发送</h1><p className="page-description">CSV/Excel 导入前先校验文件、号码、变量和重复行，再创建批任务。</p></div><button type="button" data-testid="tenant-bulk-scheduled-bulk-send-create-open" onClick={() => { setCreateError(''); setPreview(null); setPreviewMessage(''); setCreateOpen(true); }}>新建批任务</button></header>
      {message && <p role="status" className="bulk-alert success" data-testid="tenant-bulk-scheduled-operation-message">{message}</p>}
      {createOpen && (
        <ModalDialog labelledBy="bulk-send-create-title" onRequestClose={() => { setCreateOpen(false); setCreateError(''); setPreview(null); setPreviewMessage(''); }}>
          <form className="bulk-form" data-testid="tenant-bulk-scheduled-bulk-send-form" onSubmit={submit}>
            <h2 id="bulk-send-create-title">新建批任务</h2>
            <label>批次流水<input required data-testid="tenant-bulk-scheduled-bulk-send-batch-key" value={batchKey} onChange={(event) => setBatchKey(event.target.value)} /></label>
            <label>任务名称<input required data-testid="tenant-bulk-scheduled-bulk-send-task-name" value={taskName} onChange={(event) => setTaskName(event.target.value)} /></label>
            <label>优先级<select data-testid="tenant-bulk-scheduled-bulk-send-priority" value={priority} onChange={(event) => setPriority(event.target.value)}><option>NORMAL</option><option>HIGH</option><option>LOW</option></select></label>
            <label>计划发送时间<input data-testid="tenant-bulk-scheduled-bulk-send-schedule" value={scheduleAt} onChange={(event) => setScheduleAt(event.target.value)} placeholder="2026-09-10T09:00:00" /></label>
            <label>导入文件<input data-testid="tenant-bulk-scheduled-bulk-send-file" type="file" accept=".csv,.xlsx" onChange={(event) => chooseFile(event.target.files?.[0])} /></label>
            <label className="bulk-wide">导入内容<textarea required data-testid="tenant-bulk-scheduled-bulk-send-upload" value={rowsText} onChange={(event) => setRowsText(event.target.value)} /></label>
            {previewMessage && <p role="status" className="bulk-alert success">{previewMessage}</p>}
            {createError && <p role="alert" className="bulk-alert error" data-testid="tenant-bulk-scheduled-operation-error">{createError}</p>}
            {preview && (
              <section data-testid="tenant-bulk-scheduled-bulk-send-validation-results">
                <h3>校验结果</h3>
                <table className="bulk-table"><thead><tr><th>行号</th><th>手机号</th><th>变量</th><th>状态</th><th>原因</th></tr></thead>
                  <tbody>{preview.rows.map((row) => <tr key={row.rowNo}><td>{row.rowNo}</td><td>{row.maskedPhone}</td><td>{row.variables.code}</td><td>{row.validationStatus}</td><td>{row.validationReason ?? '-'}</td></tr>)}</tbody>
                </table>
              </section>
            )}
            <div className="dialog-actions">
              <button type="button" className="button-secondary" data-testid="tenant-bulk-scheduled-bulk-send-create-cancel" onClick={() => { setCreateOpen(false); setCreateError(''); setPreview(null); setPreviewMessage(''); }}>取消</button>
              <button type="button" data-testid="tenant-bulk-scheduled-bulk-send-preview" onClick={() => previewMutation.mutate()} disabled={previewMutation.isPending}>校验预览</button>
              <button type="submit" data-testid="tenant-bulk-scheduled-bulk-send-create" disabled={createMutation.isPending}>创建批任务</button>
            </div>
          </form>
        </ModalDialog>
      )}
    </section>
  );
}
