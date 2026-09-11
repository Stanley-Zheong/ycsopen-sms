import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '@/api/complaintCaseApi';
import type { ComplaintCaseRow } from '@/api/complaintCaseApi';
import AdminComplaintAnalyticsPage from '@/pages/admin/complaints/AdminComplaintAnalyticsPage';
import AdminComplaintsPage from '@/pages/admin/complaints/AdminComplaintsPage';

vi.mock('@/api/complaintCaseApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/complaintCaseApi')>();
  return {
    ...actual,
    listComplaintCases: vi.fn(),
    createComplaintCase: vi.fn(),
    acceptComplaintCase: vi.fn(),
    handleComplaintCase: vi.fn(),
    closeComplaintCase: vi.fn(),
    remediateComplaintCase: vi.fn(),
    recoverComplaintRemediation: vi.fn(),
    getComplaintAnalytics: vi.fn(),
  };
});

function renderWithQuery(ui: React.ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

const caseRow: ComplaintCaseRow = {
  id: 1,
  source: 'REGULATOR',
  tenantId: 7,
  channelId: 11,
  signatureId: 8,
  templateId: 9,
  messageId: 'MSG-1',
  contentType: 'MARKETING',
  complainedMobile: '13800138000',
  summary: '监管投诉：营销短信扰民',
  status: 'PENDING',
  attributionQuality: 'COMPLETE',
  opinion: null,
  remediation: null,
  requirement: '24小时内反馈',
  acceptedAt: null,
  handledAt: null,
  closedAt: null,
  closedNote: null,
};

describe('Phase 41 complaint case management UI', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.listComplaintCases).mockResolvedValue([caseRow]);
    vi.mocked(api.createComplaintCase).mockResolvedValue(caseRow);
    vi.mocked(api.acceptComplaintCase).mockResolvedValue({ ...caseRow, status: 'PROCESSING' });
    vi.mocked(api.handleComplaintCase).mockResolvedValue({ ...caseRow, status: 'PROCESSED', opinion: '投诉属实', remediation: '暂停通道' });
    vi.mocked(api.closeComplaintCase).mockResolvedValue({ ...caseRow, status: 'CLOSED', closedNote: '复核关闭' });
    vi.mocked(api.remediateComplaintCase).mockResolvedValue({ id: 2, complaintId: 1, disposalType: 'SUSPEND_CHANNEL', targetRef: 'channel:11', status: 'APPLIED', authorizedReviewId: 'review-41-1', failureReason: null, originalComplaintId: 1 });
    vi.mocked(api.recoverComplaintRemediation).mockResolvedValue({ id: 2, complaintId: 1, disposalType: 'SUSPEND_CHANNEL', targetRef: 'channel:11', status: 'RECOVERED', authorizedReviewId: 'review-41-recovery', failureReason: null, originalComplaintId: 1 });
    vi.mocked(api.getComplaintAnalytics).mockResolvedValue({
      totalCount: 2,
      unknownAttributionCount: 1,
      byTenant: [{ dimension: 'tenant:7', count: 1 }],
      bySignature: [{ dimension: 'signature:8', count: 1 }],
      byContentType: [{ dimension: 'MARKETING', count: 2 }],
    });
  });

  it('records source links state actions remediation recovery and attribution quality', async () => {
    renderWithQuery(<AdminComplaintsPage />);

    expect(await screen.findByTestId('admin-complaint-case-complaints-page')).toBeVisible();
    expect(await screen.findByTestId('admin-complaint-case-complaints-attribution-quality')).toHaveTextContent('COMPLETE');
    expect(screen.getByTestId('admin-complaint-case-complaints-remediation-resource')).toHaveTextContent('signature:8');

    fireEvent.click(screen.getByTestId('admin-complaint-case-complaints-create'));
    await waitFor(() => expect(api.createComplaintCase).toHaveBeenCalledWith(expect.objectContaining({
      source: 'REGULATOR',
      tenantId: 7,
      channelId: 11,
      signatureId: 8,
      templateId: 9,
    })));
    fireEvent.click(screen.getByTestId('admin-complaint-case-complaints-accept'));
    await waitFor(() => expect(api.acceptComplaintCase).toHaveBeenCalledWith(1, expect.objectContaining({ actor: 'operator' })));
    fireEvent.click(screen.getByTestId('admin-complaint-case-complaints-handle'));
    await waitFor(() => expect(api.handleComplaintCase).toHaveBeenCalledWith(1, expect.objectContaining({ opinion: '投诉属实' })));
    fireEvent.click(screen.getByTestId('admin-complaint-case-complaints-remediation'));
    await waitFor(() => expect(api.remediateComplaintCase).toHaveBeenCalledWith(1, expect.objectContaining({ targetRef: 'channel:11' })));
    fireEvent.click(screen.getByTestId('admin-complaint-case-complaints-remediation-recovery'));
    await waitFor(() => expect(api.recoverComplaintRemediation).toHaveBeenCalledWith(1, expect.objectContaining({ authorizedReviewId: 'review-41-recovery' })));
    fireEvent.click(screen.getByTestId('admin-complaint-case-complaints-close'));
    await waitFor(() => expect(api.closeComplaintCase).toHaveBeenCalledWith(1, expect.objectContaining({ opinion: '复核关闭' })));

    expect(screen.getByTestId('admin-complaint-case-complaints-state-action')).toHaveTextContent('PENDING');
  });

  it('runs row actions against the selected complaint and derived remediation target', async () => {
    vi.mocked(api.listComplaintCases).mockResolvedValue([
      caseRow,
      { ...caseRow, id: 2, channelId: null, signatureId: 18, templateId: 19, messageId: 'MSG-2' },
    ]);
    renderWithQuery(<AdminComplaintsPage />);

    const rows = await screen.findAllByTestId('admin-complaint-case-complaints-row');
    fireEvent.click(rows[1].querySelector('[data-testid="admin-complaint-case-complaints-accept"]') as HTMLElement);
    await waitFor(() => expect(api.acceptComplaintCase).toHaveBeenCalledWith(2, expect.objectContaining({ actor: 'operator' })));
    fireEvent.click(rows[1].querySelector('[data-testid="admin-complaint-case-complaints-remediation"]') as HTMLElement);
    await waitFor(() => expect(api.remediateComplaintCase).toHaveBeenCalledWith(2, expect.objectContaining({
      disposalType: 'SUSPEND_SIGNATURE_OR_TEMPLATE',
      targetRef: 'signature:18',
    })));
  });

  it('does not submit recovery without a remediation record from the current case action', async () => {
    renderWithQuery(<AdminComplaintsPage />);

    const recover = await screen.findByTestId('admin-complaint-case-complaints-remediation-recovery');

    expect(recover).toBeDisabled();
    fireEvent.click(recover);
    expect(api.recoverComplaintRemediation).not.toHaveBeenCalled();
  });

  it('renders analytics trend and distribution with unknown attribution quality', async () => {
    renderWithQuery(<AdminComplaintAnalyticsPage />);

    expect(await screen.findByTestId('admin-complaint-case-analytics-page')).toHaveTextContent('投诉趋势与分布');
    await waitFor(() => expect(screen.getByTestId('admin-complaint-case-analytics-quality')).toHaveTextContent('未知归因 1'));
    expect(screen.getByTestId('admin-complaint-case-analytics-tenant')).toHaveTextContent('tenant:7');
    expect(screen.getByTestId('admin-complaint-case-analytics-signature')).toHaveTextContent('signature:8');
    expect(screen.getByTestId('admin-complaint-case-analytics-content-type')).toHaveTextContent('MARKETING');
  });
});
