import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export const ALERT_ENGINE_PERMISSIONS = {
  menu: 'alert-engine:menu',
  read: 'alert-engine:read',
  write: 'alert-engine:write',
};

export interface AlertDashboard {
  totalCount: number;
  activeCount: number;
  severeCount: number;
  resolvedCount: number;
}

export interface AlertRule {
  id: number;
  ruleName: string;
  ruleType: string;
  metricName: string;
  metricSource: string;
  thresholdValue: number;
  comparisonOp: string;
  durationMinutes: number;
  severity: string;
  notifyChannels: string;
  notificationTargets: string;
  sourceScope: string;
  status: string;
  updatedAt: string | null;
}

export interface AlertRecord {
  id: number;
  ruleId: number;
  title: string;
  content: string | null;
  metricValue: number;
  status: string;
  severity: string;
  sourceModule: string;
  sourceKey: string;
  impactScope: string | null;
  triggeredAt: string | null;
  acknowledgedAt: string | null;
  acknowledgedBy: string | null;
  resolvedAt: string | null;
  resolvedBy: string | null;
  resolutionNote: string | null;
  deliveryState: string;
  mutedUntil: string | null;
}

export interface DeliveryAttempt {
  id: number;
  alertRecordId: number;
  channel: string;
  targetSnapshot: string;
  providerResult: string;
  retryCount: number;
  status: string;
  failureReason: string | null;
  attemptedAt: string | null;
}

function data<T>(res: { data: ApiResponse<T> }): T {
  return res.data.data;
}

export async function getAlertDashboard(): Promise<AlertDashboard> {
  return data(await apiClient.get<ApiResponse<AlertDashboard>>('/console/alerts/dashboard'));
}

export async function listAlertRules(): Promise<AlertRule[]> {
  return data(await apiClient.get<ApiResponse<AlertRule[]>>('/console/alerts/rules'));
}

export async function saveAlertRule(input: Record<string, unknown>): Promise<AlertRule> {
  return data(await apiClient.post<ApiResponse<AlertRule>>('/console/alerts/rules', input));
}

export async function listAlertHistory(filter: { status?: string; severity?: string }): Promise<AlertRecord[]> {
  const params = Object.fromEntries(Object.entries(filter).filter(([, value]) => value !== undefined && value !== ''));
  return data(await apiClient.get<ApiResponse<AlertRecord[]>>('/console/alerts/history', { params }));
}

export async function listAlertDeliveries(alertId?: number): Promise<DeliveryAttempt[]> {
  return data(await apiClient.get<ApiResponse<DeliveryAttempt[]>>('/console/alerts/deliveries', { params: alertId ? { alertId } : {} }));
}

export async function evaluateAlertSource(input: Record<string, unknown>): Promise<{ alerts: AlertRecord[] }> {
  return data(await apiClient.post<ApiResponse<{ alerts: AlertRecord[] }>>('/console/alerts/evaluate', input));
}

export async function acknowledgeAlert(alertId: number): Promise<AlertRecord> {
  return data(await apiClient.post<ApiResponse<AlertRecord>>(`/console/alerts/${alertId}/acknowledge`));
}

export async function resolveAlert(alertId: number, reason: string): Promise<AlertRecord> {
  return data(await apiClient.post<ApiResponse<AlertRecord>>(`/console/alerts/${alertId}/resolve`, { reason }));
}

export async function muteAlert(alertId: number, minutes: number, reason: string): Promise<{ id: number; scope: string; reason: string; mutedBy: string; mutedUntil: string }> {
  return data(await apiClient.post<ApiResponse<{ id: number; scope: string; reason: string; mutedBy: string; mutedUntil: string }>>(
    `/console/alerts/${alertId}/mute`,
    { minutes, reason },
  ));
}
