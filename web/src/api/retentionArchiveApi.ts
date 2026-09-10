import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export const RETENTION_ARCHIVE_PERMISSIONS = {
  menu: 'retention-archive:menu',
  read: 'retention-archive:read',
  write: 'retention-archive:write',
  verify: 'retention-archive:verify',
  restore: 'retention-archive:restore',
  export: 'retention-archive:export',
} as const;

export interface ArchivePolicy {
  id: number;
  dataDomain: string;
  sourceTable: string;
  retentionDays: number;
  hotMonths: number;
  partitionUnit: string;
  legalHoldUntil: string | null;
  encryptionRequired: boolean;
  status: string;
  updatedBy: string | null;
  updatedAt: string | null;
}

export interface ArchiveManifest {
  id: number;
  policyId: number;
  dataDomain: string;
  sourceTable: string;
  partitionKey: string;
  tenantId: number | null;
  archiveStatus: string;
  rowCount: number;
  sourceIdentityJson: string;
  manifestJson: string;
  checksumSha256: string;
  encryptionKeyVersion: string;
  retentionUntil: string;
  legalHoldUntil: string | null;
  deletionEligible: boolean;
  failureReason: string | null;
  createdBy: string | null;
  createdAt: string;
  verifiedAt: string | null;
  restoredAt: string | null;
  exportedTaskId: number | null;
}

export interface RestoreJob {
  id: number;
  manifestId: number;
  requestType: string;
  status: string;
  requestedBy: string;
  resultMessage: string;
  restoredRecordCount: number;
  exportTaskId: number | null;
  createdAt: string;
  completedAt: string | null;
}

function data<T>(res: { data: ApiResponse<T> }): T {
  return res.data.data;
}

export async function listArchivePolicies(): Promise<ArchivePolicy[]> {
  return data(await apiClient.get<ApiResponse<ArchivePolicy[]>>('/console/archive/policies'));
}

export async function saveArchivePolicy(dataDomain: string, payload: {
  retentionDays: number;
  hotMonths: number;
  legalHoldUntil?: string | null;
}): Promise<ArchivePolicy> {
  return data(await apiClient.put<ApiResponse<ArchivePolicy>>(`/console/archive/policies/${dataDomain}`, payload));
}

export async function listArchiveManifests(filter: { dataDomain?: string; status?: string; tenantId?: string } = {}): Promise<ArchiveManifest[]> {
  return data(await apiClient.get<ApiResponse<ArchiveManifest[]>>('/console/archive/manifests', {
    params: {
      dataDomain: filter.dataDomain || undefined,
      status: filter.status || undefined,
      tenantId: filter.tenantId || undefined,
    },
  }));
}

export async function scanArchive(dataDomain: string, tenantId?: string): Promise<ArchiveManifest> {
  return data(await apiClient.post<ApiResponse<ArchiveManifest>>('/console/archive/manifests/scan', {
    dataDomain,
    tenantId: tenantId ? Number(tenantId) : null,
  }));
}

export async function verifyArchiveManifest(id: number): Promise<ArchiveManifest> {
  return data(await apiClient.post<ApiResponse<ArchiveManifest>>(`/console/archive/manifests/${id}/verify`));
}

export async function restoreArchiveManifest(id: number): Promise<RestoreJob> {
  return data(await apiClient.post<ApiResponse<RestoreJob>>(`/console/archive/manifests/${id}/restore`));
}

export async function exportArchiveManifest(id: number): Promise<RestoreJob> {
  return data(await apiClient.post<ApiResponse<RestoreJob>>(`/console/archive/manifests/${id}/export`));
}
