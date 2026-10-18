import axios from 'axios';
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
  createdAt?: string | null;
  createdBy?: string | null;
  acceptedBy: string | null;
  handledBy: string | null;
  closedBy: string | null;
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
  requirement: string;
}

export interface ComplaintCaseStateInput {
  status: string;
  opinion: string;
  remediation: string;
  requirement: string;
  actor?: string;
}

export interface ComplaintRemediationInput {
  disposalType: string;
  targetRef: string;
  actor?: string;
  authorizedReviewId: string;
  reason: string;
}

export interface ComplaintRecoveryInput {
  disposalRecordId: number;
  authorizedReviewId: string;
  actor?: string;
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

export interface ComplaintCaseEvent {
  id: number;
  complaintId: number;
  eventType: string;
  actor: string | null;
  occurredAt: string;
  fromStatus: string | null;
  toStatus: string | null;
  evidenceText: string | null;
  targetRef: string | null;
  result: string;
  reviewId: string | null;
  failureReason: string | null;
  relatedDisposalId: number | null;
}

export interface ComplaintCaseDetail {
  complaint: ComplaintCaseRow;
  timeline: ComplaintCaseEvent[];
  remediations: ComplaintRemediationRow[];
}

export interface ComplaintReferenceOption {
  id: number;
  label: string;
  tenantId: number | null;
}

export interface ComplaintReferenceOptions {
  tenants: ComplaintReferenceOption[];
  channels: ComplaintReferenceOption[];
  signatures: ComplaintReferenceOption[];
  templates: ComplaintReferenceOption[];
}

export interface ComplaintAnalyticsDimension {
  dimension: string;
  count: number;
}

export interface ComplaintTrendPoint {
  day: string;
  count: number;
}

export interface ComplaintAnalytics {
  totalCount: number;
  unknownAttributionCount: number;
  trend: ComplaintTrendPoint[];
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

export async function getComplaintCaseDetail(id: number): Promise<ComplaintCaseDetail> {
  return data(await apiClient.get<ApiResponse<ComplaintCaseDetail>>(`/console/complaints/${id}`));
}

export async function getComplaintReferenceOptions(): Promise<ComplaintReferenceOptions> {
  return data(await apiClient.get<ApiResponse<ComplaintReferenceOptions>>('/console/complaint-reference-options'));
}

export async function listComplaintRemediations(): Promise<ComplaintRemediationRow[]> {
  return data(await apiClient.get<ApiResponse<ComplaintRemediationRow[]>>('/console/complaint-remediations'));
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

export function complaintFailureCode(error: unknown): string | null {
  if (!axios.isAxiosError(error)) return null;
  const code = error.response?.data?.data?.errorCode;
  return typeof code === 'string' ? code : null;
}
