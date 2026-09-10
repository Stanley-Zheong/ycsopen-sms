import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export interface OperationFilter {
  tenantId?: string;
  messageId?: string;
  status?: string;
  channelId?: string;
  errorCode?: string;
}

export interface SubmissionRow {
  submissionId: number;
  tenantId: number;
  submitId: string;
  messageId: string | null;
  sourceProtocol: string;
  productType: string;
  submissionStatus: string;
  sendStatus: string | null;
  templateId: number | null;
  signatureId: number | null;
  errorCode: string | null;
  errorMessage: string | null;
  createdAt: string;
}

export interface SendRow {
  taskId: number;
  messageId: string;
  tenantId: number;
  submissionId: number | null;
  maskedMobile: string;
  contentSummary: string;
  sendStatus: string;
  channelId: number | null;
  providerMessageId: string | null;
  carrier: string | null;
  province: string | null;
  city: string | null;
  errorCode: string | null;
  errorMessage: string | null;
  cost: number;
  retryCount: number;
  outboxState: string | null;
  sentAt: string | null;
  deliveredAt: string | null;
  createdAt: string;
  version: number;
}

export interface ReceiptRow {
  receiptId: number;
  messageId: string;
  tenantId: number | null;
  maskedMobile: string;
  channelId: number | null;
  providerMessageId: string | null;
  receiptStatus: string;
  sendStatus: string | null;
  errorCode: string | null;
  rawPayloadSummary: string;
  receiptDigest: string | null;
  carrier: string | null;
  province: string | null;
  city: string | null;
  reportTime: string;
}

export interface ErrorGroupRow {
  normalizedCode: string;
  platformCategory: string;
  severity: string;
  retryable: boolean;
  totalCount: number;
  tenantCount: number;
  channelCount: number;
  firstSeenAt: string;
  lastSeenAt: string;
}

export interface ActionResult {
  actionId: string;
  action: string;
  target: string;
  status: string;
  resultCode: string | null;
  resultMessage: string | null;
}

export interface BulkActionResult {
  actionId: string;
  action: string;
  total: number;
  completed: number;
  failed: number;
  results: ActionResult[];
}

function data<T>(res: { data: ApiResponse<T> }): T {
  return res.data.data;
}

function params(filter: OperationFilter) {
  return Object.fromEntries(Object.entries(filter).filter(([, value]) => value !== undefined && value !== ''));
}

export async function listSubmissions(filter: OperationFilter): Promise<SubmissionRow[]> {
  return data(await apiClient.get<ApiResponse<SubmissionRow[]>>('/console/message-operations/submissions', { params: params(filter) }));
}

export async function listSends(filter: OperationFilter): Promise<SendRow[]> {
  return data(await apiClient.get<ApiResponse<SendRow[]>>('/console/message-operations/sends', { params: params(filter) }));
}

export async function listReceipts(filter: OperationFilter): Promise<ReceiptRow[]> {
  return data(await apiClient.get<ApiResponse<ReceiptRow[]>>('/console/message-operations/receipts', { params: params(filter) }));
}

export async function listErrorGroups(filter: OperationFilter): Promise<ErrorGroupRow[]> {
  return data(await apiClient.get<ApiResponse<ErrorGroupRow[]>>('/console/message-operations/errors', { params: params(filter) }));
}

export async function resendMessage(messageId: string, actionId: string, reason: string): Promise<ActionResult> {
  return data(await apiClient.post<ApiResponse<ActionResult>>(`/console/message-operations/sends/${messageId}/resend`, { actionId, reason }));
}

export async function appealMessage(messageId: string, actionId: string, reason: string): Promise<ActionResult> {
  return data(await apiClient.post<ApiResponse<ActionResult>>(`/console/message-operations/sends/${messageId}/appeal`, { actionId, reason }));
}

export async function correctReceipt(receiptId: number, actionId: string, reason: string, status: string, errorCode: string, taxonomyVersion: string): Promise<ActionResult> {
  return data(await apiClient.post<ApiResponse<ActionResult>>(`/console/message-operations/receipts/${receiptId}/correct`, {
    actionId,
    reason,
    status,
    errorCode,
    taxonomyVersion,
  }));
}

export async function replayReceipt(receiptId: number, actionId: string, reason: string): Promise<ActionResult> {
  return data(await apiClient.post<ApiResponse<ActionResult>>(`/console/message-operations/receipts/${receiptId}/replay`, { actionId, reason }));
}

export async function bulkErrorAction(actionId: string, action: string, errorCode: string, messageIds: string[], reason: string): Promise<BulkActionResult> {
  return data(await apiClient.post<ApiResponse<BulkActionResult>>('/console/message-operations/errors/actions', {
    actionId,
    action,
    errorCode,
    messageIds,
    reason,
  }));
}

export async function requestMessageExport(filter: OperationFilter, actionId: string, reason: string, exportType = 'MESSAGE_OPERATIONS'): Promise<ActionResult> {
  return data(await apiClient.post<ApiResponse<ActionResult>>('/console/message-operations/exports', { actionId, reason }, {
    params: { ...params(filter), exportType },
  }));
}
