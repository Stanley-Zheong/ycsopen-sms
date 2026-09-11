import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export interface RechargeRecord {
  id: number;
  tenantId: number;
  amountMil: number;
  rechargeMethod: string;
  transactionRefMask: string;
  evidenceText: string;
  status: string;
  submitterActor: string;
  reviewerActor: string | null;
  reviewReason: string | null;
  reviewedAt: string | null;
  createdAt: string;
}

function data<T>(res: { data: ApiResponse<T> }): T {
  return res.data.data;
}

export async function submitRecharge(
  tenantId: number,
  input: { amountMil: number; rechargeMethod: string; transactionRef: string; evidenceText: string },
): Promise<RechargeRecord> {
  return data(await apiClient.post<ApiResponse<RechargeRecord>>(`/console/recharges/tenant/${tenantId}`, input));
}

export async function listTenantRecharges(tenantId: number): Promise<RechargeRecord[]> {
  return data(await apiClient.get<ApiResponse<RechargeRecord[]>>(`/console/recharges/tenant/${tenantId}`));
}

export async function listRechargeReviews(status = 'PENDING'): Promise<RechargeRecord[]> {
  return data(await apiClient.get<ApiResponse<RechargeRecord[]>>('/console/recharges/reviews', { params: { status } }));
}

export async function reviewRecharge(
  rechargeId: number,
  input: { approved: boolean; reason: string },
): Promise<RechargeRecord> {
  return data(await apiClient.post<ApiResponse<RechargeRecord>>(`/console/recharges/${rechargeId}/review`, input));
}
