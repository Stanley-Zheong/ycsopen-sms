import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export type ComplaintStatus = 'PENDING' | 'PROCESSING' | 'PROCESSED' | 'CLOSED';

export interface ComplaintCaseRow {
  id: number;
  source: string;
  tenantId: number | null;
  channelId: number | null;
  signatureId: number | null;
  templateId: number | null;
  messageId: string | null;
  contentType: string | null;
  complainedMobile: string | null;
  summary: string;
  status: ComplaintStatus;
  attributionQuality: string;
  opinion: string | null;
  remediation: string | null;
  requirement: string | null;
  acceptedAt: string | null;
  handledAt: string | null;
  closedAt: string | null;
  closedNote: string | null;
  acceptedBy?: string | null;
  handledBy?: string | null;
  closedBy?: string | null;
}

export interface ComplaintCaseCreateInput {
  source: string;
  summary: string;
  tenantId: number | null;
  channelId: number | null;
  signatureId: number | null;
  templateId: number | null;
  messageId: string;
  contentType: string;
  complainedMobile: string;
  attributionQuality: string;
  requirement: string;
}

export interface ComplaintCaseStateInput {
  status: string;
  opinion: string;
  remediation: string;
  requirement: string;
  actor: string;
}

export interface ComplaintRemediationInput {
  disposalType: string;
  targetRef: string;
  actor: string;
  authorizedReviewId: string;
  reason: string;
}

export interface ComplaintRecoveryInput {
  disposalRecordId: number;
  authorizedReviewId: string;
  actor: string;
  resumeCondition: string;
}

export interface ComplaintRemediationRow {
  id: number;
  complaintId: number;
  disposalType: string;
  targetRef: string;
  status: string;
  authorizedReviewId: string | null;
  failureReason: string | null;
  originalComplaintId: number | null;
}

export interface ComplaintAnalyticsDimension {
  dimension: string;
  count: number;
}

export interface ComplaintAnalytics {
  totalCount: number;
  unknownAttributionCount: number;
  byTenant: ComplaintAnalyticsDimension[];
  bySignature: ComplaintAnalyticsDimension[];
  byContentType: ComplaintAnalyticsDimension[];
}

function data<T>(res: { data: ApiResponse<T> }): T {
  return res.data.data;
}

export async function listComplaintCases(): Promise<ComplaintCaseRow[]> {
  return data(await apiClient.get<ApiResponse<ComplaintCaseRow[]>>('/console/complaints'));
}

export async function createComplaintCase(input: ComplaintCaseCreateInput): Promise<ComplaintCaseRow> {
  return data(await apiClient.post<ApiResponse<ComplaintCaseRow>>('/console/complaints', input));
}

export async function acceptComplaintCase(id: number, input: ComplaintCaseStateInput): Promise<ComplaintCaseRow> {
  return data(await apiClient.post<ApiResponse<ComplaintCaseRow>>(`/console/complaints/${id}/accept`, input));
}

export async function handleComplaintCase(id: number, input: ComplaintCaseStateInput): Promise<ComplaintCaseRow> {
  return data(await apiClient.post<ApiResponse<ComplaintCaseRow>>(`/console/complaints/${id}/handle`, input));
}

export async function closeComplaintCase(id: number, input: ComplaintCaseStateInput): Promise<ComplaintCaseRow> {
  return data(await apiClient.post<ApiResponse<ComplaintCaseRow>>(`/console/complaints/${id}/close`, input));
}

export async function remediateComplaintCase(id: number, input: ComplaintRemediationInput): Promise<ComplaintRemediationRow> {
  return data(await apiClient.post<ApiResponse<ComplaintRemediationRow>>(`/console/complaints/${id}/remediations`, input));
}

export async function recoverComplaintRemediation(id: number, input: ComplaintRecoveryInput): Promise<ComplaintRemediationRow> {
  return data(await apiClient.post<ApiResponse<ComplaintRemediationRow>>(`/console/complaints/${id}/recoveries`, input));
}

export async function getComplaintAnalytics(): Promise<ComplaintAnalytics> {
  return data(await apiClient.get<ApiResponse<ComplaintAnalytics>>('/console/complaint-analytics'));
}
