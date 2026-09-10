import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export const SHORTLINK_PERMISSIONS = {
  menu: 'shortlink:menu',
  read: 'shortlink:read',
  write: 'shortlink:write',
  reviewMenu: 'shortlink-review:menu',
  reviewRead: 'shortlink-review:read',
  reviewWrite: 'shortlink-review:write',
} as const;

export interface ShortLinkRow {
  id: number;
  tenantId: number;
  targetUrl: string;
  customDomain: string | null;
  shortCode: string;
  shortUrl: string;
  validUntil: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'EXPIRED' | 'OFFLINE' | 'TAKEN_DOWN';
  clickCount: number;
  targetVersion: number;
  immutableTargetSha256: string;
  automatedResultJson: string;
  screenshotEvidenceRef: string | null;
  domainEvidenceJson: string | null;
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH';
  reviewOpinion: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  offlineReason: string | null;
  offlineAt: string | null;
  lastRecheckAt: string | null;
}

export interface ShortLinkAnalytics {
  uniqueClicks: number;
  regions: Array<{ label: string; count: number }>;
  devices: Array<{ label: string; count: number }>;
}

function data<T>(res: { data: ApiResponse<T> }): T {
  return res.data.data;
}

export async function listTenantShortLinks(): Promise<ShortLinkRow[]> {
  return data(await apiClient.get<ApiResponse<ShortLinkRow[]>>('/console/tenant/shortlinks'));
}

export async function createShortLink(payload: {
  tenantId?: number;
  originalUrl: string;
  customDomain?: string | null;
  validUntil: string;
}): Promise<ShortLinkRow> {
  return data(await apiClient.post<ApiResponse<ShortLinkRow>>('/console/tenant/shortlinks', payload));
}

export async function shortLinkAnalytics(): Promise<ShortLinkAnalytics> {
  return data(await apiClient.get<ApiResponse<ShortLinkAnalytics>>('/console/tenant/shortlinks/analytics'));
}

export async function listShortLinkReview(status = 'PENDING'): Promise<ShortLinkRow[]> {
  return data(await apiClient.get<ApiResponse<ShortLinkRow[]>>('/console/shortlinks/review', { params: { status } }));
}

export async function approveShortLink(id: number, opinion: string): Promise<ShortLinkRow> {
  return data(await apiClient.post<ApiResponse<ShortLinkRow>>(`/console/shortlinks/review/${id}/approve`, { opinion }));
}

export async function rejectShortLink(id: number, opinion: string): Promise<ShortLinkRow> {
  return data(await apiClient.post<ApiResponse<ShortLinkRow>>(`/console/shortlinks/review/${id}/reject`, { opinion }));
}

export async function inspectShortLink(id: number, observedTargetUrl: string): Promise<ShortLinkRow> {
  return data(await apiClient.post<ApiResponse<ShortLinkRow>>(`/console/shortlinks/review/${id}/inspect`, { observedTargetUrl }));
}
