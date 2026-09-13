import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export const CUSTOM_REPORT_PERMISSIONS = {
  menu: 'custom-report:menu',
  read: 'custom-report:read',
  write: 'custom-report:write',
};

export interface CustomReportCapability {
  metricCode: string;
  metricName: string;
  dimensions: string[];
  measures: string[];
  formula: string;
  freshnessRule: string;
  permissionScope: string;
  formulaVersion: string;
}

export interface CustomReportCommand {
  reportName: string;
  metricCode: string;
  dimensions: string[];
  measures: string[];
  tenantId?: number | null;
  channelId?: number | null;
  messageType?: string | null;
  province?: string | null;
  startTime?: string | null;
  endTime?: string | null;
  roleScope: 'PLATFORM' | 'TENANT';
}

export interface CustomReportDataRow {
  values: Record<string, string | number | null>;
  drilldownKey: string;
  qualityState: string;
  freshnessAt: string | null;
}

export interface CustomReportPreview {
  metricCode: string;
  metricName: string;
  formula: string;
  formulaVersion: string;
  freshnessRule: string;
  freshnessAt: string | null;
  qualityState: string;
  accessibleColumns: string[];
  truncated: boolean;
  rows: CustomReportDataRow[];
}

export interface CustomReportDefinition {
  id: number;
  reportName: string;
  metricCode: string;
  tenantId: number | null;
  roleScope: string;
  definitionSnapshot: string;
  status: string;
  createdBy: string;
  createdAt: string | null;
}

export interface CustomReportExportRequest {
  id: number;
  reportDefinitionId: number;
  definitionSnapshot: string;
  status: string;
  requestedBy: string;
  requestedAt: string | null;
}

function data<T>(res: { data: ApiResponse<T> }): T {
  return res.data.data;
}

export async function listCustomReportCapabilities(): Promise<CustomReportCapability[]> {
  return data(await apiClient.get<ApiResponse<CustomReportCapability[]>>('/console/custom-reports/capabilities'));
}

export async function previewCustomReport(command: CustomReportCommand): Promise<CustomReportPreview> {
  return data(await apiClient.post<ApiResponse<CustomReportPreview>>('/console/custom-reports/preview', command));
}

export async function saveCustomReportDefinition(command: CustomReportCommand): Promise<CustomReportDefinition> {
  return data(await apiClient.post<ApiResponse<CustomReportDefinition>>('/console/custom-reports/definitions', command));
}

export async function listCustomReportDefinitions(): Promise<CustomReportDefinition[]> {
  return data(await apiClient.get<ApiResponse<CustomReportDefinition[]>>('/console/custom-reports/definitions'));
}

export async function requestCustomReportExport(definitionId: number): Promise<CustomReportExportRequest> {
  return data(await apiClient.post<ApiResponse<CustomReportExportRequest>>(`/console/custom-reports/definitions/${definitionId}/export`));
}
