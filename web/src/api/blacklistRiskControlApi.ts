import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export const BLACKLIST_RISK_PERMISSIONS = {
  menu: 'blacklist:menu',
  read: 'blacklist:read',
  write: 'blacklist:write',
  import: 'blacklist:import',
  export: 'blacklist:export',
  providerRead: 'risk-provider:read',
  providerWrite: 'risk-provider:write',
  analysisRead: 'risk-analysis:read',
  analysisCheck: 'risk-analysis:check',
  appeal: 'risk-analysis:appeal',
} as const;

export interface BlacklistEntryRow {
  id: number;
  tenantId: number | null;
  maskedMobile: string;
  mobileRef: string;
  listType: string;
  reason: string;
  source: string;
  status: string;
  createdBy: string;
  createdAt: string;
  expiresAt: string | null;
}

export interface BlacklistEntryPayload {
  tenantId: number | null;
  mobile: string;
  listType: string;
  source: string;
  reason: string;
  expiresAt?: string | null;
}

export interface BlacklistImportPayload {
  tenantId: number | null;
  mobiles: string[];
  listType: string;
  source: string;
  reason: string;
  expiresAt?: string | null;
}

export interface BlacklistImportResult {
  success: number;
  failed: number;
  errors: string[];
}

export interface RiskProviderConfig {
  id: number;
  providerName: string;
  providerUrl: string;
  credentialRef: string;
  checkLevel: string;
  thresholdScore: number;
  timeoutMs: number;
  fallbackPolicy: string;
  status: string;
  cacheTtlSeconds: number;
  createdBy: string;
  createdAt: string;
}

export interface RiskProviderPayload {
  providerName: string;
  providerUrl: string;
  credentialRef: string;
  checkLevel: string;
  thresholdScore: number;
  timeoutMs: number;
  fallbackPolicy: string;
  status: string;
  cacheTtlSeconds: number;
}

export interface RiskDecisionRow {
  id: number;
  requestId: string;
  tenantId: number;
  mobileRef: string;
  sourceCategory: string;
  riskResult: string;
  traceReason: string;
  taskCreated: boolean;
  charged: boolean;
  createdAt: string;
}

export interface RiskAnalytics {
  total: number;
  blocked: number;
  allowed: number;
  systemHits: number;
  tenantHits: number;
  providerHits: number;
  degraded: number;
  appeals: number;
}

export interface BlacklistEntryFilters {
  tenantId?: string;
  listType?: string;
  status?: string;
}

export async function listBlacklistEntries(filters: BlacklistEntryFilters = {}): Promise<BlacklistEntryRow[]> {
  const res = await apiClient.get<ApiResponse<BlacklistEntryRow[]>>('/console/risk/blacklist', { params: filters });
  return res.data.data;
}

export async function createBlacklistEntry(payload: BlacklistEntryPayload): Promise<BlacklistEntryRow> {
  const res = await apiClient.post<ApiResponse<BlacklistEntryRow>>('/console/risk/blacklist', payload);
  return res.data.data;
}

export async function importBlacklistEntries(payload: BlacklistImportPayload): Promise<BlacklistImportResult> {
  const res = await apiClient.post<ApiResponse<BlacklistImportResult>>('/console/risk/blacklist/import', payload);
  return res.data.data;
}

export async function disableBlacklistEntry(id: number): Promise<BlacklistEntryRow> {
  const res = await apiClient.post<ApiResponse<BlacklistEntryRow>>(`/console/risk/blacklist/${id}/disable`);
  return res.data.data;
}

export async function requestBlacklistExport(filters: BlacklistEntryFilters = {}): Promise<{ requestId: string; matchedRows: number; status: string }> {
  const res = await apiClient.post<ApiResponse<{ requestId: string; matchedRows: number; status: string }>>('/console/risk/blacklist/export-request', null, { params: filters });
  return res.data.data;
}

export async function saveRiskProvider(payload: RiskProviderPayload): Promise<RiskProviderConfig> {
  const res = await apiClient.post<ApiResponse<RiskProviderConfig>>('/console/risk/provider', payload);
  return res.data.data;
}

export async function listRiskProviders(): Promise<RiskProviderConfig[]> {
  const res = await apiClient.get<ApiResponse<RiskProviderConfig[]>>('/console/risk/provider');
  return res.data.data;
}

export async function checkRisk(tenantId: number, mobileRefs: string[], forceProviderFailure = false): Promise<RiskDecisionRow[]> {
  const res = await apiClient.post<ApiResponse<RiskDecisionRow[]>>('/console/risk/check', {
    tenantId,
    mobileRefs,
    forceProviderFailure,
  });
  return res.data.data;
}

export async function riskAnalytics(): Promise<RiskAnalytics> {
  const res = await apiClient.get<ApiResponse<RiskAnalytics>>('/console/risk/analytics');
  return res.data.data;
}

export async function appealRiskDecision(decisionId: number, reason: string): Promise<{ id: number; decisionId: number; originalResult: string; appealResult: string }> {
  const res = await apiClient.post<ApiResponse<{ id: number; decisionId: number; originalResult: string; appealResult: string }>>('/console/risk/appeals', { decisionId, reason });
  return res.data.data;
}
