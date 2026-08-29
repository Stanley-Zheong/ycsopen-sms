import { apiClient } from './client';
import type { ApiResponse, LoginResponse } from '@/types/api';

export async function login(username: string, password: string): Promise<LoginResponse> {
  const res = await apiClient.post<ApiResponse<LoginResponse>>('/console/auth/login', {
    username,
    password,
  });
  return res.data.data;
}
