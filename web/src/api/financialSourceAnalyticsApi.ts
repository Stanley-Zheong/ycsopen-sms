import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export interface FinancialAnalyticsFilter {
  startDate: string;
  endDate: string;
  tenantId?: number | null;
  channelId?: number | null;
}

export interface FinancialSummaryRow {
  tenantId: number;
  channelId: number | null;
  periodStart: string;
  periodEnd: string;
  sourceCount: number;
  billableCount: number;
  providerCostMil: number;
  revenueMil: number;
  profitMil: number;
  priceBookVersion: string;
  unitPriceMil: number;
  formulaVersion: string;
  formula: string;
  freshnessAt: string | null;
}

export interface FinancialSourceRow {
  taskId: number;
  messageId: string;
  tenantId: number;
  channelId: number | null;
  sendStatus: string;
  finalStatus: string;
  providerCostMil: number;
  revenueMil: number;
  profitMil: number;
  priceBookVersion: string;
  unitPriceMil: number;
  formulaVersion: string;
  formula: string;
  freshnessAt: string | null;
}

function data<T>(res: { data: ApiResponse<T> }): T {
  return res.data.data;
}

function params(filter: FinancialAnalyticsFilter) {
  return {
    startDate: filter.startDate,
    endDate: filter.endDate,
    tenantId: filter.tenantId || undefined,
    channelId: filter.channelId || undefined,
  };
}

export async function listFinancialSummaries(filter: FinancialAnalyticsFilter): Promise<FinancialSummaryRow[]> {
  return data(await apiClient.get<ApiResponse<FinancialSummaryRow[]>>('/console/financial/analytics', {
    params: params(filter),
  }));
}

export async function listFinancialDrilldown(filter: FinancialAnalyticsFilter): Promise<FinancialSourceRow[]> {
  return data(await apiClient.get<ApiResponse<FinancialSourceRow[]>>('/console/financial/analytics/drilldown', {
    params: params(filter),
  }));
}
