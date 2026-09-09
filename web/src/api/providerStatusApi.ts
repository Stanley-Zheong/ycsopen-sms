import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export const PROVIDER_STATUS_PERMISSIONS = {
  menu: 'provider-status:menu',
  read: 'provider-status:read',
  write: 'provider-status:write',
  import: 'provider-status:import',
  export: 'provider-status:export',
} as const;

export interface MappingRow {
  providerName: string;
  protocol: string;
  providerCode: string;
  platformCategory: string;
  finalState: boolean;
  billable: boolean;
  retryable: boolean;
  severity: string;
  advice: string;
}

export interface ImportRequest {
  versionNo: string;
  sourceName: string;
  effectiveAt: string | null;
  rows: MappingRow[];
}

export interface ImportResponse {
  versionNo: string;
  success: number;
  failed: number;
  errors: string[];
}

export interface VersionRow {
  id: number;
  versionNo: string;
  status: string;
  sourceName: string;
  effectiveAt: string;
  conflictCount: number;
  actor: string;
  createdAt: string;
}

export interface NormalizedStatus extends MappingRow {
  versionNo: string | null;
  source: string;
}

export interface ExportResponse {
  requestId: string;
  matchedRows: number;
  status: string;
}

function data<T>(res: { data: ApiResponse<T> }): T {
  return res.data.data;
}

export async function listStatusVersions(): Promise<VersionRow[]> {
  return data(await apiClient.get<ApiResponse<VersionRow[]>>('/console/provider-status/versions'));
}

export async function listStatusMappings(): Promise<MappingRow[]> {
  return data(await apiClient.get<ApiResponse<MappingRow[]>>('/console/provider-status/mappings'));
}

export async function importStatusMappings(payload: ImportRequest): Promise<ImportResponse> {
  return data(await apiClient.post<ApiResponse<ImportResponse>>('/console/provider-status/import', payload));
}

export async function normalizeStatus(providerName: string, protocol: string, providerCode: string): Promise<NormalizedStatus> {
  return data(await apiClient.get<ApiResponse<NormalizedStatus>>('/console/provider-status/normalize', {
    params: { providerName, protocol, providerCode },
  }));
}

export async function requestStatusExport(providerName = '', protocol = ''): Promise<ExportResponse> {
  return data(await apiClient.post<ApiResponse<ExportResponse>>('/console/provider-status/export-request', null, {
    params: { providerName, protocol },
  }));
}
