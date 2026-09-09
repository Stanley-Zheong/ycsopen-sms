import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export const FREQUENCY_PERMISSIONS = {
  menu: 'frequency:menu',
  read: 'frequency:read',
  write: 'frequency:write',
  import: 'frequency:import',
  export: 'frequency:export',
  scan: 'frequency:scan',
} as const;

export interface FrequencyRuleRow {
  id: number;
  ruleName: string;
  limitType: string;
  limitCount: number;
  limitWindowSeconds: number;
  action: string;
  scope: string;
  scopeRefId: number | null;
  status: string;
  hitCount: number;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface FrequencyRulePayload {
  id?: number | null;
  ruleName: string;
  limitType: string;
  limitCount: number;
  limitWindowSeconds: number;
  action: string;
  scope: string;
  scopeRefId?: number | null;
  status: string;
}

export interface FrequencyImportPayload {
  ruleNames: string[];
  limitType: string;
  limitCount: number;
  limitWindowSeconds: number;
  action: string;
  scope: string;
  scopeRefId?: number | null;
}

export interface FrequencyAnalytics {
  total: number;
  active: number;
  hits: number;
  blocked: number;
  delayed: number;
  alerts: number;
  blockRate: number;
  coverageRate: number;
}

export interface FrequencyRuleFilters {
  name?: string;
  type?: string;
  action?: string;
  status?: string;
}

export async function listFrequencyRules(filters: FrequencyRuleFilters = {}): Promise<FrequencyRuleRow[]> {
  const res = await apiClient.get<ApiResponse<FrequencyRuleRow[]>>('/console/frequency/rules', { params: filters });
  return res.data.data;
}

export async function saveFrequencyRule(payload: FrequencyRulePayload): Promise<FrequencyRuleRow> {
  const res = await apiClient.post<ApiResponse<FrequencyRuleRow>>('/console/frequency/rules', payload);
  return res.data.data;
}

export async function importFrequencyRules(payload: FrequencyImportPayload): Promise<{ success: number; failed: number; errors: string[] }> {
  const res = await apiClient.post<ApiResponse<{ success: number; failed: number; errors: string[] }>>('/console/frequency/rules/import', payload);
  return res.data.data;
}

export async function enableFrequencyRule(id: number): Promise<FrequencyRuleRow> {
  const res = await apiClient.post<ApiResponse<FrequencyRuleRow>>(`/console/frequency/rules/${id}/enable`);
  return res.data.data;
}

export async function disableFrequencyRule(id: number): Promise<FrequencyRuleRow> {
  const res = await apiClient.post<ApiResponse<FrequencyRuleRow>>(`/console/frequency/rules/${id}/disable`);
  return res.data.data;
}

export async function requestFrequencyExport(filters: FrequencyRuleFilters = {}): Promise<{ requestId: string; matchedRows: number; status: string }> {
  const res = await apiClient.post<ApiResponse<{ requestId: string; matchedRows: number; status: string }>>(
    '/console/frequency/rules/export-request',
    null,
    { params: filters },
  );
  return res.data.data;
}

export async function frequencyAnalytics(): Promise<FrequencyAnalytics> {
  const res = await apiClient.get<ApiResponse<FrequencyAnalytics>>('/console/frequency/analytics');
  return res.data.data;
}
