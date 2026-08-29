import { create } from 'zustand';
import type { LoginResponse } from '@/types/api';

interface AuthState {
  accessToken: string | null;
  userType: LoginResponse['userType'] | null;
  tenantId: number | null;
  setSession: (session: LoginResponse) => void;
  logout: () => void;
}

/** 会话状态：登录后写入，401 或主动登出时清空（见 api/client.ts 拦截器）。 */
export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  userType: null,
  tenantId: null,
  setSession: (session) =>
    set({ accessToken: session.accessToken, userType: session.userType, tenantId: session.tenantId }),
  logout: () => set({ accessToken: null, userType: null, tenantId: null }),
}));

/** 平台方角色 vs 机构方角色，用于路由守卫（对应 PRD 3.1 节角色定义）。 */
export function isPlatformRole(userType: LoginResponse['userType'] | null): boolean {
  return userType === 'ADMIN' || userType === 'OPERATOR' || userType === 'FINANCE';
}
