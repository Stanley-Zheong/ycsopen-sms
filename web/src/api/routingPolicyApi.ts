import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export const ROUTING_POLICY_PERMISSIONS = {
  menu: 'routing-policy:menu',
  read: 'routing-policy:read',
  write: 'routing-policy:write',
  import: 'routing-policy:import',
} as const;

export interface PolicyRule {
  priority: number;
  conditionType: string;
  conditionValue: string;
  targetType: string;
  targetRef: string;
  weight: number;
}

export interface PolicyRuleView extends PolicyRule {
  id: number;
  versionNo: string;
  status: string;
}

export interface PolicyVersion {
  id: number;
  versionNo: string;
  status: string;
  sourceName: string;
  effectiveAt: string;
  actor: string;
  createdAt: string;
}

export interface PolicyImportRequest {
  versionNo: string;
  sourceName: string;
  effectiveAt: string | null;
  rules: PolicyRule[];
}

export interface PolicyImportResponse {
  versionNo: string;
  imported: number;
  status: string;
}

export interface SimulationRequest {
  tenantId: number | null;
  carrier: string;
  prefix: string;
  content: string;
  normalizedCategory: string;
}

export interface RetryPolicy {
  normalizedCategory: string;
  retryable: boolean;
  delaySeconds: number;
  maxAttempts: number;
  status: string;
}

export interface SimulationResult {
  versionNo: string | null;
  matchedRuleId: number | null;
  targetType: string;
  targetRef: string;
  explanation: string;
  circuitStatus: string;
  retryPolicy: RetryPolicy;
}

export interface CircuitState {
  id: number;
  channelCode: string;
  status: string;
  failureCount: number;
  successCount: number;
  latencyMs: number;
  history: string;
}

function data<T>(res: { data: ApiResponse<T> }): T {
  return res.data.data;
}

export async function listRoutingVersions(): Promise<PolicyVersion[]> {
  return data(await apiClient.get<ApiResponse<PolicyVersion[]>>('/console/routing-policy/versions'));
}

export async function listRoutingRules(): Promise<PolicyRuleView[]> {
  return data(await apiClient.get<ApiResponse<PolicyRuleView[]>>('/console/routing-policy/rules'));
}

export async function importRoutingPolicy(payload: PolicyImportRequest): Promise<PolicyImportResponse> {
  return data(await apiClient.post<ApiResponse<PolicyImportResponse>>('/console/routing-policy/import', payload));
}

export async function simulateRouting(payload: SimulationRequest): Promise<SimulationResult> {
  return data(await apiClient.post<ApiResponse<SimulationResult>>('/console/routing-policy/simulate', payload));
}

export async function listCircuitStates(): Promise<CircuitState[]> {
  return data(await apiClient.get<ApiResponse<CircuitState[]>>('/console/routing-policy/circuits'));
}

export async function recordCircuit(channelCode: string, success: boolean, latencyMs: number): Promise<CircuitState> {
  return data(await apiClient.post<ApiResponse<CircuitState>>(`/console/routing-policy/circuits/${channelCode}/record`, null, {
    params: { success, latencyMs },
  }));
}

export async function saveRetryPolicy(payload: RetryPolicy): Promise<RetryPolicy> {
  return data(await apiClient.post<ApiResponse<RetryPolicy>>('/console/routing-policy/retry', payload));
}

