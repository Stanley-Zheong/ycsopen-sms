import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export interface SignatureHistory {
  eventType: string;
  actor: string;
  opinion: string;
  riskLevel: string;
  createdAt: string;
}

export interface SignatureRecord {
  id: number;
  tenantId: number;
  signCode: string;
  signContent: string;
  signType: string;
  usageType: string;
  riskLevel: string;
  evidenceRef: string | null;
  applicantName: string | null;
  auditStatus: string;
  auditComment: string | null;
  auditTime: string | null;
  createdAt: string;
  history: SignatureHistory[];
}

export interface SignatureFiling {
  signatureId: number;
  channelId: number;
  channelName: string;
  protocol: string | null;
  operator: string | null;
  status: string;
  providerRequestId: string | null;
  resultMessage: string | null;
  attemptCount: number;
  channelEligible: boolean;
  channelEligibilityReason: string;
}

export interface SignatureReviewSummary {
  total: number;
  pending: number;
  approved: number;
  rejected: number;
  supplementRequired: number;
  highRisk: number;
}

export interface SignatureReviewQueue {
  summary: SignatureReviewSummary;
  items: SignatureRecord[];
}

export interface SignatureApplicationPayload {
  signContent: string;
  signType: string;
  usageType: string;
  evidenceRef: string;
  applicantName: string;
  applicantPhone: string;
}

export async function listTenantSignatures(): Promise<SignatureRecord[]> {
  const res = await apiClient.get<ApiResponse<SignatureRecord[]>>('/console/tenant/signatures');
  return res.data.data;
}

export async function submitSignatureApplication(payload: SignatureApplicationPayload): Promise<SignatureRecord> {
  const res = await apiClient.post<ApiResponse<SignatureRecord>>('/console/tenant/signatures', payload);
  return res.data.data;
}

export async function listUsableChannels(signatureId: number): Promise<SignatureFiling[]> {
  const res = await apiClient.get<ApiResponse<SignatureFiling[]>>(`/console/tenant/signatures/${signatureId}/usable-channels`);
  return res.data.data;
}

export async function listSignatureReviewQueue(keyword = ''): Promise<SignatureReviewQueue> {
  const query = keyword.trim() ? `?keyword=${encodeURIComponent(keyword.trim())}` : '';
  const res = await apiClient.get<ApiResponse<SignatureReviewQueue>>(`/console/signatures/review${query}`);
  return res.data.data;
}

export async function decideSignature(signatureId: number, decision: string, opinion: string): Promise<SignatureRecord> {
  const res = await apiClient.post<ApiResponse<SignatureRecord>>(`/console/signatures/${signatureId}/decisions`, { decision, opinion });
  return res.data.data;
}

export async function listSignatureFilings(signatureId: number): Promise<SignatureFiling[]> {
  const res = await apiClient.get<ApiResponse<SignatureFiling[]>>(`/console/signatures/${signatureId}/filings`);
  return res.data.data;
}

export async function requestSignatureFiling(signatureId: number, channelId: number): Promise<SignatureFiling> {
  const res = await apiClient.post<ApiResponse<SignatureFiling>>(`/console/signatures/${signatureId}/filings/${channelId}/request`);
  return res.data.data;
}

export async function recordSignatureFilingResult(
  signatureId: number,
  channelId: number,
  status: 'REGISTERED' | 'FAILED',
  resultMessage: string,
): Promise<SignatureFiling> {
  const res = await apiClient.post<ApiResponse<SignatureFiling>>(`/console/signatures/${signatureId}/filings/${channelId}/result`, {
    status,
    resultMessage,
  });
  return res.data.data;
}
