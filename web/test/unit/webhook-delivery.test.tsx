import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type React from 'react';
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import * as api from '@/api/webhookDeliveryApi';
import TenantWebhooksPage from '@/pages/tenant/webhooks/TenantWebhooksPage';
import AdminPushFailuresPage from '@/pages/admin/webhooks/AdminPushFailuresPage';

vi.mock('@/api/webhookDeliveryApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/webhookDeliveryApi')>();
  return {
    ...actual,
    getTenantWebhookConfig: vi.fn(),
    saveTenantWebhookConfig: vi.fn(),
    testTenantWebhook: vi.fn(),
    listWebhookFailures: vi.fn(),
    replayWebhookFailure: vi.fn(),
    pauseWebhookFailure: vi.fn(),
    resumeWebhookFailure: vi.fn(),
  };
});

function renderWithQuery(ui: React.ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

describe('Phase 28 webhook delivery UI', () => {
  beforeEach(() => {
    vi.mocked(api.getTenantWebhookConfig).mockResolvedValue({
      tenantId: 7,
      statusCallbackUrl: 'https://callback.example.com/status',
      uplinkCallbackUrl: 'https://callback.example.com/uplink',
      unsubscribeCallbackUrl: 'https://callback.example.com/unsubscribe',
      retryMaxCount: 5,
      retryBackoffSeconds: 30,
      status: 'ACTIVE',
      latestFailureReason: null,
      pausedAt: null,
      version: 1,
    });
    vi.mocked(api.saveTenantWebhookConfig).mockResolvedValue({
      tenantId: 7,
      statusCallbackUrl: 'https://callback.example.com/status2',
      uplinkCallbackUrl: 'https://callback.example.com/uplink',
      unsubscribeCallbackUrl: 'https://callback.example.com/unsubscribe',
      retryMaxCount: 5,
      retryBackoffSeconds: 30,
      status: 'ACTIVE',
      latestFailureReason: null,
      pausedAt: null,
      version: 2,
    });
    vi.mocked(api.testTenantWebhook).mockResolvedValue({
      eventId: 101,
      destinationUrl: 'https://callback.example.com/status',
      state: 'DELIVERED',
      resultCode: 'HTTP_204',
      resultMessage: null,
    });
    vi.mocked(api.listWebhookFailures).mockResolvedValue([
      { eventId: 501, tenantId: 7, eventType: 'STATUS', sourceId: 'STATUS:MSG_1:FAILED', logicalId: 'STATUS:MSG_1:FAILED', destinationUrl: 'https://callback.example.com/status', state: 'PUSH_FAILED', attemptCount: 5, maxAttempts: 5, nextAttemptAt: null, updatedAt: '2026-09-09T00:00:00' },
    ]);
    vi.mocked(api.replayWebhookFailure).mockResolvedValue({ eventId: 501, tenantId: 7, state: 'DELIVERED', resultCode: 'HTTP_200', resultMessage: 'ok' });
    vi.mocked(api.pauseWebhookFailure).mockResolvedValue({ eventId: 501, tenantId: 7, state: 'PAUSED', resultCode: 'PAUSED', resultMessage: 'operator' });
    vi.mocked(api.resumeWebhookFailure).mockResolvedValue({ eventId: 501, tenantId: 7, state: 'RETRY', resultCode: 'RESUMED', resultMessage: 'operator' });
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('configures separate tenant webhook destinations and runs an SSRF-safe test', async () => {
    renderWithQuery(<TenantWebhooksPage />);

    expect(await screen.findByTestId('tenant-webhook-delivery-config-form')).toBeVisible();
    const statusInput = await screen.findByTestId('tenant-webhook-delivery-status-url');
    await waitFor(() => expect(statusInput).toHaveValue('https://callback.example.com/status'));
    fireEvent.change(statusInput, { target: { value: 'https://callback.example.com/status2' } });
    await waitFor(() => expect(statusInput).toHaveValue('https://callback.example.com/status2'));
    fireEvent.click(screen.getByTestId('tenant-webhook-delivery-save'));
    await waitFor(() => expect(api.saveTenantWebhookConfig).toHaveBeenCalledWith(expect.objectContaining({
      statusCallbackUrl: 'https://callback.example.com/status2',
      uplinkCallbackUrl: 'https://callback.example.com/uplink',
      unsubscribeCallbackUrl: 'https://callback.example.com/unsubscribe',
      retryMaxCount: 5,
      retryBackoffSeconds: 30,
    })));
    fireEvent.click(screen.getByTestId('tenant-webhook-delivery-test'));
    await waitFor(() => expect(api.testTenantWebhook).toHaveBeenCalledWith('STATUS', 'https://callback.example.com/status2'));
  });

  it('shows failed pushes and runs replay pause resume policy actions', async () => {
    renderWithQuery(<AdminPushFailuresPage />);

    expect(await screen.findByTestId('admin-webhook-delivery-push-failures-page')).toBeVisible();
    expect(await screen.findByTestId('admin-webhook-delivery-push-failures-row')).toHaveTextContent('STATUS:MSG_1:FAILED');
    fireEvent.click(screen.getByTestId('admin-webhook-delivery-push-failures-replay'));
    await waitFor(() => expect(api.replayWebhookFailure).toHaveBeenCalledWith(501, '运营复核后处理'));
    fireEvent.click(screen.getByTestId('admin-webhook-delivery-push-failures-pause'));
    await waitFor(() => expect(api.pauseWebhookFailure).toHaveBeenCalledWith(501, '运营复核后处理'));
    fireEvent.click(screen.getByTestId('admin-webhook-delivery-push-failures-resume'));
    await waitFor(() => expect(api.resumeWebhookFailure).toHaveBeenCalledWith(501, '运营复核后处理'));
  });
});
