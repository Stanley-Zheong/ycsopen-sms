import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export const CONTENT_SAFETY_PERMISSIONS = {
  menu: 'content-safety:menu',
  read: 'content-safety:read',
  write: 'content-safety:write',
  import: 'content-safety:import',
  export: 'content-safety:export',
  scan: 'content-safety:scan',
} as const;

export interface ContentSafetyPolicy {
  id: number;
  word: string;
  category: string;
  level: string;
  replacement: string | null;
  action: string;
  scope: string;
  scopeRefId: number | null;
  status: string;
  hitCount: number;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface ContentSafetyPolicyPayload {
  id?: number | null;
  word: string;
  category: string;
  level: string;
  replacement?: string | null;
  action: string;
  scope: string;
  scopeRefId?: number | null;
  status: string;
}

export interface ContentSafetyImportPayload {
  words: string[];
  category: string;
  level: string;
  replacement?: string | null;
  action: string;
  scope: string;
  scopeRefId?: number | null;
}

export interface ContentSafetyImportResult {
  success: number;
  failed: number;
  errors: string[];
}

export interface ContentSafetyAnalytics {
  total: number;
  active: number;
  hits: number;
  intercepts: number;
  replacements: number;
  alerts: number;
  interceptRate: number;
  coverageRate: number;
}

export interface ContentSafetyScanResult {
  blocked: boolean;
  reason: string | null;
  finalContent: string | null;
}

export interface ContentSafetyFilters {
  word?: string;
  category?: string;
  level?: string;
  action?: string;
  status?: string;
}

export async function listContentSafetyPolicies(filters: ContentSafetyFilters = {}): Promise<ContentSafetyPolicy[]> {
  const res = await apiClient.get<ApiResponse<ContentSafetyPolicy[]>>('/console/content-safety/policies', { params: filters });
  return res.data.data;
}

export async function saveContentSafetyPolicy(payload: ContentSafetyPolicyPayload): Promise<ContentSafetyPolicy> {
  const res = await apiClient.post<ApiResponse<ContentSafetyPolicy>>('/console/content-safety/policies', payload);
  return res.data.data;
}

export async function importContentSafetyPolicies(payload: ContentSafetyImportPayload): Promise<ContentSafetyImportResult> {
  const res = await apiClient.post<ApiResponse<ContentSafetyImportResult>>('/console/content-safety/policies/import', payload);
  return res.data.data;
}

export async function deleteContentSafetyPolicy(id: number): Promise<ContentSafetyPolicy> {
  const res = await apiClient.post<ApiResponse<ContentSafetyPolicy>>(`/console/content-safety/policies/${id}/delete`);
  return res.data.data;
}

export async function requestContentSafetyExport(filters: ContentSafetyFilters = {}): Promise<{ requestId: string; matchedRows: number; status: string }> {
  const res = await apiClient.post<ApiResponse<{ requestId: string; matchedRows: number; status: string }>>('/console/content-safety/policies/export-request', null, { params: filters });
  return res.data.data;
}

export async function contentSafetyAnalytics(): Promise<ContentSafetyAnalytics> {
  const res = await apiClient.get<ApiResponse<ContentSafetyAnalytics>>('/console/content-safety/analytics');
  return res.data.data;
}

export async function scanFinalContent(tenantId: number, templateId: number, content: string): Promise<ContentSafetyScanResult> {
  const res = await apiClient.post<ApiResponse<ContentSafetyScanResult>>('/console/content-safety/scan', { tenantId, templateId, content });
  return res.data.data;
}
