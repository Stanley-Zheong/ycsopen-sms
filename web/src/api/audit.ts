import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export const AUDIT_PERMISSIONS = {
  operationsRead: 'audit:operations:read',
  operationsAll: 'audit:operations:all',
  securityEventsRead: 'audit:security-events:read',
  securityEventsAll: 'audit:security-events:all',
  reveal: 'privileged:data:reveal',
  revealApi: 'privileged:data:reveal:api',
} as const;

export type AuditResult = 'SUCCESS' | 'CLIENT_FAILURE' | 'SERVER_FAILURE' | 'DENIED';
export type SecurityEventType = 'UNUSUAL_LOGIN' | 'REPEATED_LOGIN_FAILURE' | 'BULK_EXPORT';

export interface OperationAuditItem {
  id: number;
  actor: string;
  tenantId: number | null;
  operation: string;
  resource: string;
  result: string;
  ipAddress: string;
  traceId: string | null;
  latencyMs: number;
  requestSummary: string;
  occurredAt: string;
}

export interface SecurityEventItem {
  id: number;
  eventType: SecurityEventType;
  actor: string;
  tenantId: number | null;
  result: string;
  ipAddress: string | null;
  traceId: string | null;
  summary: string;
  detectedAt: string;
}

export interface PagedResult<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
}

export interface OperationAuditFilters {
  actor?: string;
  operation?: string;
  result?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export interface SecurityEventFilters {
  eventType?: SecurityEventType | '';
  actor?: string;
  result?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export type RevealPurpose = 'CUSTOMER_SUPPORT' | 'SECURITY_INVESTIGATION' | 'COMPLIANCE_REVIEW';

export interface RevealResult {
  value: string;
  expiresAt: string;
  auditId: number;
}

function presentParams<T extends object>(filters: T): Record<string, unknown> {
  return Object.fromEntries(Object.entries(filters).filter(([, value]) => value !== '' && value !== undefined));
}

function withIsoTime<T extends { from?: string; to?: string }>(filters: T): T {
  const toInstant = (value: string | undefined) => value ? new Date(value).toISOString() : value;
  return { ...filters, from: toInstant(filters.from), to: toInstant(filters.to) };
}

export async function getOperationAudits(filters: OperationAuditFilters): Promise<PagedResult<OperationAuditItem>> {
  const response = await apiClient.get<ApiResponse<PagedResult<OperationAuditItem>>>('/console/operation-audits', {
    params: presentParams(withIsoTime(filters)),
  });
  return response.data.data;
}

export async function getSecurityEvents(filters: SecurityEventFilters): Promise<PagedResult<SecurityEventItem>> {
  const response = await apiClient.get<ApiResponse<PagedResult<SecurityEventItem>>>('/console/security-events', {
    params: presentParams(withIsoTime(filters)),
  });
  return response.data.data;
}

export async function revealPlatformAccountPhone(userId: number, purpose: RevealPurpose): Promise<RevealResult> {
  const response = await apiClient.post<ApiResponse<RevealResult>>(
    `/console/platform-accounts/${userId}/phone/reveal`,
    { purpose },
  );
  return response.data.data;
}
