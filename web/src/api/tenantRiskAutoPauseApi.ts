import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export const TENANT_RISK_PERMISSIONS = {
  menu: 'tenant-risk:menu',
  read: 'tenant-risk:read',
  write: 'tenant-risk:write',
};

export interface TenantRiskRule {
  id: number;
  ruleName: string;
  tenantId: number | null;
  metric: string;
  thresholdValue: number;
  durationMinutes: number;
  action: string;
  notifyTargets: string;
  status: string;
  updatedAt: string | null;
}

export interface TenantRiskEpisode {
  id: number;
  tenantId: number;
  ruleId: number;
  alertRecordId: number | null;
  metric: string;
  sourceKey: string;
  sourceRegistry: string;
  numerator: number;
  denominator: number;
  rate: number | null;
  thresholdValue: number;
  windowMinutes: number;
  dataQuality: string;
  action: string;
  status: string;
  beforeLifecycleStatus: string | null;
  sourceSnapshot: string;
  recoveryReviewId: string | null;
  recoveredBy: string | null;
}

export interface TenantRiskEvaluationResult {
  episodeId: number | null;
  dataQuality: string;
  rate: number | null;
  paused: boolean;
}

function data<T>(res: { data: ApiResponse<T> }): T {
  return res.data.data;
}

export async function listTenantRiskRules(tenantId?: number): Promise<TenantRiskRule[]> {
  return data(await apiClient.get<ApiResponse<TenantRiskRule[]>>('/console/tenant-risk/rules', {
    params: tenantId ? { tenantId } : {},
  }));
}

export async function saveTenantRiskRule(input: Record<string, unknown>): Promise<TenantRiskRule> {
  return data(await apiClient.post<ApiResponse<TenantRiskRule>>('/console/tenant-risk/rules', input));
}

export async function evaluateTenantRisk(input: Record<string, unknown>): Promise<TenantRiskEvaluationResult> {
  return data(await apiClient.post<ApiResponse<TenantRiskEvaluationResult>>('/console/tenant-risk/evaluate', input));
}

export async function listTenantRiskEpisodes(tenantId?: number): Promise<TenantRiskEpisode[]> {
  return data(await apiClient.get<ApiResponse<TenantRiskEpisode[]>>('/console/tenant-risk/episodes', {
    params: tenantId ? { tenantId } : {},
  }));
}

export async function recoverTenantRiskEpisode(id: number, input: { reviewId: string; note: string }): Promise<TenantRiskEpisode> {
  return data(await apiClient.post<ApiResponse<TenantRiskEpisode>>(`/console/tenant-risk/episodes/${id}/recover`, input));
}
