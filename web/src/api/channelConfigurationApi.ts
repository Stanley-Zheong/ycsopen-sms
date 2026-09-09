import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export type ChannelProtocol = 'CMPP' | 'SGIP' | 'SMGP' | 'HTTP';
export type ChannelOperator = 'MOBILE' | 'UNICOM' | 'TELECOM' | 'VIRTUAL' | 'INTERNATIONAL';
export type ChannelStatus = 'NORMAL' | 'MAINTENANCE' | 'ABNORMAL' | 'PAUSED' | 'OFFLINE';
export type ActivationStatus = 'APPLIED' | 'REJECTED' | 'STALE' | 'RETRYABLE';

export interface ChannelConfiguration {
  id: number;
  name: string;
  protocol: ChannelProtocol;
  operator: ChannelOperator;
  host: string;
  port: number;
  account: string;
  password: string;
  spId: string | null;
  serviceId: string | null;
  srcId: string | null;
  maxConnections: number;
  windowSize: number;
  tpsLimit: number;
  price: string;
  priority: number;
  activeWindow: string | null;
  extraConfig: Record<string, unknown>;
  availability: string;
  status: ChannelStatus;
  effectiveVersion: number | null;
  configurationVersion: number;
  updatedAt: string | null;
}

export interface ChannelConfigurationRequest {
  name: string;
  protocol: ChannelProtocol;
  operator: ChannelOperator;
  host: string;
  port: number;
  account: string;
  password: string;
  spId: string | null;
  serviceId: string | null;
  srcId: string | null;
  maxConnections: number;
  windowSize: number;
  tpsLimit: number;
  price: string;
  priority: number | null;
  activeWindow: string | null;
  extraConfig: Record<string, unknown> | null;
  availability: string;
}

export interface ChannelConnectivityResult {
  passed: boolean;
  reasonCode: string;
  retryable: boolean;
}

export interface ChannelActivationResult {
  channelId: number;
  requestedVersion: number | null;
  effectiveVersion: number | null;
  resultCode: ActivationStatus | string;
  retryable: boolean;
  safeReason: string;
}

export interface ChannelDependency {
  source: string;
  referenceId: string;
  state: string;
  unresolved: boolean;
  destinationChannelId: number | null;
}

export interface ChannelDependencyView {
  channelId: number;
  unresolvedCount: number;
  dependencies: ChannelDependency[];
}

export interface ChannelDependencyMigrationRequest {
  items: Array<{
    source: string;
    referenceId: string;
    destinationChannelId: number | null;
  }>;
}

export interface ChannelOfflineResult {
  channelId: number;
  status: ChannelStatus;
  changed: boolean;
  unresolvedDependencies: ChannelDependency[];
}

export async function listChannelConfigurations(): Promise<ChannelConfiguration[]> {
  const res = await apiClient.get<ApiResponse<ChannelConfiguration[]>>('/console/channels/configuration');
  return res.data.data;
}

export async function saveChannelConfiguration(
  request: ChannelConfigurationRequest,
  id?: number,
): Promise<ChannelConfiguration> {
  const endpoint = id == null ? '/console/channels/configuration' : `/console/channels/configuration/${id}`;
  const res = id == null
    ? await apiClient.post<ApiResponse<ChannelConfiguration>>(endpoint, request)
    : await apiClient.put<ApiResponse<ChannelConfiguration>>(endpoint, request);
  return res.data.data;
}

export async function testChannelConnectivity(id: number): Promise<ChannelConnectivityResult> {
  const res = await apiClient.post<ApiResponse<ChannelConnectivityResult>>(
    `/console/channels/configuration/${id}/connectivity-test`,
  );
  return res.data.data;
}

export async function activateChannelConfiguration(
  id: number,
  expectedEffectiveVersion: number | null,
): Promise<ChannelActivationResult> {
  const res = await apiClient.post<ApiResponse<ChannelActivationResult>>(
    `/console/channels/configuration/${id}/activate`,
    { expectedEffectiveVersion },
  );
  return res.data.data;
}

export async function retryChannelActivation(id: number, version: number): Promise<ChannelActivationResult> {
  const res = await apiClient.post<ApiResponse<ChannelActivationResult>>(
    `/console/channels/configuration/${id}/versions/${version}/retry`,
  );
  return res.data.data;
}

export async function listChannelDependencies(id: number): Promise<ChannelDependencyView> {
  const res = await apiClient.get<ApiResponse<ChannelDependency[]>>(
    `/console/channels/configuration/${id}/dependencies`,
  );
  return {
    channelId: id,
    unresolvedCount: res.data.data.filter((item) => item.unresolved).length,
    dependencies: res.data.data,
  };
}

export async function migrateChannelDependencies(
  id: number,
  request: ChannelDependencyMigrationRequest,
): Promise<ChannelDependencyView> {
  const res = await apiClient.post<ApiResponse<ChannelDependency[]>>(
    `/console/channels/configuration/${id}/dependencies/migrate`,
    request,
  );
  return {
    channelId: id,
    unresolvedCount: res.data.data.filter((item) => item.unresolved).length,
    dependencies: res.data.data,
  };
}

export async function offlineChannel(id: number): Promise<ChannelOfflineResult> {
  const res = await apiClient.post<ApiResponse<ChannelOfflineResult>>(
    `/console/channels/configuration/${id}/offline`,
  );
  return res.data.data;
}
