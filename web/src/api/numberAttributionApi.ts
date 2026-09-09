import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export const NUMBER_ATTRIBUTION_PERMISSIONS = {
  menu: 'number-attribution:menu',
  read: 'number-attribution:read',
  write: 'number-attribution:write',
  import: 'number-attribution:import',
  portability: 'number-attribution:portability',
} as const;

export interface PrefixVersionRow {
  id: number;
  versionNo: string;
  updateType: string;
  status: string;
  sourceName: string;
  totalRows: number;
  conflictCount: number;
  actor: string;
  createdAt: string;
  activatedAt: string | null;
}

export interface PrefixRow {
  prefix: string;
  carrier: string;
  province: string;
  city: string;
}

export interface PrefixImportRequest {
  versionNo: string;
  updateType: string;
  sourceName: string;
  rows: PrefixRow[];
}

export interface PrefixImportResponse {
  versionNo: string;
  success: number;
  failed: number;
  errors: string[];
}

export interface AttributionResult {
  mobile: string;
  carrier: string;
  prefixCarrier: string;
  province: string;
  city: string;
  source: string;
  sourceName: string;
  freshnessExpiresAt: string | null;
  providerFailure: boolean;
}

export interface PortabilityRow {
  id: number;
  maskedMobile: string;
  originalCarrier: string;
  currentCarrier: string;
  portedAt: string | null;
  sourceName: string;
  freshnessExpiresAt: string;
  status: string;
  updatedAt: string;
}

export interface PortabilityRequest {
  mobile: string;
  originalCarrier: string;
  currentCarrier: string;
  portedAt: string | null;
  sourceName: string;
  freshnessSeconds: number;
}

function data<T>(res: { data: ApiResponse<T> }): T {
  return res.data.data;
}

export async function listPrefixVersions(): Promise<PrefixVersionRow[]> {
  return data(await apiClient.get<ApiResponse<PrefixVersionRow[]>>('/console/number-attribution/prefixes/versions'));
}

export async function importPrefixes(payload: PrefixImportRequest): Promise<PrefixImportResponse> {
  return data(await apiClient.post<ApiResponse<PrefixImportResponse>>('/console/number-attribution/prefixes/import', payload));
}

export async function lookupAttribution(mobile: string, forceProviderFailure = false): Promise<AttributionResult> {
  return data(await apiClient.get<ApiResponse<AttributionResult>>('/console/number-attribution/lookup', {
    params: { mobile, forceProviderFailure },
  }));
}

export async function listPortabilityRows(): Promise<PortabilityRow[]> {
  return data(await apiClient.get<ApiResponse<PortabilityRow[]>>('/console/number-attribution/portability'));
}

export async function savePortability(payload: PortabilityRequest): Promise<PortabilityRow> {
  return data(await apiClient.post<ApiResponse<PortabilityRow>>('/console/number-attribution/portability', payload));
}
