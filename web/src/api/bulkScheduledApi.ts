import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export interface BulkItemPayload {
  phoneNumber: string;
  variables: Record<string, string>;
}

export interface BulkCommandPayload {
  batchKey: string;
  taskName: string;
  messageType: string;
  priority: string;
  templateId: string;
  signId: string;
  scheduleAt?: string | null;
  sourceFileName: string;
  sizeBytes: number;
  malwareVerdict: string;
  items: BulkItemPayload[];
}

export interface PreviewRow {
  rowNo: number;
  maskedPhone: string;
  variables: Record<string, string>;
  validationStatus: string;
  validationReason: string | null;
}

export interface PreviewResult {
  tenantId: number;
  batchKey: string;
  taskName: string;
  sourceFileName: string;
  total: number;
  valid: number;
  invalid: number;
  rows: PreviewRow[];
}

export interface BulkTaskView {
  bulkId: number;
  tenantId: number;
  batchKey: string;
  taskName: string;
  messageType: string;
  priority: string;
  totalCount: number;
  validCount: number;
  invalidCount: number;
  runningCount: number;
  completedCount: number;
  failCount: number;
  cancelledCount: number;
  totalCost: number;
  state: string;
  scheduleAt: string | null;
  createdAt: string;
  failureReason: string | null;
  controlReason: string | null;
}

export interface BulkItemView {
  itemId: number;
  itemTrackingId: string;
  rowNo: number;
  messageId: string | null;
  sendStatus: string;
  validationStatus: string;
  validationReason: string | null;
  cost: number;
  updatedAt: string;
}

export interface BulkTaskDetail {
  task: BulkTaskView;
  items: BulkItemView[];
}

function data<T>(res: { data: ApiResponse<T> }): T {
  return res.data.data;
}

export async function previewBulk(payload: BulkCommandPayload): Promise<PreviewResult> {
  return data(await apiClient.post<ApiResponse<PreviewResult>>('/console/tenant/bulk/preview', payload));
}

export async function createBulk(payload: BulkCommandPayload): Promise<BulkTaskView> {
  return data(await apiClient.post<ApiResponse<BulkTaskView>>('/console/tenant/bulk/tasks', payload));
}

export async function listTenantScheduledTasks(): Promise<BulkTaskView[]> {
  return data(await apiClient.get<ApiResponse<BulkTaskView[]>>('/console/tenant/scheduled-tasks'));
}

export async function tenantControlTask(bulkId: number, action: string, reason: string): Promise<BulkTaskView> {
  return data(await apiClient.post<ApiResponse<BulkTaskView>>(`/console/tenant/scheduled-tasks/${bulkId}/${action}`, { reason }));
}

export async function listAdminBulkTasks(filter: { tenantId?: string; state?: string }): Promise<BulkTaskView[]> {
  const params = Object.fromEntries(Object.entries(filter).filter(([, value]) => value !== undefined && value !== ''));
  return data(await apiClient.get<ApiResponse<BulkTaskView[]>>('/console/bulk/tasks', { params }));
}

export async function getAdminBulkTaskDetail(bulkId: number): Promise<BulkTaskDetail> {
  return data(await apiClient.get<ApiResponse<BulkTaskDetail>>(`/console/bulk/tasks/${bulkId}`));
}

export async function adminControlTask(bulkId: number, action: string, reason: string): Promise<BulkTaskView> {
  return data(await apiClient.post<ApiResponse<BulkTaskView>>(`/console/bulk/tasks/${bulkId}/${action}`, { reason }));
}
