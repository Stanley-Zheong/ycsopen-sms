import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export interface WebhookConfig {
  tenantId: number;
  statusCallbackUrl: string | null;
  uplinkCallbackUrl: string | null;
  unsubscribeCallbackUrl: string | null;
  retryMaxCount: number;
  retryBackoffSeconds: number;
  status: string;
  latestFailureReason: string | null;
  pausedAt: string | null;
  version: number;
}

export interface WebhookTestResult {
  eventId: number;
  destinationUrl: string;
  state: string;
  resultCode: string;
  resultMessage: string | null;
}

export interface WebhookFailureRow {
  eventId: number;
  tenantId: number;
  eventType: string;
  sourceId: string;
  logicalId: string;
  destinationUrl: string;
  state: string;
  attemptCount: number;
  maxAttempts: number;
  nextAttemptAt: string | null;
  updatedAt: string | null;
}

export interface DeliveryResult {
  eventId: number;
  tenantId: number;
  state: string;
  resultCode: string;
  resultMessage: string | null;
}

function data<T>(res: { data: ApiResponse<T> }): T {
  return res.data.data;
}

export async function getTenantWebhookConfig(): Promise<WebhookConfig> {
  return data(await apiClient.get<ApiResponse<WebhookConfig>>('/console/tenant/webhooks'));
}

export async function saveTenantWebhookConfig(input: Record<string, unknown>): Promise<WebhookConfig> {
  return data(await apiClient.put<ApiResponse<WebhookConfig>>('/console/tenant/webhooks', input));
}

export async function testTenantWebhook(type: 'STATUS' | 'UPLINK' | 'UNSUBSCRIBE', destinationUrl: string): Promise<WebhookTestResult> {
  return data(await apiClient.post<ApiResponse<WebhookTestResult>>('/console/tenant/webhooks/test', { type, destinationUrl }));
}

export async function listWebhookFailures(filter: { tenantId?: string; state?: string }): Promise<WebhookFailureRow[]> {
  const params = Object.fromEntries(Object.entries(filter).filter(([, value]) => value !== undefined && value !== ''));
  return data(await apiClient.get<ApiResponse<WebhookFailureRow[]>>('/console/webhook-deliveries/failures', { params }));
}

export async function replayWebhookFailure(eventId: number, reason: string): Promise<DeliveryResult> {
  return data(await apiClient.post<ApiResponse<DeliveryResult>>(`/console/webhook-deliveries/${eventId}/replay`, { reason }));
}

export async function pauseWebhookFailure(eventId: number, reason: string): Promise<DeliveryResult> {
  return data(await apiClient.post<ApiResponse<DeliveryResult>>(`/console/webhook-deliveries/${eventId}/pause`, { reason }));
}

export async function resumeWebhookFailure(eventId: number, reason: string): Promise<DeliveryResult> {
  return data(await apiClient.post<ApiResponse<DeliveryResult>>(`/console/webhook-deliveries/${eventId}/resume`, { reason }));
}
