import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export const REVIEW_HISTORY_PERMISSIONS = {
  menu: 'review-history:menu',
  read: 'review-history:read',
} as const;

export interface ResourceReviewHistoryFilters {
  resourceType: string;
  tenantId: string;
  decisionState: string;
  actor: string;
  riskLevel: string;
  keyword: string;
  createdFrom: string;
  createdTo: string;
  page: number;
  pageSize: number;
}

export interface ResourceReviewHistoryItem {
  decisionId: string;
  resourceType: string;
  resourceId: number;
  resourceCode: string;
  resourceVersion: string;
  tenantId: number;
  decisionState: string;
  actor: string;
  reason: string;
  riskLevel: string | null;
  evidenceRef: string | null;
  submittedSnapshot: string;
  lifecycleLink: string;
  createdAt: string;
}

function params(filters: ResourceReviewHistoryFilters): string {
  const search = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    const text = String(value).trim();
    if (text) search.set(key, text);
  });
  const query = search.toString();
  return query ? `?${query}` : '';
}

export async function listResourceReviewHistory(filters: ResourceReviewHistoryFilters): Promise<ResourceReviewHistoryItem[]> {
  const res = await apiClient.get<ApiResponse<ResourceReviewHistoryItem[]>>(`/console/review-history${params(filters)}`);
  return res.data.data;
}

export async function getResourceReviewHistoryDetail(decisionId: string): Promise<ResourceReviewHistoryItem> {
  const res = await apiClient.get<ApiResponse<ResourceReviewHistoryItem>>(`/console/review-history/detail?decisionId=${encodeURIComponent(decisionId)}`);
  return res.data.data;
}
