import { apiClient } from './client';
import type { ApiResponse, ComplaintRatioItem } from '@/types/api';

/** F-11.9：拉取通道 / 机构维度的月度投诉占比排行，month 格式 YYYY-MM，缺省为当月。 */
export async function fetchComplaintRatio(
  dimension: 'channel' | 'tenant',
  month?: string,
): Promise<ComplaintRatioItem[]> {
  const res = await apiClient.get<ApiResponse<ComplaintRatioItem[]>>(
    `/console/dashboard/complaint-ratio/${dimension}`,
    { params: month ? { month } : {} },
  );
  return res.data.data;
}
