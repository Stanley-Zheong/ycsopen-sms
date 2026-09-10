import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as customReportApi from '@/api/customReportApi';
import AdminCustomReportsPage from '@/pages/admin/reports/AdminCustomReportsPage';

vi.mock('@/api/customReportApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/customReportApi')>();
  return {
    ...actual,
    listCustomReportCapabilities: vi.fn(),
    previewCustomReport: vi.fn(),
    saveCustomReportDefinition: vi.fn(),
    listCustomReportDefinitions: vi.fn(),
    requestCustomReportExport: vi.fn(),
  };
});

function renderWithProviders(ui: ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
    </MemoryRouter>,
  );
}

describe('Phase 43 custom report authoring UI', () => {
  beforeEach(() => {
    vi.mocked(customReportApi.listCustomReportCapabilities).mockResolvedValue([{
      metricCode: 'CHANNEL_DELIVERY',
      metricName: '通道发送成功成本延迟指标',
      dimensions: ['period', 'tenant_id', 'channel_id', 'carrier', 'province', 'message_type'],
      measures: ['submit_count', 'send_count', 'success_count', 'failure_count', 'fee_amount', 'avg_response_ms'],
      formula: 'send/success/failure/cost/latency grouped by channel and geography',
      freshnessRule: 'freshness_at >= latest source updated_at',
      permissionScope: 'PLATFORM',
      formulaVersion: 'v1',
    }, {
      metricCode: 'TENANT_BEHAVIOR',
      metricName: '租户发送消费活跃指标',
      dimensions: ['period', 'tenant_id', 'message_type'],
      measures: ['submit_count', 'accepted_count', 'rejected_count', 'send_count', 'success_count', 'failure_count', 'fee_amount'],
      formula: 'accepted/rejected/send/success/failure/consumption grouped by tenant',
      freshnessRule: 'freshness_at >= tenant source updated_at',
      permissionScope: 'TENANT',
      formulaVersion: 'v1',
    }]);
    vi.mocked(customReportApi.previewCustomReport).mockImplementation(async (command) => ({
      metricCode: command.metricCode,
      metricName: command.metricCode === 'TENANT_BEHAVIOR' ? '租户发送消费活跃指标' : '通道发送成功成本延迟指标',
      formula: command.metricCode === 'TENANT_BEHAVIOR'
        ? 'accepted/rejected/send/success/failure/consumption grouped by tenant'
        : 'send/success/failure/cost/latency grouped by channel and geography',
      formulaVersion: 'v1',
      freshnessRule: 'freshness_at >= latest source updated_at',
      freshnessAt: '2026-09-01T01:03:00',
      qualityState: 'FRESH',
      accessibleColumns: command.dimensions.concat(command.measures),
      truncated: false,
      rows: [{
        values: {
          period: '2026-09-01',
          tenant_id: 7,
          channel_id: 11,
          carrier: 'MOBILE',
          province: '广东',
          message_type: 'VERIFY',
          send_count: 20,
          success_count: 18,
          fee_amount: 0.32,
        },
        drilldownKey: command.metricCode === 'TENANT_BEHAVIOR' ? 'drill-tenant-43' : 'drill-43',
        qualityState: 'FRESH',
        freshnessAt: '2026-09-01T01:03:00',
      }],
    }));
    vi.mocked(customReportApi.saveCustomReportDefinition).mockResolvedValue({
      id: 5,
      reportName: '通道日报',
      metricCode: 'CHANNEL_DELIVERY',
      tenantId: 7,
      roleScope: 'PLATFORM',
      definitionSnapshot: '{"formulaVersion":"v1"}',
      status: 'ACTIVE',
      createdBy: 'finance',
      createdAt: '2026-09-01T02:00:00',
    });
    vi.mocked(customReportApi.listCustomReportDefinitions).mockResolvedValue([]);
    vi.mocked(customReportApi.requestCustomReportExport).mockResolvedValue({
      id: 6,
      reportDefinitionId: 5,
      definitionSnapshot: '{"formulaVersion":"v1"}',
      status: 'REQUESTED',
      requestedBy: 'finance',
      requestedAt: '2026-09-01T02:01:00',
    });
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('builds previews saves and exports supported custom reports with accessible result table', async () => {
    renderWithProviders(<AdminCustomReportsPage />);

    expect(await screen.findByTestId('admin-custom-report-custom-reports-page')).toBeVisible();
    expect(screen.getByTestId('admin-custom-report-custom-reports-builder')).toBeVisible();
    fireEvent.change(screen.getByTestId('admin-custom-report-custom-reports-name'), { target: { value: '通道日报' } });
    fireEvent.change(screen.getByTestId('admin-custom-report-custom-reports-tenant'), { target: { value: '7' } });
    fireEvent.change(screen.getByTestId('admin-custom-report-custom-reports-channel'), { target: { value: '11' } });
    fireEvent.click(screen.getByTestId('admin-custom-report-custom-reports-preview'));

    await waitFor(() => expect(customReportApi.previewCustomReport).toHaveBeenCalled());
    expect(vi.mocked(customReportApi.previewCustomReport).mock.calls[0][0]).toMatchObject({
      reportName: '通道日报',
      metricCode: 'CHANNEL_DELIVERY',
      tenantId: 7,
      channelId: 11,
      dimensions: ['period', 'tenant_id', 'channel_id', 'carrier', 'province', 'message_type'],
      measures: ['submit_count', 'send_count', 'success_count', 'failure_count', 'fee_amount', 'avg_response_ms'],
    });
    expect(await screen.findByTestId('admin-custom-report-custom-reports-results')).toHaveTextContent('drill-43');
    expect(screen.getByTestId('admin-custom-report-custom-reports-formula')).toHaveTextContent('v1');
    expect(screen.getByTestId('admin-custom-report-custom-reports-freshness')).toHaveTextContent('2026-09-01T01:03:00');
    expect(screen.getByTestId('admin-custom-report-custom-reports-truncated')).toHaveTextContent('否');
    expect(screen.getByTestId('admin-custom-report-custom-reports-accessible-table')).toHaveTextContent('MOBILE');

    fireEvent.click(screen.getByTestId('admin-custom-report-custom-reports-save'));
    expect(await screen.findByTestId('admin-custom-report-custom-reports-saved-definition')).toHaveTextContent('通道日报');

    fireEvent.click(screen.getByTestId('admin-custom-report-custom-reports-export'));
    expect(await screen.findByTestId('admin-custom-report-custom-reports-export-status')).toHaveTextContent('REQUESTED');

    fireEvent.change(screen.getByTestId('admin-custom-report-custom-reports-metric'), { target: { value: 'TENANT_BEHAVIOR' } });
    expect(screen.getByTestId('admin-custom-report-custom-reports-dimensions')).toHaveTextContent('period / tenant_id / message_type');
    expect(screen.getByTestId('admin-custom-report-custom-reports-measures')).toHaveTextContent('accepted_count');
    fireEvent.click(screen.getByTestId('admin-custom-report-custom-reports-preview'));

    await waitFor(() => expect(customReportApi.previewCustomReport).toHaveBeenCalledTimes(2));
    expect(vi.mocked(customReportApi.previewCustomReport).mock.calls[1][0]).toMatchObject({
      metricCode: 'TENANT_BEHAVIOR',
      roleScope: 'TENANT',
      dimensions: ['period', 'tenant_id', 'message_type'],
      measures: ['submit_count', 'accepted_count', 'rejected_count', 'send_count', 'success_count', 'failure_count', 'fee_amount'],
    });
  });
});
