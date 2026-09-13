import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  downloadSecureExport,
  listSecureExports,
  retrySecureExport,
  type ExportJob,
} from '@/api/secureAsyncExportApi';
import { mutationErrorMessage } from '@/api/client';
import '@/styles/secure-async-export.css';

function displayTime(value: string | null): string {
  if (!value) return '-';
  return new Date(value).toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' });
}

function statusLabel(value: string): string {
  return ({ PENDING: '等待中', RUNNING: '运行中', COMPLETED: '已完成', FAILED: '失败' } as Record<string, string>)[value] ?? value;
}

export default function AdminExportCenterPage() {
  const queryClient = useQueryClient();
  const [tenantId, setTenantId] = useState('');
  const [exportType, setExportType] = useState('');
  const [status, setStatus] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const jobs = useQuery({
    queryKey: ['secure-async-exports', tenantId, exportType, status],
    queryFn: () => listSecureExports({ tenantId, exportType, status }),
    retry: false,
  });
  const retry = useMutation({
    mutationFn: (job: ExportJob) => retrySecureExport(job.id, '人工确认重试失败导出'),
    onSuccess: async (job) => {
      setMessage(`导出任务已重试：${job.id}`);
      setError('');
      await queryClient.invalidateQueries({ queryKey: ['secure-async-exports'] });
    },
    onError: (failure) => {
      setMessage('');
      setError(mutationErrorMessage(failure, '导出重试失败'));
    },
  });
  const download = useMutation({
    mutationFn: (job: ExportJob) => downloadSecureExport(job.id),
    onSuccess: (artifact) => {
      setMessage(`已获取加密下载包：${artifact.fileName} / ${artifact.encryptionState}`);
      setError('');
    },
    onError: (failure) => {
      setMessage('');
      setError(mutationErrorMessage(failure, '导出下载失败'));
    },
  });

  const items = jobs.data ?? [];
  const total = items.length;
  const running = items.filter((job) => ['PENDING', 'RUNNING'].includes(job.status)).length;
  const complete = items.filter((job) => job.status === 'COMPLETED').length;
  const failed = items.filter((job) => job.status === 'FAILED').length;
  const records = items.reduce((sum, job) => sum + job.recordCount, 0);

  return (
    <section className="secure-export-page" data-testid="admin-secure-async-export-center-page">
      <nav aria-label="面包屑">平台管理 / 安全异步导出</nav>
      <header className="secure-export-header">
        <div>
          <h1>安全异步导出中心</h1>
          <p className="page-description">统一查看导出快照、任务进度、失败原因、加密文件和过期下载授权。</p>
        </div>
      </header>

      {message && <p role="status" className="secure-export-alert success" data-testid="admin-secure-async-export-center-message">{message}</p>}
      {error && <p role="alert" className="secure-export-alert error" data-testid="admin-secure-async-export-center-error">{error}</p>}

      <section className="secure-export-cards" data-testid="admin-secure-async-export-center-cards">
        <article><strong>{total}</strong><span>总任务</span></article>
        <article><strong>{running}</strong><span>运行中</span></article>
        <article><strong>{complete}</strong><span>已完成</span></article>
        <article><strong>{failed}</strong><span>失败</span></article>
        <article><strong>{records}</strong><span>记录数</span></article>
        <article><strong>EXCEL/CSV/JSON/PDF</strong><span>支持格式</span></article>
      </section>

      <section className="card secure-export-filters">
        <label>机构ID<input data-testid="admin-secure-async-export-center-filter-tenant" value={tenantId} onChange={(event) => setTenantId(event.target.value)} /></label>
        <label>导出类型<input data-testid="admin-secure-async-export-center-filter-type" value={exportType} onChange={(event) => setExportType(event.target.value)} placeholder="SEND_DETAIL" /></label>
        <label>状态<input data-testid="admin-secure-async-export-center-filter-status" value={status} onChange={(event) => setStatus(event.target.value)} placeholder="COMPLETED" /></label>
        <button type="button" data-testid="admin-secure-async-export-center-refresh" onClick={() => void queryClient.invalidateQueries({ queryKey: ['secure-async-exports'] })}>刷新</button>
      </section>

      <section className="card">
        <h2>导出任务</h2>
        <table className="secure-export-table" data-testid="admin-secure-async-export-center-table">
          <thead>
            <tr>
              <th>任务ID</th><th>名称</th><th>类型</th><th>格式</th><th>状态</th><th>进度</th><th>记录数</th><th>大小</th><th>创建时间</th><th>失败原因</th><th>动作</th>
            </tr>
          </thead>
          <tbody>{items.map((job) => (
            <tr key={job.id} data-testid="admin-secure-async-export-center-row">
              <td>{job.id}</td>
              <td>{job.jobName}</td>
              <td>{job.exportType}</td>
              <td>{job.format}</td>
              <td>{statusLabel(job.status)}</td>
              <td>{job.progress}%</td>
              <td>{job.recordCount}</td>
              <td>{job.fileSizeBytes ?? '-'}</td>
              <td>{displayTime(job.createdAt)}</td>
              <td>{job.failureReason ?? '-'}</td>
              <td>
                <button type="button" data-testid="admin-secure-async-export-center-download" disabled={job.status !== 'COMPLETED'} onClick={() => download.mutate(job)}>下载加密包</button>
                <button type="button" data-testid="admin-secure-async-export-center-retry" disabled={job.status !== 'FAILED'} onClick={() => retry.mutate(job)}>重试</button>
              </td>
            </tr>
          ))}</tbody>
        </table>
      </section>
    </section>
  );
}
