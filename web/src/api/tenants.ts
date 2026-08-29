import { apiClient } from './client';
import type { ApiResponse, Tenant } from '@/types/api';

export async function approveAndActivateTrial(
  tenantId: number,
  approvedBy: string,
  trialQuota = 500,
  trialDays = 14,
): Promise<Tenant> {
  const res = await apiClient.post<ApiResponse<Tenant>>(
    `/console/tenants/${tenantId}/approve-and-activate-trial`,
    null,
    { params: { approvedBy, trialQuota, trialDays } },
  );
  return res.data.data;
}
