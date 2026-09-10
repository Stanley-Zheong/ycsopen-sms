import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export interface UplinkRecord {
  id: number;
  tenantId: number;
  sourceProtocol: string;
  sourceConnector: string;
  sourceEventId: string;
  messageId: string | null;
  phoneMasked: string;
  content: string;
  contentKeyword: string | null;
  state: string;
  carrier: string | null;
  province: string | null;
  city: string | null;
  destination: string | null;
  channelId: number | null;
  signatureId: number | null;
  productCode: string | null;
  pushState: string;
  pushEventId: number | null;
  receiveTime: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface UplinkPushMonitorRow {
  eventId: number;
  tenantId: number;
  sourceId: string;
  logicalId: string;
  destinationUrl: string;
  state: string;
  attemptCount: number;
  maxAttempts: number;
  attemptRows: number;
  nextAttemptAt: string | null;
  updatedAt: string | null;
  latencyMs: number;
}

export interface UplinkAutoReplyConfig {
  tenantId: number;
  enabled: boolean;
  keyword: string | null;
  templateId: string | null;
  responseContent: string | null;
  loopGuardMinutes: number;
  auditReason: string | null;
  updatedBy: string;
  updatedAt: string | null;
}

export interface UplinkDeliveryResult {
  eventId: number;
  tenantId: number;
  state: string;
  resultCode: string;
  resultMessage: string | null;
}

function data<T>(res: { data: ApiResponse<T> }): T {
  return res.data.data;
}

function params(filter: Record<string, unknown>) {
  return Object.fromEntries(Object.entries(filter).filter(([, value]) => value !== undefined && value !== null && value !== ''));
}

export async function listAdminUplinks(filter: Record<string, unknown>): Promise<UplinkRecord[]> {
  return data(await apiClient.get<ApiResponse<UplinkRecord[]>>('/console/uplinks', { params: params(filter) }));
}

export async function getAdminUplink(id: number): Promise<UplinkRecord> {
  return data(await apiClient.get<ApiResponse<UplinkRecord>>(`/console/uplinks/${id}`));
}

export async function replayAdminUplink(id: number, reason: string): Promise<UplinkDeliveryResult> {
  return data(await apiClient.post<ApiResponse<UplinkDeliveryResult>>(`/console/uplinks/${id}/replay`, { reason }));
}

export async function listUplinkPushMonitor(filter: Record<string, unknown>): Promise<UplinkPushMonitorRow[]> {
  return data(await apiClient.get<ApiResponse<UplinkPushMonitorRow[]>>('/console/uplinks/push-monitor', { params: params(filter) }));
}

export async function replayUplinkPushEvent(eventId: number, reason: string): Promise<UplinkDeliveryResult> {
  return data(await apiClient.post<ApiResponse<UplinkDeliveryResult>>(`/console/uplinks/push-monitor/${eventId}/replay`, { reason }));
}

export async function pauseUplinkPushEvent(eventId: number, reason: string): Promise<UplinkDeliveryResult> {
  return data(await apiClient.post<ApiResponse<UplinkDeliveryResult>>(`/console/uplinks/push-monitor/${eventId}/pause`, { reason }));
}

export async function resumeUplinkPushEvent(eventId: number, reason: string): Promise<UplinkDeliveryResult> {
  return data(await apiClient.post<ApiResponse<UplinkDeliveryResult>>(`/console/uplinks/push-monitor/${eventId}/resume`, { reason }));
}

export async function listTenantUplinks(filter: Record<string, unknown>): Promise<UplinkRecord[]> {
  return data(await apiClient.get<ApiResponse<UplinkRecord[]>>('/console/tenant/uplinks', { params: params(filter) }));
}

export async function getTenantUplinkAutoReply(): Promise<UplinkAutoReplyConfig> {
  return data(await apiClient.get<ApiResponse<UplinkAutoReplyConfig>>('/console/tenant/uplinks/auto-reply'));
}

export async function saveTenantUplinkAutoReply(input: Record<string, unknown>): Promise<UplinkAutoReplyConfig> {
  return data(await apiClient.put<ApiResponse<UplinkAutoReplyConfig>>('/console/tenant/uplinks/auto-reply', input));
}
