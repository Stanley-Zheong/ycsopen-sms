import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export const FEE_WARNING_PERMISSIONS = {
  menu: 'fee-warning:menu',
  read: 'fee-warning:read',
  write: 'fee-warning:write',
};

export interface FeeWarningRule {
  id: number;
  ruleName: string;
  tenantId: number | null;
  metricType: string;
  thresholdValue: number;
  action: string;
  notifyChannels: string;
  notificationTargets: string;
  status: string;
  updatedAt: string | null;
}

export interface FeeWarningEpisode {
  id: number;
  tenantId: number;
  ruleId: number;
  alertRecordId: number | null;
  metricType: string;
  sourceKey: string;
  sourceAmountMil: number;
  creditLimitMil: number | null;
  usedAmountMil: number | null;
  thresholdValue: number;
  ratio: number | null;
  action: string;
  status: string;
  approvalState: string;
  deliveryState: string;
  sourceSnapshot: string;
  actor: string;
  resolutionNote: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

function data<T>(res: { data: ApiResponse<T> }): T {
  return res.data.data;
}

export async function listFeeWarningRules(tenantId?: number): Promise<FeeWarningRule[]> {
  return data(await apiClient.get<ApiResponse<FeeWarningRule[]>>('/console/fee-warnings/rules', {
    params: tenantId ? { tenantId } : {},
  }));
}

export async function saveFeeWarningRule(input: Record<string, unknown>): Promise<FeeWarningRule> {
  return data(await apiClient.post<ApiResponse<FeeWarningRule>>('/console/fee-warnings/rules', input));
}

export async function evaluateFeeWarning(input: { tenantId: number; estimatedAmountMil: number }): Promise<FeeWarningEpisode[]> {
  return data(await apiClient.post<ApiResponse<FeeWarningEpisode[]>>('/console/fee-warnings/evaluate', input));
}

export async function listFeeWarningEpisodes(tenantId?: number): Promise<FeeWarningEpisode[]> {
  return data(await apiClient.get<ApiResponse<FeeWarningEpisode[]>>('/console/fee-warnings/episodes', {
    params: tenantId ? { tenantId } : {},
  }));
}

export async function approveFeeWarningEpisode(id: number, reason: string): Promise<FeeWarningEpisode> {
  return data(await apiClient.post<ApiResponse<FeeWarningEpisode>>(
    `/console/fee-warnings/episodes/${id}/approve`,
    { reason },
  ));
}

export async function listTenantFeeWarningEpisodes(): Promise<FeeWarningEpisode[]> {
  return data(await apiClient.get<ApiResponse<FeeWarningEpisode[]>>('/tenant/fee-warnings/episodes'));
}
