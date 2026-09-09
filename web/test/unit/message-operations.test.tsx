import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
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

describe('Phase 27 message receipt error operations UI', () => {
  beforeEach(() => {
    vi.mocked(api.listSubmissions).mockResolvedValue([
      { submissionId: 101, tenantId: 42, submitId: 'SUBMIT-1', messageId: 'MSG_FAILED', sourceProtocol: 'HTTP', productType: 'NOTIFY', submissionStatus: 'ACCEPTED', sendStatus: 'FAILED', templateId: 11, signatureId: 12, errorCode: 'E42', errorMessage: '供应商拒绝', createdAt: '2026-09-09T00:00:00' },
    ]);
    vi.mocked(api.listSends).mockResolvedValue([
      { taskId: 201, messageId: 'MSG_FAILED', tenantId: 42, submissionId: 101, maskedMobile: '已保护', contentSummary: '【签名】验证码...', sendStatus: 'FAILED', channelId: 7, providerMessageId: 'UP-1', carrier: 'MOBILE', province: '广东', city: '深圳', errorCode: 'E42', errorMessage: '供应商拒绝', cost: 0.05, retryCount: 0, outboxState: 'FAILED', sentAt: null, deliveredAt: null, createdAt: '2026-09-09T00:00:00', version: 3 },
    ]);
    vi.mocked(api.listReceipts).mockResolvedValue([
      { receiptId: 501, messageId: 'MSG_FAILED', tenantId: 42, maskedMobile: '已保护', channelId: 7, providerMessageId: 'UP-1', receiptStatus: 'FAILED', sendStatus: 'FAILED', errorCode: 'E42', rawPayloadSummary: 'raw payload protected', receiptDigest: 'R-1', carrier: 'MOBILE', province: '广东', city: '深圳', reportTime: '2026-09-09T00:00:00' },
    ]);
    vi.mocked(api.listErrorGroups).mockResolvedValue([
      { normalizedCode: 'E42', platformCategory: 'FAILURE', severity: 'ERROR', retryable: true, totalCount: 1, tenantCount: 1, channelCount: 1, firstSeenAt: '2026-09-09T00:00:00', lastSeenAt: '2026-09-09T00:00:00' },
    ]);
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
    fireEvent.click(screen.getByTestId('admin-message-receipt-export-request'));
    await waitFor(() => expect(api.requestMessageExport).toHaveBeenCalled());
    expect(await screen.findByTestId('admin-message-receipt-operation-message')).toHaveTextContent('导出请求已登记');
  });

  it('resends and appeals eligible failed messages without exposing plaintext mobile', async () => {
    renderPage('sends');

    expect(await screen.findByTestId('admin-message-receipt-send-details-page')).toBeVisible();
    expect(await screen.findByTestId('admin-message-receipt-send-details-row')).toHaveTextContent('已保护');
    fireEvent.click(screen.getByTestId('admin-message-receipt-send-details-resend'));
    await waitFor(() => expect(api.resendMessage).toHaveBeenCalledWith('MSG_FAILED', expect.stringMatching(/^RESEND-/), '运营复核确认'));
    fireEvent.click(screen.getByTestId('admin-message-receipt-send-details-appeal'));
    await waitFor(() => expect(api.appealMessage).toHaveBeenCalled());
  });

  it('corrects and replays receipts with explicit reason', async () => {
    renderPage('receipts');

    expect(await screen.findByTestId('admin-message-receipt-receipt-details-page')).toBeVisible();
    fireEvent.click(await screen.findByTestId('admin-message-receipt-receipt-correct'));
    await waitFor(() => expect(api.correctReceipt).toHaveBeenCalledWith(501, expect.stringMatching(/^CORRECT-/), '运营复核确认', 'DELIVERED', '', 'ST20260909'));
    fireEvent.click(screen.getByTestId('admin-message-receipt-receipt-replay'));
    await waitFor(() => expect(api.replayReceipt).toHaveBeenCalled());
  });

  it('runs bulk retry from the error distribution section', async () => {
    renderPage('errors');

    expect(await screen.findByTestId('admin-message-receipt-error-details-page')).toBeVisible();
    expect(await screen.findByTestId('admin-message-receipt-error-details-row')).toHaveTextContent('E42');
    fireEvent.click(screen.getByTestId('admin-message-receipt-error-details-bulk-retry'));
    await waitFor(() => expect(api.bulkErrorAction).toHaveBeenCalledWith(expect.stringMatching(/^BULK-/), 'BULK_RETRY', 'E42', ['MSG_FAILED'], '运营复核确认'));
  });
});
