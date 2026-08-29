import { apiClient } from './client';
import type { ApiResponse, Channel } from '@/types/api';

export async function listChannels(): Promise<Channel[]> {
  const res = await apiClient.get<ApiResponse<Channel[]>>('/console/channels');
  return res.data.data;
}

/** F-4.7 通道暂停：立即从路由候选中移除，需记录原因与操作人。 */
export async function pauseChannel(id: number, reason: string, operatedBy: string): Promise<Channel> {
  const res = await apiClient.post<ApiResponse<Channel>>(`/console/channels/${id}/pause`, null, {
    params: { reason, operatedBy },
  });
  return res.data.data;
}

export async function resumeChannel(id: number): Promise<Channel> {
  const res = await apiClient.post<ApiResponse<Channel>>(`/console/channels/${id}/resume`);
  return res.data.data;
}
