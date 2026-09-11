import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export interface UnsubscribeKeyword {
  id: number;
  keyword: string;
  keywordNormalized: string;
  scope: string;
  tenantId: number | null;
  status: string;
  createdBy: string | null;
  updatedAt: string | null;
}

export interface UnsubscribeRecord {
  id: number;
  tenantId: number;
  signatureId: number | null;
  productCode: string | null;
  maskedMobile: string | null;
  triggerKeyword: string | null;
  method: string;
  result: string;
  handlingState: string;
  notificationState: string;
  notificationEventId: number | null;
  confirmationState: string;
  replyEventId: number | null;
  uplinkRecordId: number | null;
  unsubscribedAt: string | null;
  updatedAt: string | null;
}

export interface UnsubscribeStatisticsRow {
  tenantId: number;
  signatureId: number | null;
  productCode: string | null;
  unsubscribeCount: number;
  finalSentCount: number;
  rate: number;
}

export interface UnsubscribeAlertEvent {
  id: number;
  tenantId: number;
  signatureId: number | null;
  productCode: string | null;
  periodStart: string | null;
  periodEnd: string | null;
  unsubscribeCount: number;
  finalSentCount: number;
  rate: number;
  thresholdRate: number;
  formula: string;
  freshnessAt: string | null;
  sourceEvent: string;
}

export interface ExportRequestResponse {
  taskId: number;
  status: string;
  recordCount: number;
  fileFormat: string;
}

function data<T>(res: { data: ApiResponse<T> }): T {
  return res.data.data;
}

function params(filter: Record<string, unknown>) {
  return Object.fromEntries(Object.entries(filter).filter(([, value]) => value !== undefined && value !== null && value !== ''));
}

export async function listAdminUnsubscribes(filter: Record<string, unknown>): Promise<UnsubscribeRecord[]> {
  return data(await apiClient.get<ApiResponse<UnsubscribeRecord[]>>('/console/unsubscribes', { params: params(filter) }));
}

export async function listAdminUnsubscribeKeywords(tenantId?: string): Promise<UnsubscribeKeyword[]> {
  return data(await apiClient.get<ApiResponse<UnsubscribeKeyword[]>>('/console/unsubscribe/keywords', { params: params({ tenantId }) }));
}

export async function saveAdminUnsubscribeKeyword(input: Record<string, unknown>): Promise<UnsubscribeKeyword> {
  return data(await apiClient.put<ApiResponse<UnsubscribeKeyword>>('/console/unsubscribe/keywords', input));
}

export async function listUnsubscribeStatistics(filter: Record<string, unknown>): Promise<UnsubscribeStatisticsRow[]> {
  return data(await apiClient.get<ApiResponse<UnsubscribeStatisticsRow[]>>('/console/unsubscribe/statistics', { params: params(filter) }));
}

export async function evaluateUnsubscribeAlerts(input: Record<string, unknown>): Promise<UnsubscribeAlertEvent[]> {
  return data(await apiClient.post<ApiResponse<UnsubscribeAlertEvent[]>>('/console/unsubscribe/alerts/evaluate', input));
}

export async function listTenantUnsubscribes(filter: Record<string, unknown>): Promise<UnsubscribeRecord[]> {
  return data(await apiClient.get<ApiResponse<UnsubscribeRecord[]>>('/console/tenant/unsubscribes', { params: params(filter) }));
}

export async function listTenantUnsubscribeKeywords(): Promise<UnsubscribeKeyword[]> {
  return data(await apiClient.get<ApiResponse<UnsubscribeKeyword[]>>('/console/tenant/unsubscribe/keywords'));
}

export async function saveTenantUnsubscribeKeyword(input: Record<string, unknown>): Promise<UnsubscribeKeyword> {
  return data(await apiClient.put<ApiResponse<UnsubscribeKeyword>>('/console/tenant/unsubscribe/keywords', input));
}

export async function requestTenantUnsubscribeExport(input: Record<string, unknown>): Promise<ExportRequestResponse> {
  return data(await apiClient.post<ApiResponse<ExportRequestResponse>>('/console/tenant/unsubscribes/export-request', input));
}
