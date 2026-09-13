import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export const OPERATIONAL_DASHBOARD_PERMISSIONS = {
  menu: 'operational-dashboard:menu',
  read: 'operational-dashboard:read',
  write: 'operational-dashboard:write',
} as const;

export interface MetricSource {
  registry: string;
  formula: string;
  freshnessAt: string | null;
  permissionScope: string;
  formulaVersion: string;
}

export interface PlatformDashboard {
  realtime: { totalUsers: number; todayMessages: number; successRate: number; activeTenants: number; comparisonMessages: number };
  kpi: { todaySend: number; activeTenants: number; successRate: number; todayRevenue: number; formula: string };
  hourlyTrend: Array<{ bucketStart: string; sendCount: number; successCount: number; successRate: number }>;
  tenantRank: Array<{ tenantId: number; sendCount: number; successCount: number; successRate: number }>;
  channelHealth: { normal: number; maintenance: number; abnormal: number };
  financeWarning: { warningCount: number; freshnessAt: string | null };
  source: MetricSource;
}

export interface TenantOperationalOverview {
  tenantId: number;
  balanceMil: number;
  trialStatus: string;
  contractStatus: string;
  todayMessages: number;
  successRate: number;
  serviceStatus: string;
  source: MetricSource;
}

export interface ResourceStatistics {
  resources: Array<{ tenantId: number; signatureId: number | null; templateId: number | null; submitCount: number; successCount: number; rejectedCount: number; freshnessAt: string | null }>;
  channelComparisons: Array<{ tenantId: number; channelId: number | null; sendCount: number; successCount: number; failureCount: number; successRate: number; freshnessAt: string | null }>;
  accessibleColumns: string[];
  source: MetricSource;
  empty: boolean;
  errorState: string;
}

export interface ApiStatus {
  rows: Array<{ component: string; status: string; source: string; freshnessAt: string | null; impact: string; drilldownKey: string }>;
  source: MetricSource;
}

export interface DashboardConfiguration {
  role: string;
  globalCards: boolean;
  tenantCards: boolean;
  refreshMode: string;
  pollingSeconds: number;
  complaintThreshold: string;
  updatedBy: string;
  updatedAt: string | null;
}

export interface DashboardConfigurationCommand {
  role: string;
  globalCards: boolean;
  tenantCards: boolean;
  refreshMode: string;
  pollingSeconds: number;
  complaintThreshold: string;
}

function data<T>(res: { data: ApiResponse<T> }): T {
  return res.data.data;
}

export async function getPlatformDashboard(): Promise<PlatformDashboard> {
  return data(await apiClient.get<ApiResponse<PlatformDashboard>>('/console/operational-dashboards/platform'));
}

export async function getTenantOperationalOverview(tenantId?: number): Promise<TenantOperationalOverview> {
  return data(await apiClient.get<ApiResponse<TenantOperationalOverview>>('/console/operational-dashboards/tenant-overview', {
    params: { tenantId },
  }));
}

export async function getResourceStatistics(tenantId?: number): Promise<ResourceStatistics> {
  return data(await apiClient.get<ApiResponse<ResourceStatistics>>('/console/operational-dashboards/resource-statistics', {
    params: { tenantId },
  }));
}

export async function getApiStatus(): Promise<ApiStatus> {
  return data(await apiClient.get<ApiResponse<ApiStatus>>('/console/operational-dashboards/api-status'));
}

export async function getDashboardConfiguration(role: string): Promise<DashboardConfiguration> {
  return data(await apiClient.get<ApiResponse<DashboardConfiguration>>('/console/operational-dashboards/configuration', {
    params: { role },
  }));
}

export async function saveDashboardConfiguration(command: DashboardConfigurationCommand): Promise<DashboardConfiguration> {
  return data(await apiClient.post<ApiResponse<DashboardConfiguration>>('/console/operational-dashboards/configuration', command));
}
