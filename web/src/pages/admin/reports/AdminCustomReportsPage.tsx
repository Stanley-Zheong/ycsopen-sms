import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  listCustomReportCapabilities,
  listCustomReportDefinitions,
  previewCustomReport,
  requestCustomReportExport,
  saveCustomReportDefinition,
  type CustomReportCommand,
  type CustomReportDefinition,
  type CustomReportPreview,
} from '@/api/customReportApi';

const DEFAULT_START = '2026-09-01T00:00:00';
const DEFAULT_END = '2026-09-02T00:00:00';

export default function AdminCustomReportsPage() {
  const queryClient = useQueryClient();
  const [reportName, setReportName] = useState('自定义报表');
  const [tenantId, setTenantId] = useState('7');
  const [channelId, setChannelId] = useState('');
  const [messageType, setMessageType] = useState('');
  const [province, setProvince] = useState('');
  const [selectedMetricCode, setSelectedMetricCode] = useState('CHANNEL_DELIVERY');
  const [preview, setPreview] = useState<CustomReportPreview | null>(null);
  const [saved, setSaved] = useState<CustomReportDefinition | null>(null);
  const [exportStatus, setExportStatus] = useState('');
  const [error, setError] = useState('');
  const capabilities = useQuery({
    queryKey: ['custom-report-capabilities'],
    queryFn: listCustomReportCapabilities,
  });
  const definitions = useQuery({
    queryKey: ['custom-report-definitions'],
    queryFn: listCustomReportDefinitions,
  });
  const selected = capabilities.data?.find((metric) => metric.metricCode === selectedMetricCode) || capabilities.data?.[0];
  const command = useMemo<CustomReportCommand>(() => ({
    reportName,
    metricCode: selected?.metricCode || 'CHANNEL_DELIVERY',
    dimensions: selected?.dimensions || ['period', 'tenant_id', 'channel_id', 'carrier', 'province', 'message_type'],
    measures: selected?.measures || ['send_count', 'success_count', 'fee_amount'],
    tenantId: numberOrNull(tenantId),
    channelId: numberOrNull(channelId),
    messageType: textOrNull(messageType),
    province: textOrNull(province),
    startTime: DEFAULT_START,
    endTime: DEFAULT_END,
    roleScope: selected?.permissionScope === 'TENANT' ? 'TENANT' : 'PLATFORM',
  }), [channelId, messageType, province, reportName, selected, tenantId]);
  const previewMutation = useMutation({
    mutationFn: previewCustomReport,
    onSuccess: (result) => {
      setError('');
      setPreview(result);
    },
    onError: (failure) => setError(message(failure)),
  });
  const saveMutation = useMutation({
    mutationFn: saveCustomReportDefinition,
    onSuccess: async (definition) => {
      setError('');
      setSaved(definition);
      await queryClient.invalidateQueries({ queryKey: ['custom-report-definitions'] });
    },
    onError: (failure) => setError(message(failure)),
  });
  const exportMutation = useMutation({
    mutationFn: requestCustomReportExport,
    onSuccess: (request) => {
      setError('');
      setExportStatus(request.status);
    },
    onError: (failure) => setError(message(failure)),
  });

  return (
    <section className="card" data-testid="admin-custom-report-custom-reports-page">
      <h1>自定义报表</h1>
      <p>仅允许选择聚合注册表声明的维度和指标，结果保留公式、版本、新鲜度和质量状态。</p>

      <section className="card" data-testid="admin-custom-report-custom-reports-builder">
        <h2>报表构建器</h2>
        <div className="form-grid">
          <label>报表名称
            <input data-testid="admin-custom-report-custom-reports-name" value={reportName} onChange={(e) => setReportName(e.target.value)} />
          </label>
          <label>来源指标
            <select data-testid="admin-custom-report-custom-reports-metric" value={selected?.metricCode || selectedMetricCode} onChange={(event) => setSelectedMetricCode(event.target.value)}>
              {(capabilities.data || []).map((metric) => (
                <option key={metric.metricCode} value={metric.metricCode}>{metric.metricName}</option>
              ))}
              {!capabilities.data?.length && <option value="CHANNEL_DELIVERY">通道发送成功成本延迟指标</option>}
            </select>
          </label>
          <label>租户ID
            <input data-testid="admin-custom-report-custom-reports-tenant" value={tenantId} onChange={(e) => setTenantId(e.target.value)} />
          </label>
          <label>通道ID
            <input data-testid="admin-custom-report-custom-reports-channel" value={channelId} onChange={(e) => setChannelId(e.target.value)} />
          </label>
          <label>消息类型
            <input data-testid="admin-custom-report-custom-reports-message-type" value={messageType} onChange={(e) => setMessageType(e.target.value)} />
          </label>
          <label>省份
            <input data-testid="admin-custom-report-custom-reports-province" value={province} onChange={(e) => setProvince(e.target.value)} />
          </label>
        </div>
        <div data-testid="admin-custom-report-custom-reports-dimensions">维度：{command.dimensions.join(' / ')}</div>
        <div data-testid="admin-custom-report-custom-reports-measures">指标：{command.measures.join(' / ')}</div>
        <button data-testid="admin-custom-report-custom-reports-preview" type="button" onClick={() => previewMutation.mutate(command)}>预览报表</button>
        <button data-testid="admin-custom-report-custom-reports-save" type="button" onClick={() => saveMutation.mutate(command)}>保存定义</button>
        {error && <div data-testid="admin-custom-report-custom-reports-error">{error}</div>}
      </section>

      {selected && (
        <section className="card" data-testid="admin-custom-report-custom-reports-registry">
          <h2>聚合注册表</h2>
          <div data-testid="admin-custom-report-custom-reports-formula">公式：{preview?.formulaVersion || selected.formulaVersion} / {preview?.formula || selected.formula}</div>
          <div>权限：{selected.permissionScope}</div>
          <div>新鲜度规则：{selected.freshnessRule}</div>
        </section>
      )}

      <section className="card" data-testid="admin-custom-report-custom-reports-results">
        <h2>可视化结果</h2>
        <div data-testid="admin-custom-report-custom-reports-freshness">新鲜度：{preview?.freshnessAt || '-'}</div>
        <div data-testid="admin-custom-report-custom-reports-quality">质量：{preview?.qualityState || '-'}</div>
        <div data-testid="admin-custom-report-custom-reports-truncated">截断：{preview?.truncated ? '是，最多显示500行' : '否'}</div>
        <table data-testid="admin-custom-report-custom-reports-accessible-table">
          <thead>
            <tr>
              {(preview?.accessibleColumns || command.dimensions.concat(command.measures)).map((column) => <th key={column}>{column}</th>)}
              <th>drilldown</th>
            </tr>
          </thead>
          <tbody>
            {(preview?.rows || []).map((row) => (
              <tr key={row.drilldownKey} data-testid="admin-custom-report-custom-reports-row">
                {(preview?.accessibleColumns || []).map((column) => <td key={column}>{row.values[column] ?? '-'}</td>)}
                <td>{row.drilldownKey}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      {saved && (
        <section className="card" data-testid="admin-custom-report-custom-reports-saved-definition">
          <h2>已保存定义</h2>
          <div>{saved.reportName} / {saved.metricCode} / {saved.status}</div>
          <button data-testid="admin-custom-report-custom-reports-export" type="button" onClick={() => exportMutation.mutate(saved.id)}>申请导出</button>
        </section>
      )}
      <div data-testid="admin-custom-report-custom-reports-export-status">导出请求：{exportStatus || '-'}</div>
      <section className="card" data-testid="admin-custom-report-custom-reports-definition-list">
        <h2>已保存列表</h2>
        {(definitions.data || []).map((definition) => (
          <div key={definition.id}>{definition.reportName} / {definition.metricCode}</div>
        ))}
      </section>
    </section>
  );
}

function numberOrNull(value: string): number | null {
  const trimmed = value.trim();
  if (!trimmed) return null;
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : null;
}

function textOrNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

function message(error: unknown): string {
  return error instanceof Error ? error.message : '自定义报表操作失败';
}
