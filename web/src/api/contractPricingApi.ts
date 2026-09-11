import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export interface ContractOverview {
  tenantId: number;
  tenantState: string;
  billingMode: string | null;
  priceBookVersion: string | null;
  contractNo: string | null;
  signedAt: string | null;
  attachmentRef: string | null;
  creditLimitMil: number | null;
  billingPeriod: string | null;
  contractStatus: string;
  approvedBy: string | null;
}

export interface ContractRow {
  tenantId: number;
  billingMode: string;
  priceBookVersion: string;
  contractNo: string;
  signedAt: string;
  attachmentRef: string;
  creditLimitMil: number | null;
  billingPeriod: string | null;
  contractStatus: string;
  approvedBy: string;
}

function data<T>(res: { data: ApiResponse<T> }): T {
  return res.data.data;
}

export async function getContractOverview(tenantId: number): Promise<ContractOverview> {
  return data(await apiClient.get<ApiResponse<ContractOverview>>(`/console/contracts/tenants/${tenantId}/overview`));
}

export async function approveContract(
  tenantId: number,
  input: {
    billingMode: string;
    priceBookVersion: string;
    contractNo: string;
    signedAt: string;
    attachmentRef: string;
    creditLimitMil: number | null;
    billingPeriod: string | null;
  },
): Promise<ContractRow> {
  return data(await apiClient.post<ApiResponse<ContractRow>>(`/console/contracts/tenants/${tenantId}`, input));
}
