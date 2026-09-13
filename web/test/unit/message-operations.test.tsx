import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '@/api/messageOperationsApi';
import MessageOperationsPage from '@/pages/admin/records/MessageOperationsPage';

vi.mock('@/api/messageOperationsApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/messageOperationsApi')>();
  return {
    ...actual,
    listSubmissions: vi.fn(),
    listSends: vi.fn(),
    listReceipts: vi.fn(),
    listErrorGroups: vi.fn(),
    resendMessage: vi.fn(),
    appealMessage: vi.fn(),
    correctReceipt: vi.fn(),
    replayReceipt: vi.fn(),
    bulkErrorAction: vi.fn(),
    requestMessageExport: vi.fn(),
  };
});

function renderPage(initialSection: 'submissions' | 'sends' | 'receipts' | 'errors' = 'submissions') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MessageOperationsPage initialSection={initialSection} />
    </QueryClientProvider>,
  );
}

function sendRow(overrides: Partial<api.SendRow> = {}): api.SendRow {
  return {
    taskId: 201,
    messageId: 'MSG_FAILED',
    tenantId: 42,
    submissionId: 101,
    maskedMobile: '已保护',
    contentSummary: '【签名】验证码...',
    sendStatus: 'FAILED',
    channelId: 7,
    providerMessageId: 'UP-1',
    carrier: 'MOBILE',
    province: '广东',
    city: '深圳',
    errorCode: 'E42',
    errorMessage: '供应商拒绝',
    cost: 0.05,
    retryCount: 0,
    outboxState: 'FAILED',
    sentAt: null,
    deliveredAt: null,
    createdAt: '2026-09-09T00:00:00',
    version: 3,
    ...overrides,
  };
}

function errorGroup(overrides: Partial<api.ErrorGroupRow> = {}): api.ErrorGroupRow {
  return {
    normalizedCode: 'E42',
    platformCategory: 'FAILURE',
    severity: 'ERROR',
    retryable: true,
    totalCount: 1,
    tenantCount: 1,
    channelCount: 1,
    firstSeenAt: '2026-09-09T00:00:00',
    lastSeenAt: '2026-09-09T00:00:00',
    ...overrides,
  };
}

describe('Phase 27 message receipt error operations UI', () => {
  beforeEach(() => {
    vi.mocked(api.listSubmissions).mockResolvedValue([
      { submissionId: 101, tenantId: 42, submitId: 'SUBMIT-1', messageId: 'MSG_FAILED', sourceProtocol: 'HTTP', productType: 'NOTIFY', submissionStatus: 'ACCEPTED', sendStatus: 'FAILED', templateId: 11, signatureId: 12, errorCode: 'E42', errorMessage: '供应商拒绝', createdAt: '2026-09-09T00:00:00' },
    ]);
    vi.mocked(api.listSends).mockResolvedValue([sendRow()]);
    vi.mocked(api.listReceipts).mockResolvedValue([
      { receiptId: 501, messageId: 'MSG_FAILED', tenantId: 42, maskedMobile: '已保护', channelId: 7, providerMessageId: 'UP-1', receiptStatus: 'FAILED', sendStatus: 'FAILED', errorCode: 'E42', rawPayloadSummary: 'raw payload protected', receiptDigest: 'R-1', carrier: 'MOBILE', province: '广东', city: '深圳', reportTime: '2026-09-09T00:00:00' },
    ]);
    vi.mocked(api.listErrorGroups).mockResolvedValue([errorGroup()]);
    vi.mocked(api.resendMessage).mockResolvedValue({ actionId: 'RESEND-1', action: 'RESEND', target: 'MSG_FAILED', status: 'COMPLETED', resultCode: 'RETRY_CREATED', resultMessage: '301' });
    vi.mocked(api.appealMessage).mockResolvedValue({ actionId: 'APPEAL-1', action: 'APPEAL', target: 'MSG_FAILED', status: 'COMPLETED', resultCode: 'APPEAL_RECORDED', resultMessage: null });
    vi.mocked(api.correctReceipt).mockResolvedValue({ actionId: 'CORRECT-1', action: 'RECEIPT_CORRECT', target: 'MSG_FAILED', status: 'COMPLETED', resultCode: 'RECEIPT_CORRECTED', resultMessage: null });
    vi.mocked(api.replayReceipt).mockResolvedValue({ actionId: 'REPLAY-1', action: 'RECEIPT_REPLAY', target: 'MSG_FAILED', status: 'COMPLETED', resultCode: 'RECEIPT_REPLAYED', resultMessage: 'FAILED' });
    vi.mocked(api.bulkErrorAction).mockResolvedValue({ actionId: 'BULK-1', action: 'BULK_RETRY', total: 1, completed: 1, failed: 0, results: [] });
    vi.mocked(api.requestMessageExport).mockResolvedValue({ actionId: 'EXPORT-1', action: 'EXPORT_REQUEST', target: 'snapshot', status: 'COMPLETED', resultCode: 'EXPORT_REQUESTED', resultMessage: '导出请求已登记，匹配行数:3' });
  });

  afterEach(() => {
    vi.clearAllMocks();
    act(() => undefined);
  });

  it('shows submission trace, masked send rows, and export handoff', async () => {
    renderPage('submissions');

    expect(await screen.findByTestId('admin-message-receipt-submission-details-page')).toBeVisible();
    expect(await screen.findByTestId('admin-message-receipt-submission-details-trace')).toHaveTextContent('SUBMIT-1');
    expect(screen.queryByTestId('admin-message-receipt-action-reason')).not.toBeInTheDocument();
    fireEvent.change(screen.getByTestId('admin-message-receipt-filter-error-code'), { target: { value: 'E42' } });
    fireEvent.click(screen.getByTestId('query-submit'));
    fireEvent.click(screen.getByTestId('admin-message-receipt-export-request'));
    expect(screen.getByTestId('admin-message-receipt-action-target')).toHaveTextContent('消息运营导出（发送、回执与提交记录）');
    expect(screen.getByTestId('admin-message-receipt-action-target')).toHaveTextContent('机构 42');
    expect(screen.getByTestId('admin-message-receipt-action-target')).not.toHaveTextContent('错误码 E42');
    expect(screen.getByTestId('admin-message-receipt-action-consequence')).toHaveTextContent('错误码筛选 E42 不受该导出接口支持，不会应用于导出');
    fireEvent.change(screen.getByTestId('admin-message-receipt-action-reason'), { target: { value: '导出用于问题排查' } });
    fireEvent.click(screen.getByTestId('admin-message-receipt-action-confirm'));
    await waitFor(() => expect(api.requestMessageExport).toHaveBeenCalledWith({ tenantId: '42', messageId: '', status: '' }, expect.stringMatching(/^EXPORT-/), '导出用于问题排查', 'MESSAGE_OPERATIONS'));
    expect(await screen.findByTestId('admin-message-receipt-operation-message')).toHaveTextContent('导出请求已登记');
  });

  it.each([
    { section: 'submissions' as const, triggerId: 'admin-message-receipt-export-request', dataset: '消息运营导出（发送、回执与提交记录）', exportType: 'MESSAGE_OPERATIONS', excludesGroups: false },
    { section: 'sends' as const, triggerId: 'admin-secure-async-send-details-export', dataset: '发送详单导出', exportType: 'SEND_DETAIL', excludesGroups: false },
    { section: 'receipts' as const, triggerId: 'admin-secure-async-receipt-export', dataset: '回执详单导出', exportType: 'RECEIPT_DETAIL', excludesGroups: false },
    { section: 'errors' as const, triggerId: 'admin-message-receipt-export-request', dataset: '消息运营导出（发送、回执与提交记录）', exportType: 'MESSAGE_OPERATIONS', excludesGroups: true },
  ])('describes the real $section export dataset and excludes unsupported error-code filtering', async ({ section, triggerId, dataset, exportType, excludesGroups }) => {
    renderPage(section);
    expect(await screen.findByTestId(`admin-message-receipt-${section === 'submissions' ? 'submission' : section.slice(0, -1)}-details-page`)).toBeVisible();
    fireEvent.change(screen.getByTestId('admin-message-receipt-filter-error-code'), { target: { value: 'E99' } });
    fireEvent.click(screen.getByTestId('query-submit'));
    fireEvent.click(screen.getByTestId(triggerId));

    expect(screen.getByTestId('admin-message-receipt-action-target')).toHaveTextContent(dataset);
    expect(screen.getByTestId('admin-message-receipt-action-target')).not.toHaveTextContent('错误码 E99');
    expect(screen.getByTestId('admin-message-receipt-action-consequence')).toHaveTextContent('错误码筛选 E99 不受该导出接口支持，不会应用于导出');
    if (excludesGroups) expect(screen.getByTestId('admin-message-receipt-action-consequence')).toHaveTextContent('不包含错误聚合行');
    fireEvent.change(screen.getByTestId('admin-message-receipt-action-reason'), { target: { value: '核对实际导出范围' } });
    fireEvent.click(screen.getByTestId('admin-message-receipt-action-confirm'));
    await waitFor(() => expect(api.requestMessageExport).toHaveBeenCalledWith(
      { tenantId: '42', messageId: '', status: '' },
      expect.stringMatching(/^EXPORT-/),
      '核对实际导出范围',
      exportType,
    ));
  });

  it('resends and appeals eligible failed messages without exposing plaintext mobile', async () => {
    renderPage('sends');

    expect(await screen.findByTestId('admin-message-receipt-send-details-page')).toBeVisible();
    expect(await screen.findByTestId('admin-message-receipt-send-details-row')).toHaveTextContent('已保护');
    fireEvent.click(screen.getByTestId('admin-message-receipt-send-details-resend'));
    fireEvent.change(screen.getByTestId('admin-message-receipt-action-reason'), { target: { value: '供应商失败重试' } });
    fireEvent.click(screen.getByTestId('admin-message-receipt-action-confirm'));
    await waitFor(() => expect(api.resendMessage).toHaveBeenCalledWith('MSG_FAILED', expect.stringMatching(/^RESEND-/), '供应商失败重试'));
    fireEvent.click(screen.getByTestId('admin-message-receipt-send-details-appeal'));
    fireEvent.change(screen.getByTestId('admin-message-receipt-action-reason'), { target: { value: '供应商拒绝申诉' } });
    fireEvent.click(screen.getByTestId('admin-message-receipt-action-confirm'));
    await waitFor(() => expect(api.appealMessage).toHaveBeenCalledWith('MSG_FAILED', expect.stringMatching(/^APPEAL-/), '供应商拒绝申诉'));
  });

  it('corrects and replays receipts with explicit reason', async () => {
    renderPage('receipts');

    expect(await screen.findByTestId('admin-message-receipt-receipt-details-page')).toBeVisible();
    fireEvent.click(await screen.findByTestId('admin-message-receipt-receipt-correct'));
    fireEvent.change(screen.getByTestId('admin-message-receipt-action-reason'), { target: { value: '运营商送达凭证确认' } });
    fireEvent.click(screen.getByTestId('admin-message-receipt-action-confirm'));
    await waitFor(() => expect(api.correctReceipt).toHaveBeenCalledWith(501, expect.stringMatching(/^CORRECT-/), '运营商送达凭证确认', 'DELIVERED', '', 'ST20260909'));
    fireEvent.click(screen.getByTestId('admin-message-receipt-receipt-replay'));
    fireEvent.change(screen.getByTestId('admin-message-receipt-action-reason'), { target: { value: '重新处理原始回执' } });
    fireEvent.click(screen.getByTestId('admin-message-receipt-action-confirm'));
    await waitFor(() => expect(api.replayReceipt).toHaveBeenCalledWith(501, expect.stringMatching(/^REPLAY-/), '重新处理原始回执'));
  });

  it('runs bulk retry from the error distribution section', async () => {
    renderPage('errors');

    expect(await screen.findByTestId('admin-message-receipt-error-details-page')).toBeVisible();
    expect(await screen.findByTestId('admin-message-receipt-error-details-row')).toHaveTextContent('E42');
    fireEvent.click(screen.getByTestId('admin-message-receipt-error-details-bulk-retry'));
    expect(screen.getByTestId('admin-message-receipt-action-target')).toHaveTextContent('错误码 E42');
    fireEvent.change(screen.getByTestId('admin-message-receipt-action-reason'), { target: { value: '错误组已具备重试条件' } });
    fireEvent.click(screen.getByTestId('admin-message-receipt-action-confirm'));
    await waitFor(() => expect(api.bulkErrorAction).toHaveBeenCalledWith(expect.stringMatching(/^BULK-/), 'BULK_RETRY', 'E42', ['MSG_FAILED'], '错误组已具备重试条件'));
  });

  it('binds each bulk action to the selected error group and its matching failed messages', async () => {
    vi.mocked(api.listSends).mockResolvedValue([
      sendRow(),
      sendRow({ taskId: 202, messageId: 'MSG_OTHER', errorCode: 'E99' }),
      sendRow({ taskId: 203, messageId: 'MSG_SENT', errorCode: 'E42', sendStatus: 'SENT' }),
    ]);
    vi.mocked(api.listErrorGroups).mockResolvedValue([
      errorGroup({ normalizedCode: 'E99' }),
      errorGroup(),
    ]);
    renderPage('errors');

    const rows = await screen.findAllByTestId('admin-message-receipt-error-details-row');
    const e42Row = rows.find((row) => within(row).queryByText('E42'));
    const e99Row = rows.find((row) => within(row).queryByText('E99'));
    expect(e42Row).toBeDefined();
    expect(e99Row).toBeDefined();

    fireEvent.click(within(e42Row!).getByTestId('admin-message-receipt-error-details-bulk-retry'));
    expect(screen.getByTestId('admin-message-receipt-action-target')).toHaveTextContent('错误码 E42 · 1 条失败消息');
    fireEvent.change(screen.getByTestId('admin-message-receipt-action-reason'), { target: { value: '仅重试 E42' } });
    fireEvent.click(screen.getByTestId('admin-message-receipt-action-confirm'));
    await waitFor(() => expect(api.bulkErrorAction).toHaveBeenCalledWith(expect.stringMatching(/^BULK-/), 'BULK_RETRY', 'E42', ['MSG_FAILED'], '仅重试 E42'));

    fireEvent.click(within(e99Row!).getByTestId('admin-message-receipt-error-details-mark-problem'));
    expect(screen.getByTestId('admin-message-receipt-action-target')).toHaveTextContent('错误码 E99 · 1 条失败消息');
    fireEvent.change(screen.getByTestId('admin-message-receipt-action-reason'), { target: { value: '仅标记 E99' } });
    fireEvent.click(screen.getByTestId('admin-message-receipt-action-confirm'));
    await waitFor(() => expect(api.bulkErrorAction).toHaveBeenCalledWith(expect.stringMatching(/^PROBLEM-/), 'MARK_PROBLEM', 'E99', ['MSG_OTHER'], '仅标记 E99'));
  });

  it('keeps bulk controls disabled while targets load and when no failed message matches', async () => {
    let resolveSends!: (rows: api.SendRow[]) => void;
    vi.mocked(api.listSends).mockReturnValue(new Promise((resolve) => { resolveSends = resolve; }));
    vi.mocked(api.listErrorGroups).mockResolvedValue([errorGroup()]);
    renderPage('errors');

    const bulkRetry = await screen.findByTestId('admin-message-receipt-error-details-bulk-retry');
    expect(bulkRetry).toBeDisabled();
    fireEvent.click(bulkRetry);
    expect(screen.queryByTestId('admin-message-receipt-action-dialog')).not.toBeInTheDocument();

    await act(async () => resolveSends([]));
    await waitFor(() => expect(bulkRetry).toBeDisabled());
    fireEvent.click(bulkRetry);
    expect(screen.queryByTestId('admin-message-receipt-action-dialog')).not.toBeInTheDocument();
    expect(api.bulkErrorAction).not.toHaveBeenCalled();
  });

  it('keeps both bulk controls disabled when target loading fails', async () => {
    vi.mocked(api.listSends).mockRejectedValue(new Error('send target lookup failed'));
    vi.mocked(api.listErrorGroups).mockResolvedValue([errorGroup()]);
    renderPage('errors');

    const row = await screen.findByTestId('admin-message-receipt-error-details-row');
    const bulkRetry = within(row).getByTestId('admin-message-receipt-error-details-bulk-retry');
    const markProblem = within(row).getByTestId('admin-message-receipt-error-details-mark-problem');
    await waitFor(() => {
      expect(bulkRetry).toBeDisabled();
      expect(markProblem).toBeDisabled();
    });

    fireEvent.click(bulkRetry);
    fireEvent.click(markProblem);
    expect(screen.queryByTestId('admin-message-receipt-action-dialog')).not.toBeInTheDocument();
    expect(api.bulkErrorAction).not.toHaveBeenCalled();
  });

  it('does not partially submit an error group above the backend bulk limit', async () => {
    vi.mocked(api.listSends).mockResolvedValue(Array.from({ length: 51 }, (_, index) => sendRow({
      taskId: 300 + index,
      messageId: `MSG_E42_${index}`,
    })));
    vi.mocked(api.listErrorGroups).mockResolvedValue([errorGroup({ totalCount: 51 })]);
    renderPage('errors');

    const row = await screen.findByTestId('admin-message-receipt-error-details-row');
    const bulkRetry = within(row).getByTestId('admin-message-receipt-error-details-bulk-retry');
    const markProblem = within(row).getByTestId('admin-message-receipt-error-details-mark-problem');
    await waitFor(() => expect(bulkRetry).toBeDisabled());
    expect(markProblem).toBeDisabled();
    expect(bulkRetry).toHaveAttribute('title', '错误组共 51 条，超过单次 50 条限制');

    fireEvent.click(bulkRetry);
    fireEvent.click(markProblem);
    expect(screen.queryByTestId('admin-message-receipt-action-dialog')).not.toBeInTheDocument();
    expect(api.bulkErrorAction).not.toHaveBeenCalled();
  });

  it('does not submit a truncated subset when the aggregate group is larger than its loaded targets', async () => {
    vi.mocked(api.listSends).mockResolvedValue([
      ...Array.from({ length: 151 }, (_, index) => sendRow({
        taskId: 400 + index,
        messageId: `MSG_E99_${index}`,
        errorCode: 'E99',
      })),
      ...Array.from({ length: 49 }, (_, index) => sendRow({
        taskId: 600 + index,
        messageId: `MSG_E42_${index}`,
      })),
    ]);
    vi.mocked(api.listErrorGroups).mockResolvedValue([errorGroup({ totalCount: 50 })]);
    renderPage('errors');

    const row = await screen.findByTestId('admin-message-receipt-error-details-row');
    const bulkRetry = within(row).getByTestId('admin-message-receipt-error-details-bulk-retry');
    const markProblem = within(row).getByTestId('admin-message-receipt-error-details-mark-problem');
    await waitFor(() => expect(bulkRetry).toBeDisabled());
    expect(markProblem).toBeDisabled();
    expect(bulkRetry).toHaveAttribute('title', '目标未完整加载：已加载 49 条，共 50 条');

    fireEvent.click(bulkRetry);
    fireEvent.click(markProblem);
    expect(screen.queryByTestId('admin-message-receipt-action-dialog')).not.toBeInTheDocument();
    expect(api.bulkErrorAction).not.toHaveBeenCalled();
  });

  it('does not pass the display-only UNKNOWN group to the bulk API', async () => {
    vi.mocked(api.listSends).mockResolvedValue([sendRow({ errorCode: null })]);
    vi.mocked(api.listErrorGroups).mockResolvedValue([errorGroup({ normalizedCode: 'UNKNOWN' })]);
    renderPage('errors');

    const row = await screen.findByTestId('admin-message-receipt-error-details-row');
    const bulkRetry = within(row).getByTestId('admin-message-receipt-error-details-bulk-retry');
    const markProblem = within(row).getByTestId('admin-message-receipt-error-details-mark-problem');
    await waitFor(() => expect(bulkRetry).toBeDisabled());
    expect(markProblem).toBeDisabled();
    expect(bulkRetry).toHaveAttribute('title', '错误码为空的 UNKNOWN 分组不支持批量操作');

    fireEvent.click(bulkRetry);
    fireEvent.click(markProblem);
    expect(screen.queryByTestId('admin-message-receipt-action-dialog')).not.toBeInTheDocument();
    expect(api.bulkErrorAction).not.toHaveBeenCalled();
  });
});
