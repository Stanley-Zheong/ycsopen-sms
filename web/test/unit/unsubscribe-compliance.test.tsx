import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '@/api/unsubscribeComplianceApi';
import AdminUnsubscribesPage from '@/pages/admin/unsubscribes/AdminUnsubscribesPage';
import TenantUnsubscribesPage from '@/pages/tenant/unsubscribes/TenantUnsubscribesPage';

vi.mock('@/api/unsubscribeComplianceApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/unsubscribeComplianceApi')>();
  return {
    ...actual,
    listAdminUnsubscribes: vi.fn(),
    listAdminUnsubscribeKeywords: vi.fn(),
    saveAdminUnsubscribeKeyword: vi.fn(),
    listUnsubscribeStatistics: vi.fn(),
    evaluateUnsubscribeAlerts: vi.fn(),
    listTenantUnsubscribes: vi.fn(),
    listTenantUnsubscribeKeywords: vi.fn(),
    saveTenantUnsubscribeKeyword: vi.fn(),
    requestTenantUnsubscribeExport: vi.fn(),
  };
});

function renderWithQuery(ui: React.ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

const record = {
  id: 701,
  tenantId: 7,
  signatureId: 88,
  productCode: 'STANDARD',
  maskedMobile: '138****8000',
  triggerKeyword: 'TD',
  method: 'UPLINK',
  result: 'TENANT_BLACKLISTED',
  handlingState: 'TENANT_BLACKLISTED',
  notificationState: 'PENDING',
  notificationEventId: 501,
  confirmationState: 'DISABLED',
  replyEventId: null,
  uplinkRecordId: 9001,
  unsubscribedAt: '2026-09-10T10:00:00',
  updatedAt: '2026-09-10T10:01:00',
};

describe('Phase 33 unsubscribe compliance UI', () => {
  beforeEach(() => {
    vi.mocked(api.listAdminUnsubscribes).mockResolvedValue([record]);
    vi.mocked(api.listAdminUnsubscribeKeywords).mockResolvedValue([
      { id: 1, keyword: 'TD', keywordNormalized: 'TD', scope: 'GLOBAL', tenantId: null, status: 'ACTIVE', createdBy: 'system', updatedAt: null },
      { id: 2, keyword: '退订', keywordNormalized: '退订', scope: 'GLOBAL', tenantId: null, status: 'ACTIVE', createdBy: 'system', updatedAt: null },
    ]);
    vi.mocked(api.saveAdminUnsubscribeKeyword).mockResolvedValue({ id: 3, keyword: 'QUIT', keywordNormalized: 'QUIT', scope: 'GLOBAL', tenantId: null, status: 'ACTIVE', createdBy: 'operator', updatedAt: null });
    vi.mocked(api.listUnsubscribeStatistics).mockResolvedValue([
      { tenantId: 7, signatureId: 88, productCode: 'STANDARD', unsubscribeCount: 2, finalSentCount: 200, rate: 0.01 },
    ]);
    vi.mocked(api.evaluateUnsubscribeAlerts).mockResolvedValue([
      { id: 1, tenantId: 7, signatureId: 88, productCode: 'STANDARD', periodStart: null, periodEnd: null, unsubscribeCount: 2, finalSentCount: 20, rate: 0.1, thresholdRate: 0.03, formula: 'unsubscribe_count/final_sent_count', freshnessAt: null, sourceEvent: 'UNSUBSCRIBE_RATE_ABNORMAL' },
    ]);
    vi.mocked(api.listTenantUnsubscribes).mockResolvedValue([record]);
    vi.mocked(api.listTenantUnsubscribeKeywords).mockResolvedValue([
      { id: 1, keyword: 'TD', keywordNormalized: 'TD', scope: 'GLOBAL', tenantId: null, status: 'ACTIVE', createdBy: 'system', updatedAt: null },
    ]);
    vi.mocked(api.saveTenantUnsubscribeKeyword).mockResolvedValue({ id: 9, keyword: 'STOP', keywordNormalized: 'STOP', scope: 'TENANT', tenantId: 7, status: 'ACTIVE', createdBy: '7', updatedAt: null });
    vi.mocked(api.requestTenantUnsubscribeExport).mockResolvedValue({ taskId: 66, status: 'PENDING', recordCount: 1, fileFormat: 'CSV' });
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('shows admin keyword evidence statistics and alert actions', async () => {
    renderWithQuery(<AdminUnsubscribesPage />);

    expect(await screen.findByTestId('admin-unsubscribe-compliance-keywords-page')).toBeVisible();
    expect(await screen.findByTestId('admin-unsubscribe-compliance-unsubscribes-page')).toBeVisible();
    expect(await screen.findByTestId('admin-unsubscribe-compliance-statistics-page')).toBeVisible();
    expect(await screen.findByTestId('admin-unsubscribe-compliance-unsubscribe-row')).toHaveTextContent('138****8000');
    expect(screen.getByTestId('admin-unsubscribe-compliance-unsubscribes-notification-state')).toHaveTextContent('PENDING');
    fireEvent.change(screen.getByTestId('admin-unsubscribe-compliance-keyword-input'), { target: { value: 'QUIT' } });
    fireEvent.click(screen.getByTestId('admin-unsubscribe-compliance-keyword-save'));
    await waitFor(() => expect(api.saveAdminUnsubscribeKeyword).toHaveBeenCalledWith(expect.objectContaining({ keyword: 'QUIT', scope: 'GLOBAL' })));
    fireEvent.click(screen.getByTestId('admin-unsubscribe-compliance-alert-evaluate'));
    await waitFor(() => expect(api.evaluateUnsubscribeAlerts).toHaveBeenCalledWith(expect.objectContaining({ thresholdRate: 0.03 })));
  });

  it('shows tenant scoped evidence keyword management and export request', async () => {
    renderWithQuery(<TenantUnsubscribesPage />);

    expect(await screen.findByTestId('tenant-unsubscribe-compliance-unsubscribes-page')).toBeVisible();
    expect(await screen.findByTestId('tenant-unsubscribe-compliance-unsubscribe-row')).toHaveTextContent('TD');
    expect(screen.getByTestId('tenant-unsubscribe-compliance-unsubscribes-notification-state')).toHaveTextContent('PENDING');
    fireEvent.click(screen.getByTestId('tenant-secure-async-unsubscribes-export'));
    await waitFor(() => expect(api.requestTenantUnsubscribeExport).toHaveBeenCalled());
    fireEvent.change(screen.getByTestId('tenant-unsubscribe-compliance-keyword-input'), { target: { value: 'STOP' } });
    fireEvent.click(screen.getByTestId('tenant-unsubscribe-compliance-keyword-save'));
    await waitFor(() => expect(api.saveTenantUnsubscribeKeyword).toHaveBeenCalledWith(expect.objectContaining({ keyword: 'STOP', scope: 'TENANT' })));
  });
});
