import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export type ChannelHealthState = 'HEALTHY' | 'DEGRADED' | 'FAILED' | 'MAINTENANCE' | 'PAUSED' | string;
export type ChannelPoolMode = 'WEIGHTED' | 'PRIMARY_BACKUP';

export interface ChannelHealthMonitorRow {
  channelId: number;
  channelName: string;
  protocol: string;
  operator: string;
  status: string;
  healthState: ChannelHealthState;
  timeoutRate: string | number | null;
  failureRate: string | number | null;
  averageLatencyMs: number;
  reasonCode: string | null;
  candidateEligible: boolean;
  candidateReasonCode: string;
  eventCount: number;
  pauseReason: string | null;
  pausedBy: string | null;
  pausedAt: string | null;
}

export interface ChannelHealthObservationRequest {
  connected: boolean;
  timeoutRate: string;
  failureRate: string;
  averageLatencyMs: number;
  reasonCode: string;
}

export interface ChannelPauseRequest {
  trigger: 'MANUAL' | 'HEALTH' | 'COMPLAINT' | 'RATIO';
  actor?: string;
  reason: string;
}

export interface ChannelCandidateDecision {
  channelId: number;
  eligible: boolean;
  reasonCode: string;
}

export interface ChannelPoolMember {
  channelId: number;
  weight: number;
  primaryMember: boolean;
  enabled: boolean;
}

export interface ChannelPool {
  id: number;
  name: string;
  mode: ChannelPoolMode;
  version: number;
  status: string;
  members: ChannelPoolMember[];
}

export interface ChannelPoolRequest {
  name: string;
  mode: ChannelPoolMode;
  expectedVersion: number | null;
  members: ChannelPoolMember[];
}

export interface DispatchRecoveryInventoryRow {
  taskId: number;
  messageId: string;
  tenantId: number;
  channelId: number | null;
  channelName: string | null;
  channelStatus: string | null;
  sendStatus: string;
  outboxState: string | null;
  outboxErrorCode: string | null;
  recoveryState: 'MIGRATABLE' | 'RETRYABLE' | 'UNCERTAIN' | 'OBSERVE' | string;
}

export interface DispatchRecoveryResult {
  originalTaskId: number;
  newTaskId: number | null;
  action: string;
  channelId: number | null;
}

export interface ChannelRecoveryTestResult {
  channelId: number;
  success: boolean;
  state: string;
}

export async function listChannelHealthMonitor(): Promise<ChannelHealthMonitorRow[]> {
  const res = await apiClient.get<ApiResponse<ChannelHealthMonitorRow[]>>('/console/channel-health/monitor');
  return res.data.data;
}

export async function recordChannelObservation(
  channelId: number,
  request: ChannelHealthObservationRequest,
): Promise<ChannelHealthMonitorRow> {
  const res = await apiClient.post<ApiResponse<ChannelHealthMonitorRow>>(
    `/console/channel-health/channels/${channelId}/observations`,
    request,
  );
  return res.data.data;
}

export async function pauseChannel(channelId: number, request: ChannelPauseRequest): Promise<ChannelHealthMonitorRow> {
  const res = await apiClient.post<ApiResponse<ChannelHealthMonitorRow>>(
    `/console/channel-health/channels/${channelId}/pause`,
    request,
  );
  return res.data.data;
}

export async function startChannelMaintenance(
  channelId: number,
  request: ChannelPauseRequest,
): Promise<ChannelHealthMonitorRow> {
  const res = await apiClient.post<ApiResponse<ChannelHealthMonitorRow>>(
    `/console/channel-health/channels/${channelId}/maintenance/start`,
    request,
  );
  return res.data.data;
}

export async function endChannelMaintenance(
  channelId: number,
  request: ChannelPauseRequest,
): Promise<ChannelHealthMonitorRow> {
  const res = await apiClient.post<ApiResponse<ChannelHealthMonitorRow>>(
    `/console/channel-health/channels/${channelId}/maintenance/end`,
    request,
  );
  return res.data.data;
}

export async function getChannelCandidate(channelId: number): Promise<ChannelCandidateDecision> {
  const res = await apiClient.get<ApiResponse<ChannelCandidateDecision>>(
    `/console/channel-health/channels/${channelId}/candidate`,
  );
  return res.data.data;
}

export async function listChannelPools(): Promise<ChannelPool[]> {
  const res = await apiClient.get<ApiResponse<ChannelPool[]>>('/console/channel-health/pools');
  return res.data.data;
}

export async function saveChannelPool(request: ChannelPoolRequest, id?: number): Promise<ChannelPool> {
  const endpoint = id == null ? '/console/channel-health/pools' : `/console/channel-health/pools/${id}`;
  const res = id == null
    ? await apiClient.post<ApiResponse<ChannelPool>>(endpoint, request)
    : await apiClient.put<ApiResponse<ChannelPool>>(endpoint, request);
  return res.data.data;
}

export async function listDispatchRecoveryInventory(): Promise<DispatchRecoveryInventoryRow[]> {
  const res = await apiClient.get<ApiResponse<DispatchRecoveryInventoryRow[]>>('/console/dispatch-recovery/inventory');
  return res.data.data;
}

export async function migrateDispatchTask(taskId: number, evidence: string): Promise<DispatchRecoveryResult> {
  const res = await apiClient.post<ApiResponse<DispatchRecoveryResult>>(
    `/console/dispatch-recovery/tasks/${taskId}/migrate`,
    { evidence },
  );
  return res.data.data;
}

export async function retryDispatchTask(taskId: number, evidence: string): Promise<DispatchRecoveryResult> {
  const res = await apiClient.post<ApiResponse<DispatchRecoveryResult>>(
    `/console/dispatch-recovery/tasks/${taskId}/retry`,
    { evidence },
  );
  return res.data.data;
}

export async function recordRecoveryTest(
  channelId: number,
  success: boolean,
  evidence: string,
): Promise<ChannelRecoveryTestResult> {
  const res = await apiClient.post<ApiResponse<ChannelRecoveryTestResult>>(
    `/console/dispatch-recovery/channels/${channelId}/recovery-tests`,
    { success, evidence },
  );
  return res.data.data;
}

export async function resumeRecoveredChannel(channelId: number, evidence: string): Promise<ChannelRecoveryTestResult> {
  const res = await apiClient.post<ApiResponse<ChannelRecoveryTestResult>>(
    `/console/dispatch-recovery/channels/${channelId}/resume`,
    { evidence },
  );
  return res.data.data;
}
