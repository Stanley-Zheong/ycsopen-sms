import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export const EXEMPTION_PERMISSIONS = {
  menu: 'exemption:menu',
  read: 'exemption:read',
  write: 'exemption:write',
  audit: 'exemption:audit',
} as const;

export interface ExemptionPolicyRecord {
  id: number;
  tenantId: number;
  exemptionType: string;
  resourceId: string;
  productCode: string;
  scopeExpression: string;
  approvalStatus: string;
  validFrom: string;
  validUntil: string;
  revoked: boolean;
  versionNo: number;
  usageCount: number;
  reason: string;
  createdBy: string;
  revokedBy: string | null;
  revokeReason: string | null;
  revokedAt: string | null;
  createdAt: string;
}

export interface ExemptionPolicyPayload {
  tenantId: number;
  exemptionType: string;
  resourceId: string;
  productCode: string;
  scopeExpression: string;
  approvalStatus: string;
  validFrom: string;
  validUntil: string;
  reason: string;
}

export interface ExemptionPreviewPayload {
  tenantId: number;
  exemptionType: string;
  resourceId: string;
  productCode: string;
  scopeExpression: string;
  controlCode: string;
  reason: string;
}

export interface ExemptionPreviewResult {
  result: string;
  exemptionRuleId: number | null;
  versionNo: number | null;
  reason: string;
  subjectType: string;
  subjectId: string;
  productCode: string;
  scopeExpression: string;
}

export interface ExemptionUsageRecord {
  id: number;
  exemptionRuleId: number | null;
  versionNo: number | null;
  tenantId: number;
  subjectType: string;
  subjectId: string;
  productCode: string;
  scopeExpression: string;
  controlCode: string;
  actor: string;
  reason: string;
  result: string;
  createdAt: string;
}

export async function listExemptionPolicies(): Promise<ExemptionPolicyRecord[]> {
  const res = await apiClient.get<ApiResponse<ExemptionPolicyRecord[]>>('/console/exemptions');
  return res.data.data;
}

export async function createExemptionPolicy(payload: ExemptionPolicyPayload): Promise<ExemptionPolicyRecord> {
  const res = await apiClient.post<ApiResponse<ExemptionPolicyRecord>>('/console/exemptions', payload);
  return res.data.data;
}

export async function previewExemptionPolicy(payload: ExemptionPreviewPayload): Promise<ExemptionPreviewResult> {
  const res = await apiClient.post<ApiResponse<ExemptionPreviewResult>>('/console/exemptions/effective-preview', payload);
  return res.data.data;
}

export async function revokeExemptionPolicy(id: number, reason: string): Promise<ExemptionPolicyRecord> {
  const res = await apiClient.post<ApiResponse<ExemptionPolicyRecord>>(`/console/exemptions/${id}/revoke`, { reason });
  return res.data.data;
}

export async function listExemptionUsageHistory(): Promise<ExemptionUsageRecord[]> {
  const res = await apiClient.get<ApiResponse<ExemptionUsageRecord[]>>('/console/exemptions/usage-history');
  return res.data.data;
}
