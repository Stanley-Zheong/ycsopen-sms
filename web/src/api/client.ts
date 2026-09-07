import axios from 'axios';
import { getActiveAccessToken, useAuthStore } from '@/store/authStore';
import { INTERNAL_ERROR_EVENT, type InternalErrorDetail } from '@/components/common/InternalErrorNotice';

/** 统一 axios 实例：自动带上登录态 token；401 时清空本地登录态并跳转登录页。 */
export const apiClient = axios.create({ baseURL: '/api/v1' });

export function mutationErrorMessage(error: unknown, fallback: string): string {
  return axios.isAxiosError(error) && error.response?.status === 403 ? '无权执行此操作' : fallback;
}

apiClient.interceptors.request.use((config) => {
  const token = getActiveAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  } else {
    delete config.headers.Authorization;
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      useAuthStore.getState().logout();
    }
    if (error.response?.status === 500 && typeof window !== 'undefined') {
      const detail: InternalErrorDetail = {
        traceId: error.response.data?.traceId ?? error.response.headers?.['x-correlation-id'] ?? null,
      };
      window.dispatchEvent(new CustomEvent<InternalErrorDetail>(INTERNAL_ERROR_EVENT, { detail }));
    }
    return Promise.reject(error);
  },
);
