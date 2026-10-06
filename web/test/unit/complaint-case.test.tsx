import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '@/api/complaintCaseApi';
import type { ComplaintCaseDetail, ComplaintCaseRow } from '@/api/complaintCaseApi';
import AdminComplaintAnalyticsPage from '@/pages/admin/complaints/AdminComplaintAnalyticsPage';
import AdminComplaintsPage from '@/pages/admin/complaints/AdminComplaintsPage';
import { useAuthStore } from '@/store/authStore';

vi.mock('@/api/complaintCaseApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/complaintCaseApi')>();
  return {
    ...actual,
    listComplaintCases: vi.fn(),
    getComplaintCaseDetail: vi.fn(),
    getComplaintReferenceOptions: vi.fn(),
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
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
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
  complainedMobile: '138****8000',
  summary: '监管投诉：营销短信扰民',
  status: 'PENDING',
  attributionQuality: 'COMPLETE',
  opinion: null,
  remediation: null,
  requirement: '24小时内反馈',
  createdAt: '2026-10-06T08:00:00',
  createdBy: 'operator',
  acceptedAt: null,
  handledAt: null,
  closedAt: null,
  closedNote: null,
  acceptedBy: null,
  handledBy: null,
  closedBy: null,
};

const detail: ComplaintCaseDetail = {
  complaint: caseRow,
  timeline: [{
    id: 1,
    complaintId: 1,
    eventType: 'REGISTERED',
    actor: 'operator',
    occurredAt: '2026-10-06T08:00:00',
    fromStatus: null,
    toStatus: 'PENDING',
    evidenceText: '监管投诉：营销短信扰民',
    targetRef: null,
    result: 'SUCCESS',
    reviewId: null,
    failureReason: null,
    relatedDisposalId: null,
  }],
  remediations: [],
};

const referenceOptions = {
  tenants: [{ id: 7, label: '机构七', tenantId: null }, { id: 9, label: '机构九', tenantId: null }],
  channels: [{ id: 11, label: '主通道', tenantId: null }],
  signatures: [{ id: 8, label: '【优创】', tenantId: 7 }, { id: 18, label: '【机构九】', tenantId: 9 }],
  templates: [{ id: 9, label: '营销模板', tenantId: 7 }],
};

async function openCase() {
  fireEvent.click(await screen.findByTestId('admin-complaint-case-complaints-open-case'));
  return screen.findByTestId('admin-complaint-case-complaints-case-id');
}

describe('Issue 124 complaint case context UI', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({
      accessToken: 'test-token',
      userType: 'OPERATOR',
      tenantId: null,
      expiresAt: Date.now() + 60_000,
      principalKey: 'operator:test-token',
    });
    vi.mocked(api.listComplaintCases).mockResolvedValue([caseRow]);
    vi.mocked(api.getComplaintCaseDetail).mockResolvedValue(detail);
    vi.mocked(api.getComplaintReferenceOptions).mockResolvedValue(referenceOptions);
    vi.mocked(api.createComplaintCase).mockResolvedValue(caseRow);
    vi.mocked(api.acceptComplaintCase).mockResolvedValue({ ...caseRow, status: 'PROCESSING' });
    vi.mocked(api.handleComplaintCase).mockResolvedValue({ ...caseRow, status: 'PROCESSED' });
    vi.mocked(api.closeComplaintCase).mockResolvedValue({ ...caseRow, status: 'CLOSED', closedNote: '复核关闭' });
    vi.mocked(api.remediateComplaintCase).mockResolvedValue({ id: 2, complaintId: 1, disposalType: 'SUSPEND_CHANNEL', targetRef: 'channel:11', status: 'APPLIED', authorizedReviewId: 'review-124', failureReason: null, originalComplaintId: 1 });
    vi.mocked(api.recoverComplaintRemediation).mockResolvedValue({ id: 2, complaintId: 1, disposalType: 'SUSPEND_CHANNEL', targetRef: 'channel:11', status: 'RECOVERED', authorizedReviewId: 'review-124-recovery', failureReason: '处置执行失败，请根据安全审计日志排查', originalComplaintId: 1 });
    vi.mocked(api.getComplaintAnalytics).mockResolvedValue({
      totalCount: 2,
      unknownAttributionCount: 1,
      byTenant: [{ dimension: 'tenant:7', count: 1 }],
      bySignature: [{ dimension: 'signature:8', count: 1 }],
      byContentType: [{ dimension: 'MARKETING', count: 2 }],
      trend: [{ day: '2026-09-12', count: 2 }],
    });
  });

  it('starts intake blank and submits only explicit controlled and source-backed choices', async () => {
    renderWithQuery(<AdminComplaintsPage />);

    expect(await screen.findByRole('option', { name: /机构七/ })).toBeVisible();
    expect(screen.getByTestId('admin-complaint-case-complaints-source')).toHaveValue('');
    expect(screen.getByTestId('admin-complaint-case-complaints-summary')).toHaveValue('');
    expect(screen.getByTestId('admin-complaint-case-complaints-tenant-id')).toHaveValue('');
    expect(screen.getByTestId('form-submit')).toBeDisabled();

    fireEvent.change(screen.getByTestId('admin-complaint-case-complaints-source'), { target: { value: 'USER_REPORT' } });
    fireEvent.change(screen.getByTestId('admin-complaint-case-complaints-summary'), { target: { value: '用户投诉短信内容' } });
    fireEvent.change(screen.getByTestId('admin-complaint-case-complaints-tenant-id'), { target: { value: '7' } });
    fireEvent.change(screen.getByTestId('admin-complaint-case-complaints-signature-id'), { target: { value: '8' } });
    fireEvent.change(screen.getByTestId('admin-complaint-case-complaints-tenant-id'), { target: { value: '9' } });
    expect(screen.getByTestId('admin-complaint-case-complaints-signature-id')).toHaveValue('');
    fireEvent.change(screen.getByTestId('admin-complaint-case-complaints-content-type'), { target: { value: 'NOTIFY' } });
    fireEvent.click(screen.getByTestId('form-submit'));

    await waitFor(() => expect(api.createComplaintCase).toHaveBeenCalledWith(expect.objectContaining({
      source: 'USER_REPORT', summary: '用户投诉短信内容', tenantId: 9, signatureId: null, contentType: 'NOTIFY',
    })));
    expect(api.createComplaintCase).not.toHaveBeenCalledWith(expect.objectContaining({ attributionQuality: expect.anything() }));
  });

  it('latches an intake submission synchronously before pending state renders', async () => {
    vi.mocked(api.createComplaintCase).mockReturnValue(new Promise<ComplaintCaseRow>(() => {}));
    renderWithQuery(<AdminComplaintsPage />);
    await screen.findByRole('option', { name: /机构七/ });
    fireEvent.change(screen.getByTestId('admin-complaint-case-complaints-source'), { target: { value: 'USER_REPORT' } });
    fireEvent.change(screen.getByTestId('admin-complaint-case-complaints-summary'), { target: { value: '重复提交防护' } });

    const form = screen.getByTestId('entity-form');
    act(() => {
      form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
      form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    });

    await waitFor(() => expect(api.createComplaintCase).toHaveBeenCalledTimes(1));
    expect(screen.getByTestId('form-submit')).toBeDisabled();
  });

  it('orders intake, attribution, list and selected-case workspace with independent empty state', async () => {
    vi.mocked(api.listComplaintCases).mockResolvedValue([]);
    renderWithQuery(<AdminComplaintsPage />);

    const ids = ['admin-complaint-case-complaints-intake-card', 'admin-complaint-case-complaints-attribution-card', 'admin-complaint-case-complaints-list-card', 'admin-complaint-case-complaints-case-workspace'];
    const cards = ids.map((id) => screen.getByTestId(id));
    cards.forEach((card, index) => {
      expect(card).toHaveClass('card');
      if (index > 0) expect(cards[index - 1].compareDocumentPosition(card) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    });
    expect(await screen.findByTestId('table-empty')).toHaveTextContent('暂无投诉记录');
    expect(screen.getByTestId('admin-complaint-case-complaints-case-workspace-empty')).toHaveTextContent('请选择一个投诉案件');
  });

  it('loads one case timeline and submits a target-bound accept draft only once', async () => {
    let resolveAccept!: (row: ComplaintCaseRow) => void;
    vi.mocked(api.acceptComplaintCase).mockReturnValue(new Promise((resolve) => { resolveAccept = resolve; }));
    renderWithQuery(<AdminComplaintsPage />);

    expect(await openCase()).toHaveTextContent('#1');
    expect(api.getComplaintCaseDetail).toHaveBeenCalledWith(1);
    expect(screen.getByTestId('admin-complaint-case-complaints-timeline')).toHaveTextContent('REGISTERED');
    expect(screen.getByTestId('admin-complaint-case-complaints-accept')).toBeEnabled();
    expect(screen.getByTestId('admin-complaint-case-complaints-handle')).toBeDisabled();

    fireEvent.click(screen.getByTestId('admin-complaint-case-complaints-accept'));
    expect(screen.getByTestId('admin-complaint-case-complaints-action-target')).toHaveTextContent('#1');
    expect(screen.getByTestId('admin-complaint-case-complaints-action-target')).toHaveTextContent('机构 7');
    expect(screen.getByTestId('admin-complaint-case-complaints-action-current-status')).toHaveTextContent('PENDING');
    expect(screen.getByTestId('admin-complaint-case-complaints-opinion')).toHaveValue('');
    fireEvent.change(screen.getByTestId('admin-complaint-case-complaints-opinion'), { target: { value: '投诉属实，接单核查' } });
    fireEvent.click(screen.getByTestId('admin-complaint-case-complaints-action-confirm'));
    fireEvent.click(screen.getByTestId('admin-complaint-case-complaints-action-confirm'));
    await waitFor(() => expect(api.acceptComplaintCase).toHaveBeenCalledTimes(1));
    expect(screen.getByTestId('admin-complaint-case-complaints-action-cancel')).toBeDisabled();
    expect(screen.getByTestId('admin-complaint-case-complaints-action-close')).toBeDisabled();
    await waitFor(() => expect(screen.getByTestId('modal')).toHaveFocus());

    resolveAccept({ ...caseRow, status: 'PROCESSING' });
    await waitFor(() => expect(screen.queryByTestId('admin-complaint-case-complaints-action-dialog')).not.toBeInTheDocument());
  });

  it('discards a cancelled action draft and recovers only the latest failed record', async () => {
    const processed = { ...caseRow, status: 'PROCESSED' as const };
    vi.mocked(api.getComplaintCaseDetail).mockResolvedValue({
      complaint: processed,
      timeline: detail.timeline,
      remediations: [
        { id: 5, complaintId: 1, disposalType: 'SUSPEND_CHANNEL', targetRef: 'channel:11', status: 'FAILED', authorizedReviewId: 'review-failed', failureReason: '处置执行失败，请根据安全审计日志排查', originalComplaintId: 1 },
        { id: 4, complaintId: 1, disposalType: 'SUSPEND_CHANNEL', targetRef: 'channel:11', status: 'FAILED', authorizedReviewId: 'review-old', failureReason: '处置执行失败，请根据安全审计日志排查', originalComplaintId: 1 },
      ],
    });
    renderWithQuery(<AdminComplaintsPage />);
    await openCase();

    expect(screen.getByTestId('admin-complaint-case-complaints-remediation')).toBeEnabled();
    fireEvent.click(screen.getByTestId('admin-complaint-case-complaints-close'));
    fireEvent.change(screen.getByTestId('admin-complaint-case-complaints-close-note'), { target: { value: '不应保留' } });
    fireEvent.click(screen.getByTestId('admin-complaint-case-complaints-action-cancel'));
    fireEvent.click(screen.getByTestId('admin-complaint-case-complaints-close'));
    expect(screen.getByTestId('admin-complaint-case-complaints-close-note')).toHaveValue('');
    fireEvent.click(screen.getByTestId('admin-complaint-case-complaints-action-cancel'));

    fireEvent.click(screen.getByTestId('admin-complaint-case-complaints-remediation-recovery'));
    expect(screen.getByTestId('admin-complaint-case-complaints-recovery-record')).toHaveTextContent('#5');
    fireEvent.change(screen.getByTestId('admin-complaint-case-complaints-recovery-review-id'), { target: { value: 'review-124-recovery' } });
    fireEvent.change(screen.getByTestId('admin-complaint-case-complaints-recovery-condition'), { target: { value: '复核通过' } });
    fireEvent.click(screen.getByTestId('admin-complaint-case-complaints-action-confirm'));
    await waitFor(() => expect(api.recoverComplaintRemediation).toHaveBeenCalledWith(1, expect.objectContaining({ disposalRecordId: 5 })));
  });

  it('unlocks cancel and confirm after a generic mutation failure', async () => {
    vi.mocked(api.acceptComplaintCase)
      .mockRejectedValueOnce(new Error('temporary failure'))
      .mockResolvedValueOnce({ ...caseRow, status: 'PROCESSING' });
    renderWithQuery(<AdminComplaintsPage />);
    await openCase();
    fireEvent.click(screen.getByTestId('admin-complaint-case-complaints-accept'));
    fireEvent.change(screen.getByTestId('admin-complaint-case-complaints-opinion'), { target: { value: '接单核查' } });
    fireEvent.click(screen.getByTestId('admin-complaint-case-complaints-action-confirm'));

    expect(await screen.findByTestId('admin-complaint-case-complaints-action-error')).toHaveTextContent('操作失败');
    expect(screen.getByTestId('admin-complaint-case-complaints-action-cancel')).toBeEnabled();
    expect(screen.getByTestId('admin-complaint-case-complaints-action-confirm')).toBeEnabled();
    fireEvent.click(screen.getByTestId('admin-complaint-case-complaints-action-confirm'));
    await waitFor(() => expect(api.acceptComplaintCase).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(screen.queryByTestId('admin-complaint-case-complaints-action-dialog')).not.toBeInTheDocument());
  });

  it('refreshes stale state and retries list, choices and detail independently', async () => {
    vi.mocked(api.listComplaintCases).mockRejectedValueOnce(new Error('list unavailable')).mockResolvedValueOnce([caseRow]);
    vi.mocked(api.getComplaintReferenceOptions).mockRejectedValueOnce(new Error('options unavailable')).mockResolvedValueOnce(referenceOptions);
    vi.mocked(api.getComplaintCaseDetail)
      .mockRejectedValueOnce(new Error('detail unavailable'))
      .mockResolvedValueOnce(detail)
      .mockResolvedValue({ ...detail, complaint: { ...detail.complaint, status: 'PROCESSING' } });
    vi.mocked(api.acceptComplaintCase).mockRejectedValue({ isAxiosError: true, response: { status: 409, data: { data: { errorCode: 'COMPLAINT_STATE_STALE' } } } });
    renderWithQuery(<AdminComplaintsPage />);

    fireEvent.click(await screen.findByTestId('admin-complaint-case-complaints-list-retry'));
    fireEvent.click(await screen.findByTestId('admin-complaint-case-complaints-reference-options-retry'));
    expect(await screen.findByRole('option', { name: /机构七/ })).toBeVisible();
    fireEvent.click(screen.getByTestId('admin-complaint-case-complaints-open-case'));
    fireEvent.click(await screen.findByTestId('admin-complaint-case-complaints-case-workspace-retry'));
    expect(await screen.findByTestId('admin-complaint-case-complaints-case-id')).toHaveTextContent('#1');
    fireEvent.click(screen.getByTestId('admin-complaint-case-complaints-accept'));
    fireEvent.change(screen.getByTestId('admin-complaint-case-complaints-opinion'), { target: { value: '接单核查' } });
    fireEvent.click(screen.getByTestId('admin-complaint-case-complaints-action-confirm'));
    expect(await screen.findByTestId('admin-complaint-case-complaints-action-stale')).toHaveTextContent('案件状态已变化');
    await waitFor(() => expect(api.getComplaintCaseDetail).toHaveBeenCalledTimes(3));
    expect(screen.getByTestId('admin-complaint-case-complaints-action-current-status')).toHaveTextContent('PROCESSING');
    expect(screen.getByTestId('admin-complaint-case-complaints-action-confirm')).toBeDisabled();
    fireEvent.click(screen.getByTestId('admin-complaint-case-complaints-action-cancel'));
    expect(screen.queryByTestId('admin-complaint-case-complaints-action-dialog')).not.toBeInTheDocument();
  });

  it('keeps FINANCE read-only while preserving case context and timeline', async () => {
    useAuthStore.setState({ userType: 'FINANCE' });
    renderWithQuery(<AdminComplaintsPage />);

    expect(screen.queryByTestId('entity-form')).not.toBeInTheDocument();
    expect(api.getComplaintReferenceOptions).not.toHaveBeenCalled();
    await openCase();
    expect(screen.getByTestId('admin-complaint-case-complaints-timeline')).toHaveTextContent('REGISTERED');
    expect(screen.queryByTestId('admin-complaint-case-complaints-accept')).not.toBeInTheDocument();
  });

  it('renders analytics trend and distribution with unknown attribution quality', async () => {
    renderWithQuery(<AdminComplaintAnalyticsPage />);
    expect(await screen.findByTestId('admin-complaint-case-analytics-page')).toHaveTextContent('投诉趋势与分布');
    await waitFor(() => expect(screen.getByTestId('admin-complaint-case-analytics-quality')).toHaveTextContent('未知归因 1'));
    expect(screen.getByTestId('admin-complaint-case-analytics-trend')).toHaveTextContent('2026-09-12：2');
  });

  it('does not present zero analytics while the request is loading', () => {
    vi.mocked(api.getComplaintAnalytics).mockReturnValue(new Promise<never>(() => {}));
    renderWithQuery(<AdminComplaintAnalyticsPage />);
    expect(screen.getByTestId('admin-complaint-case-analytics-loading')).toHaveTextContent('正在加载投诉分析');
    expect(screen.queryByTestId('admin-complaint-case-analytics-quality')).not.toBeInTheDocument();
  });

  it('shows only the analytics error state when the request fails', async () => {
    vi.mocked(api.getComplaintAnalytics).mockRejectedValue(new Error('analytics unavailable'));
    renderWithQuery(<AdminComplaintAnalyticsPage />);
    expect(await screen.findByTestId('admin-complaint-case-analytics-error')).toHaveTextContent('投诉分析加载失败');
    expect(screen.queryByTestId('admin-complaint-case-analytics-quality')).not.toBeInTheDocument();
  });
});
