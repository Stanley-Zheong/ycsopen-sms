import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export interface TemplateHistory {
  eventType: string;
  actor: string;
  opinion: string;
  snapshotContent: string;
  variableNames: string[];
  createdAt: string;
}

export interface TemplateRecord {
  id: number;
  tenantId: number;
  templateCode: string;
  templateName: string;
  content: string;
  templateType: string;
  signatureId: number;
  paramCheckRule: string | null;
  description: string | null;
  variableNames: string[];
  versionNo: number;
  previousTemplateId: number | null;
  auditStatus: string;
  auditComment: string | null;
  auditTime: string | null;
  createdAt: string;
  history: TemplateHistory[];
}

export interface TemplatePayload {
  templateName: string;
  content: string;
  templateType: string;
  signatureId: number;
  paramCheckRule: string;
  description: string;
}

export interface TemplateReviewSummary {
  total: number;
  pending: number;
  approved: number;
  rejected: number;
  amendmentRequired: number;
}

export interface TemplateReviewQueue {
  summary: TemplateReviewSummary;
  items: TemplateRecord[];
}

export async function listTenantTemplates(): Promise<TemplateRecord[]> {
  const res = await apiClient.get<ApiResponse<TemplateRecord[]>>('/console/tenant/templates');
  return res.data.data;
}

export async function submitTemplateApplication(payload: TemplatePayload): Promise<TemplateRecord> {
  const res = await apiClient.post<ApiResponse<TemplateRecord>>('/console/tenant/templates', payload);
  return res.data.data;
}

export async function previewTemplate(templateId: number, variables: Record<string, string>): Promise<string> {
  const res = await apiClient.post<ApiResponse<{ renderedContent: string }>>(`/console/tenant/templates/${templateId}/preview`, { variables });
  return res.data.data.renderedContent;
}

export async function resubmitTemplate(templateId: number, payload: TemplatePayload): Promise<TemplateRecord> {
  const res = await apiClient.post<ApiResponse<TemplateRecord>>(`/console/tenant/templates/${templateId}/resubmit`, payload);
  return res.data.data;
}

export async function listTemplateReviewQueue(keyword = ''): Promise<TemplateReviewQueue> {
  const query = keyword.trim() ? `?keyword=${encodeURIComponent(keyword.trim())}` : '';
  const res = await apiClient.get<ApiResponse<TemplateReviewQueue>>(`/console/templates/review${query}`);
  return res.data.data;
}

export async function decideTemplate(templateId: number, decision: string, opinion: string): Promise<TemplateRecord> {
  const res = await apiClient.post<ApiResponse<TemplateRecord>>(`/console/templates/${templateId}/decisions`, { decision, opinion });
  return res.data.data;
}
