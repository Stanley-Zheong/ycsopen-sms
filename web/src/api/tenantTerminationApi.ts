import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export const TENANT_TERMINATION_PERMISSIONS = {
  menu: 'tenant-termination:menu',
  read: 'tenant-termination:read',
  write: 'tenant-termination:write',
  approve: 'tenant-termination:approve',
  effect: 'tenant-termination:effect',
} as const;

export interface TerminationRequestView {
  id: number;
  tenantId: number;
  reason: string;
  requestEvidence: string;
  requestStatus: string;
  requestedBy: string;
  requestedAt: string;
  approvedBy: string | null;
  approvedAt: string | null;
  adminOpinion: string | null;
  effectiveAt: string | null;
  clearanceSnapshotJson: string;
  participantSnapshotJson: string;
  compensationJson: string | null;
}

export interface ParticipantView {
  id: number;
  requestId: number;
  tenantId: number;
  participantCode: string;
  participantName: string;
  participantState: string;
  blockerCount: number;
  evidenceJson: string;
}

export interface AuditView {
  id: number;
  requestId: number;
  tenantId: number;
  action: string;
  actor: string;
  resultStatus: string;
  evidenceJson: string;
  createdAt: string;
}

export interface ParticipantDefinition {
  code: string;
  name: string;
  rule: string;
}

export interface TerminationDetail {
  request: TerminationRequestView;
  participants: ParticipantView[];
  audits: AuditView[];
}

export interface ClearanceItem {
  code: string;
  passed: boolean;
  amountOrCount: number;
  evidence: string;
}

export interface ClearanceView {
  tenantId: number;
  lifecycleStatus: string;
  billingMode: string | null;
  clearancePassed: boolean;
  items: ClearanceItem[];
}

function data<T>(res: { data: ApiResponse<T> }): T {
  return res.data.data;
}

export function parseClearance(row: TerminationRequestView | null | undefined): ClearanceView | null {
  if (!row?.clearanceSnapshotJson) return null;
  try {
    return JSON.parse(row.clearanceSnapshotJson) as ClearanceView;
  } catch {
    return null;
  }
}

export async function listTenantTerminations(filter: { tenantId?: string; status?: string } = {}): Promise<TerminationRequestView[]> {
  return data(await apiClient.get<ApiResponse<TerminationRequestView[]>>('/console/tenant-terminations', {
    params: {
      tenantId: filter.tenantId || undefined,
      status: filter.status || undefined,
    },
  }));
}

export async function listTerminationParticipants(): Promise<ParticipantDefinition[]> {
  return data(await apiClient.get<ApiResponse<ParticipantDefinition[]>>('/console/tenant-terminations/participants'));
}

export async function getTenantTermination(id: number): Promise<TerminationDetail> {
  return data(await apiClient.get<ApiResponse<TerminationDetail>>(`/console/tenant-terminations/${id}`));
}

export async function requestTenantTermination(payload: {
  tenantId: number;
  reason: string;
  requestEvidence: string;
}): Promise<TerminationDetail> {
  return data(await apiClient.post<ApiResponse<TerminationDetail>>('/console/tenant-terminations', payload));
}

export async function refreshTerminationClearance(id: number): Promise<TerminationDetail> {
  return data(await apiClient.post<ApiResponse<TerminationDetail>>(`/console/tenant-terminations/${id}/refresh-clearance`));
}

export async function approveTenantTermination(id: number, opinion: string): Promise<TerminationDetail> {
  return data(await apiClient.post<ApiResponse<TerminationDetail>>(`/console/tenant-terminations/${id}/approve`, { opinion }));
}

export async function effectTenantTermination(id: number): Promise<TerminationDetail> {
  return data(await apiClient.post<ApiResponse<TerminationDetail>>(`/console/tenant-terminations/${id}/effect`));
}
