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
    listComplaintRemediations: vi.fn(),
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
    vi.mocked(api.listComplaintRemediations).mockResolvedValue([]);
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
      trend: [{ day: '2026-09-12', count: 2 }],
    });
  });

  it('records source links state actions remediation recovery and attribution quality', async () => {
    renderWithQuery(<AdminComplaintsPage />);

    expect(await screen.findByTestId('admin-complaint-case-complaints-page')).toBeVisible();
    expect(await screen.findByTestId('admin-complaint-case-complaints-attribution-quality')).toHaveTextContent('COMPLETE');
    expect(screen.getByTestId('admin-complaint-case-complaints-remediation-resource')).toHaveTextContent('signature:8');
    expect(screen.getByTitle('REGULATOR')).toHaveClass('complaint-table-truncate');
    expect(screen.getByTitle('COMPLETE')).toHaveClass('complaint-table-truncate');
    expect(screen.getByTitle('PENDING')).toHaveClass('complaint-table-truncate');

    fireEvent.click(screen.getByTestId('form-submit'));
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
    expect(screen.getByTestId('admin-complaint-case-complaints-remediation-recovery')).toBeDisabled();
    fireEvent.click(screen.getByTestId('admin-complaint-case-complaints-close'));
    await waitFor(() => expect(api.closeComplaintCase).toHaveBeenCalledWith(1, expect.objectContaining({ opinion: '复核关闭' })));

    expect(screen.getByTestId('admin-complaint-case-complaints-state-action')).toHaveTextContent('PENDING');
  });

  it('renders ordered complaint cards and a structured empty table', async () => {
    vi.mocked(api.listComplaintCases).mockResolvedValue([]);
    renderWithQuery(<AdminComplaintsPage />);

    const cardIds = [
      'admin-complaint-case-complaints-intake-card',
      'admin-complaint-case-complaints-attribution-card',
      'admin-complaint-case-complaints-evidence-card',
      'admin-complaint-case-complaints-list-card',
    ];
    const cardTitles = ['投诉登记', '归因与要求', '处理证据', '投诉列表'];
    const cards = cardIds.map((cardId) => screen.getByTestId(cardId));

    cards.forEach((card, index) => {
      expect(card).toHaveClass('card');
      expect(card).toHaveTextContent(cardTitles[index]);
      if (index > 0) {
        expect(cards[index - 1].compareDocumentPosition(card) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
      }
    });
    expect(screen.getByTestId('entity-form')).toContainElement(screen.getByTestId('form-actions'));
    expect(screen.getByTestId('data-table')).toHaveTextContent('来源摘要归因质量状态处置资源要求动作');
    expect(await screen.findByTestId('table-empty')).toHaveTextContent('暂无投诉记录');
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

  it('enables recovery only for a persisted failed remediation after refresh', async () => {
    vi.mocked(api.listComplaintRemediations).mockResolvedValue([{ id: 4, complaintId: 1, disposalType: 'SUSPEND_CHANNEL', targetRef: 'channel:11', status: 'FAILED', authorizedReviewId: 'review-41-fail', failureReason: 'provider timeout', originalComplaintId: 1 }]);
    renderWithQuery(<AdminComplaintsPage />);

    const recover = await screen.findByTestId('admin-complaint-case-complaints-remediation-recovery');

    await waitFor(() => expect(recover).toBeEnabled());
    expect(screen.getByTestId('admin-complaint-case-complaints-remediation-status')).toHaveTextContent('FAILED');
    expect(screen.getByTestId('admin-complaint-case-complaints-remediation-status')).toHaveTextContent('provider timeout');
    fireEvent.click(recover);
    await waitFor(() => expect(api.recoverComplaintRemediation).toHaveBeenCalledWith(1, expect.objectContaining({
      disposalRecordId: 4,
      authorizedReviewId: 'review-41-recovery',
    })));
  });

  it('keeps applied remediation visible but not recoverable', async () => {
    vi.mocked(api.listComplaintRemediations).mockResolvedValue([{ id: 2, complaintId: 1, disposalType: 'SUSPEND_CHANNEL', targetRef: 'channel:11', status: 'APPLIED', authorizedReviewId: 'review-41-1', failureReason: null, originalComplaintId: 1 }]);
    renderWithQuery(<AdminComplaintsPage />);

    expect(await screen.findByTestId('admin-complaint-case-complaints-remediation-status')).toHaveTextContent('APPLIED');
    expect(screen.getByTestId('admin-complaint-case-complaints-remediation-recovery')).toBeDisabled();
    expect(api.recoverComplaintRemediation).not.toHaveBeenCalled();
  });

  it('identifies and recovers the newest failed record when a newer applied record exists', async () => {
    vi.mocked(api.listComplaintRemediations).mockResolvedValue([
      { id: 5, complaintId: 1, disposalType: 'SUSPEND_CHANNEL', targetRef: 'channel:11', status: 'APPLIED', authorizedReviewId: 'review-41-5', failureReason: null, originalComplaintId: 1 },
      { id: 4, complaintId: 1, disposalType: 'SUSPEND_CHANNEL', targetRef: 'channel:11', status: 'FAILED', authorizedReviewId: 'review-41-4', failureReason: 'latest failure', originalComplaintId: 1 },
      { id: 3, complaintId: 1, disposalType: 'SUSPEND_CHANNEL', targetRef: 'channel:11', status: 'FAILED', authorizedReviewId: 'review-41-3', failureReason: 'older failure', originalComplaintId: 1 },
    ]);
    renderWithQuery(<AdminComplaintsPage />);

    const status = await screen.findByTestId('admin-complaint-case-complaints-remediation-status');
    expect(status).toHaveTextContent('APPLIED #5');
    expect(status).toHaveTextContent('待恢复 FAILED #4：latest failure');
    const recover = screen.getByTestId('admin-complaint-case-complaints-remediation-recovery');
    expect(recover).toHaveAttribute('title', '恢复处置记录 #4');
    fireEvent.click(recover);
    await waitFor(() => expect(api.recoverComplaintRemediation).toHaveBeenCalledWith(1, expect.objectContaining({
      disposalRecordId: 4,
    })));
  });

  it('reports remediation readback failure and keeps recovery unavailable', async () => {
    vi.mocked(api.listComplaintRemediations).mockRejectedValue(new Error('network unavailable'));
    renderWithQuery(<AdminComplaintsPage />);

    expect(await screen.findByTestId('admin-complaint-case-complaints-remediation-load-error'))
      .toHaveTextContent('处置记录加载失败，恢复功能暂不可用');
    expect(screen.getByTestId('admin-complaint-case-complaints-remediation-status'))
      .toHaveTextContent('处置记录加载失败');
    expect(screen.getByTestId('admin-complaint-case-complaints-remediation-recovery')).toBeDisabled();
  });

  it('renders analytics trend and distribution with unknown attribution quality', async () => {
    renderWithQuery(<AdminComplaintAnalyticsPage />);

    expect(await screen.findByTestId('admin-complaint-case-analytics-page')).toHaveTextContent('投诉趋势与分布');
    await waitFor(() => expect(screen.getByTestId('admin-complaint-case-analytics-quality')).toHaveTextContent('未知归因 1'));
    expect(screen.getByTestId('admin-complaint-case-analytics-trend')).toHaveTextContent('2026-09-12：2');
    expect(screen.getByTestId('admin-complaint-case-analytics-tenant')).toHaveTextContent('tenant:7');
    expect(screen.getByTestId('admin-complaint-case-analytics-signature')).toHaveTextContent('signature:8');
    expect(screen.getByTestId('admin-complaint-case-analytics-content-type')).toHaveTextContent('MARKETING');
  });

  it('does not present zero analytics while the request is loading', () => {
    vi.mocked(api.getComplaintAnalytics).mockReturnValue(new Promise<never>(() => {}));
    renderWithQuery(<AdminComplaintAnalyticsPage />);

    expect(screen.getByTestId('admin-complaint-case-analytics-loading')).toHaveTextContent('正在加载投诉分析');
    expect(screen.queryByTestId('admin-complaint-case-analytics-quality')).not.toBeInTheDocument();
    expect(screen.queryByTestId('admin-complaint-case-analytics-trend')).not.toBeInTheDocument();
  });

  it('shows only the analytics error state when the request fails', async () => {
    vi.mocked(api.getComplaintAnalytics).mockRejectedValue(new Error('analytics unavailable'));
    renderWithQuery(<AdminComplaintAnalyticsPage />);

    expect(await screen.findByTestId('admin-complaint-case-analytics-error')).toHaveTextContent('投诉分析加载失败');
    expect(screen.queryByTestId('admin-complaint-case-analytics-quality')).not.toBeInTheDocument();
    expect(screen.queryByTestId('admin-complaint-case-analytics-trend')).not.toBeInTheDocument();
  });
});
