import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export const TRIAL_PREPAID_PERMISSIONS = {
  menu: 'trial-prepaid:menu',
  read: 'trial-prepaid:read',
  write: 'trial-prepaid:write',
} as const;

export interface TrialOverview {
  tenantId: number;
  trialStatus: string;
  quotaTotal: number;
  quotaRemaining: number;
  validFrom: string;
  validUntil: string;
  version: number;
}

export interface ConsumptionEntry {
  tenantId: number;
  messageRef: string;
  businessType: string;
  quotaDelta: number;
  amountMil: number;
  entryType: string;
  state: string;
  actor: string;
  createdAt: string;
}

export interface BalanceAuditEntry {
  tenantId: number;
  businessDocId: string;
  mutationType: string;
  amountMil: number;
  beforeBalanceMil: number;
  afterBalanceMil: number;
  beforeFrozenMil: number;
  afterFrozenMil: number;
  accountVersion: number;
  actor: string;
  createdAt: string;
}

export interface ConversionRequest {
  id: number;
  tenantId: number;
  trialStatus: string;
  status: string;
}

function data<T>(res: { data: ApiResponse<T> }): T {
  return res.data.data;
}

export async function getTrialOverview(tenantId: number): Promise<TrialOverview> {
  return data(await apiClient.get<ApiResponse<TrialOverview>>(`/console/trial-prepaid/tenants/${tenantId}/overview`));
}

export async function activateTrial(tenantId: number, quota: number, startAt: string | null, endAt: string | null): Promise<TrialOverview> {
  return data(await apiClient.post<ApiResponse<TrialOverview>>(`/console/trial-prepaid/tenants/${tenantId}/trial`, { quota, startAt, endAt }));
}

export async function consumeTrial(tenantId: number, messageRef: string, businessType: string): Promise<TrialOverview> {
  return data(await apiClient.post<ApiResponse<TrialOverview>>(`/console/trial-prepaid/tenants/${tenantId}/trial/consume`, { messageRef, businessType }));
}

export async function requestConversion(tenantId: number): Promise<ConversionRequest> {
  return data(await apiClient.post<ApiResponse<ConversionRequest>>(`/console/trial-prepaid/tenants/${tenantId}/conversion-request`));
}

export async function listConsumption(tenantId: number | null, businessType = ''): Promise<ConsumptionEntry[]> {
  return data(await apiClient.get<ApiResponse<ConsumptionEntry[]>>('/console/trial-prepaid/consumption', {
    params: { tenantId, businessType: businessType || undefined },
  }));
}

export async function listBalanceAudits(tenantId: number | null): Promise<BalanceAuditEntry[]> {
  return data(await apiClient.get<ApiResponse<BalanceAuditEntry[]>>('/console/trial-prepaid/balance-audits', {
    params: { tenantId },
  }));
}
