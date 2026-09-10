import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export const SECURE_ASYNC_EXPORT_PERMISSIONS = {
  menu: 'secure-async-export:menu',
  read: 'secure-async-export:read',
  create: 'secure-async-export:create',
  download: 'secure-async-export:download',
  retry: 'secure-async-export:retry',
} as const;

export interface ExportJob {
  id: number;
  requestId: string;
  tenantId: number | null;
  exportType: string;
  producer: string;
  jobName: string;
  createdBy: string;
  format: string;
  status: string;
  progress: number;
  recordCount: number;
  fileSizeBytes: number | null;
  fileSha256: string | null;
  encryptionState: string;
  retryCount: number;
  splitCount: number;
  partialFailureCount: number;
  failureReason: string | null;
  createdAt: string;
  completedAt: string | null;
}

export interface DownloadArtifact {
  id: number;
  requestId: string;
  fileName: string;
  format: string;
  encryptionState: string;
  artifactBase64: string;
  sha256: string;
  sizeBytes: number;
}

function data<T>(res: { data: ApiResponse<T> }): T {
  return res.data.data;
}

export async function listSecureExports(filter: { tenantId?: string; exportType?: string; status?: string } = {}): Promise<ExportJob[]> {
  return data(await apiClient.get<ApiResponse<ExportJob[]>>('/console/exports', {
    params: {
      tenantId: filter.tenantId || undefined,
      exportType: filter.exportType || undefined,
      status: filter.status || undefined,
    },
  }));
}

export async function retrySecureExport(id: number, reason: string): Promise<ExportJob> {
  return data(await apiClient.post<ApiResponse<ExportJob>>(`/console/exports/${id}/retry`, { reason }));
}

export async function downloadSecureExport(id: number): Promise<DownloadArtifact> {
  return data(await apiClient.get<ApiResponse<DownloadArtifact>>(`/console/exports/${id}/download`));
}
