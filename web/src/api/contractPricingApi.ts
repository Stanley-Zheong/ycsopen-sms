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

export interface TrialCandidate {
  tenantId: number;
  tenantNo: string;
  shortName: string;
  fullName: string;
  salesOwner: string | null;
  industry: string | null;
  configurationSnapshotVersion: string;
  lifecycleStatus: string;
  trialStatus: string;
  trialStartAt: string;
  trialEndAt: string;
  remainingDays: number;
  quotaUsed: number;
  quotaTotal: number;
  messageCount: number;
  successCount: number;
  successRate: number | null;
  complaintCount: number;
  complaintRate: number | null;
  statisticsAt: string;
  dataQuality: 'COMPLETE' | 'INCOMPLETE' | 'NO_DATA';
  sourceRegistry: string;
  conversionEligible: boolean;
  ineligibilityReasons: string[];
}

export interface DailyTrend {
  date: string;
  messageCount: number;
  successCount: number;
  failureCount: number;
  complaintCount: number;
}

export interface StatusCount {
  status: string;
  count: number;
}

export interface ComplaintDetail {
  id: number;
  source: string;
  messageId: string | null;
  summary: string | null;
  status: string;
  createdAt: string;
}

export interface TrialAnalysis {
  tenant: TrialCandidate;
  periodStart: string;
  periodEnd: string;
  trend: DailyTrend[];
  messageStatuses: StatusCount[];
  complaints: ComplaintDetail[];
  sourceRegistry: string;
  statisticsAt: string;
  dataQuality: TrialCandidate['dataQuality'];
}

export interface PriceBookOption {
  priceBookVersion: string;
  productCode: string;
  unitPriceMil: number;
}

export interface WorkbenchFilters {
  keyword: string;
  salesOwner: string;
  industry: string;
  trialStatus: string;
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

export async function listTrialCandidates(filters: WorkbenchFilters): Promise<TrialCandidate[]> {
  const params = Object.fromEntries(Object.entries(filters).filter(([, value]) => value.trim() !== ''));
  return data(await apiClient.get<ApiResponse<TrialCandidate[]>>('/console/contracts/workbench', { params }));
}

export async function getTrialAnalysis(tenantId: number): Promise<TrialAnalysis> {
  return data(await apiClient.get<ApiResponse<TrialAnalysis>>(
    `/console/contracts/workbench/tenants/${tenantId}/analysis`,
  ));
}

export async function listActivePriceBooks(): Promise<PriceBookOption[]> {
  return data(await apiClient.get<ApiResponse<PriceBookOption[]>>('/console/contracts/price-books'));
}
