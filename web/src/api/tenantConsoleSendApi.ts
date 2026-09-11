import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export interface TenantConsoleSendRequest {
  submitId: string;
  phoneNumber: string;
  templateId: string;
  signId: string;
  templateParams: Record<string, string>;
  callbackUrl?: string | null;
}

export interface TenantConsoleSendResponse {
  messageId: string;
  status: string;
}

export async function sendTenantConsoleMessage(payload: TenantConsoleSendRequest): Promise<TenantConsoleSendResponse> {
  const res = await apiClient.post<ApiResponse<TenantConsoleSendResponse>>('/console/tenant/send', payload);
  return res.data.data;
}
