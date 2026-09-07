import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export const SYSTEM_CONFIGURATION_PERMISSIONS = {
  menu: 'system:configuration:menu',
  read: 'system:configuration:read',
  write: 'system:configuration:write',
  activate: 'system:configuration:activate',
} as const;

export type ConfigurationValueType = 'INTEGER' | 'BOOLEAN' | 'SECRET_REFERENCE';
export type ConfigurationVersionStatus = 'DRAFT' | 'ACTIVE' | 'SUPERSEDED' | 'ABANDONED' | 'RELOAD_REJECTED';

export interface ActiveConfiguration {
  version: number;
  checksum: string;
  actorUserId: number | null;
  actor: string | null;
  activatedAt: string | null;
  reloadStatus: string;
  reloadErrorCode: string | null;
}

export interface ConfigurationSetting {
  key: string;
  label: string;
  type: ConfigurationValueType;
  value: string;
  defaultValue: string;
  validation: string;
  sensitive: boolean;
  configured: boolean;
}

export interface ConfigurationDraft {
  version: number;
  baseVersion: number;
  changedKeys: string[];
  reason: string;
  actor: string;
  createdAt: string;
}

export interface ConfigurationHistoryItem {
  version: number;
  sourceVersion: number | null;
  status: ConfigurationVersionStatus;
  changedKeys: string[];
  reason: string;
  actor: string;
  createdAt: string;
  activatedBy: number | null;
  activatedAt: string | null;
  reloadStatus: string;
  reloadErrorCode: string | null;
}

export interface SystemConfigurationView {
  active: ActiveConfiguration;
  settings: ConfigurationSetting[];
  draft: ConfigurationDraft | null;
  history: ConfigurationHistoryItem[];
}

export async function getSystemConfiguration(): Promise<SystemConfigurationView> {
  const response = await apiClient.get<ApiResponse<SystemConfigurationView>>('/console/system-configuration');
  return response.data.data;
}

export async function stageSystemConfiguration(request: {
  expectedActiveVersion: number;
  changes: Record<string, string>;
  reason: string;
}): Promise<number> {
  const response = await apiClient.post<ApiResponse<number>>('/console/system-configuration/versions', request);
  return response.data.data;
}

export async function activateSystemConfiguration(version: number, request: {
  expectedActiveVersion: number;
  reason: string;
}): Promise<{ activeVersion: number; reloadStatus: string }> {
  const response = await apiClient.post<ApiResponse<{ activeVersion: number; reloadStatus: string }>>(
    `/console/system-configuration/versions/${version}/activate`, request,
  );
  return response.data.data;
}

export async function rollbackSystemConfiguration(version: number, request: {
  expectedActiveVersion: number;
  reason: string;
}): Promise<{ activeVersion: number; reloadStatus: string }> {
  const response = await apiClient.post<ApiResponse<{ activeVersion: number; reloadStatus: string }>>(
    `/console/system-configuration/versions/${version}/rollback`, request,
  );
  return response.data.data;
}
