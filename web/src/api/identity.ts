import { apiClient } from './client';
import type { ApiResponse, LoginResponse } from '@/types/api';

export type PlatformUserType = Extract<LoginResponse['userType'], 'ADMIN' | 'OPERATOR' | 'FINANCE'>;
export type PlatformAccountStatus = 'ACTIVE' | 'DISABLED' | 'LOCKED';
export type PermissionResourceType = 'MENU' | 'BUTTON' | 'API' | 'DATA';

export interface PlatformAccount {
  id: number;
  username: string;
  email: string | null;
  realName: string | null;
  maskedPhone: string | null;
  userType: PlatformUserType;
  status: PlatformAccountStatus;
  roleIds: number[];
  validUntil: string | null;
  lastLoginAt: string | null;
  createdBy: string | null;
  createdAt: string;
}

export interface SavePlatformAccountRequest {
  username: string;
  password?: string;
  phone: string;
  email: string;
  realName: string;
  userType: PlatformUserType;
  validUntil: string | null;
  roleIds: number[];
}

export interface PlatformRole {
  id: number;
  code: string;
  name: string;
  description: string;
  status: 'ACTIVE' | 'DISABLED';
  userCount: number;
  permissionIds: number[];
}

export interface PermissionItem {
  id: number;
  code: string;
  name: string;
  resourceType: PermissionResourceType;
  resourcePath: string | null;
  httpMethod: string | null;
  parentId: number | null;
  sortOrder: number;
  status: 'ACTIVE' | 'DISABLED';
}

export interface AccountOverview {
  id: number;
  username: string;
  userType: PlatformUserType;
  roleNames: string[];
  permissions: Array<{ code: string; resourceType: PermissionResourceType }>;
  lastLoginAt: string | null;
  lastLoginIp: string | null;
}

export interface LoginHistoryItem {
  id: number;
  userId: number | null;
  username: string;
  loginIp: string;
  userAgent: string | null;
  outcome: string;
  occurredAt: string;
}

export interface LoginHistoryPage {
  items: LoginHistoryItem[];
  page: number;
  size: number;
  totalElements: number;
}

export const IDENTITY_PERMISSIONS = {
  identityMenu: 'identity:menu',
  usersRead: 'identity:accounts:read',
  rolesRead: 'identity:roles:read',
  historyRead: 'identity:history:read',
  historyAll: 'identity:history:all',
  createUser: 'identity:accounts:create',
  updateUser: 'identity:accounts:update',
  changeUserState: 'identity:accounts:update',
  createRole: 'identity:roles:create',
  updateRole: 'identity:roles:update',
  saveRole: 'identity:roles:grant',
  deleteRole: 'identity:roles:delete',
} as const;

export async function getPlatformAccounts(): Promise<PlatformAccount[]> {
  const response = await apiClient.get<ApiResponse<PlatformAccount[]>>('/console/platform-accounts');
  return response.data.data;
}

export async function createPlatformAccount(request: SavePlatformAccountRequest): Promise<PlatformAccount> {
  const response = await apiClient.post<ApiResponse<PlatformAccount>>('/console/platform-accounts', request);
  return response.data.data;
}

export async function updatePlatformAccount(
  userId: number,
  request: SavePlatformAccountRequest,
): Promise<PlatformAccount> {
  const response = await apiClient.put<ApiResponse<PlatformAccount>>(`/console/platform-accounts/${userId}`, request);
  return response.data.data;
}

export async function changePlatformAccountState(
  userId: number,
  action: 'disable' | 'enable' | 'unlock',
): Promise<void> {
  await apiClient.post(`/console/platform-accounts/${userId}/${action}`);
}

export async function getPlatformRoles(): Promise<PlatformRole[]> {
  const response = await apiClient.get<ApiResponse<PlatformRole[]>>('/console/platform-roles');
  return response.data.data;
}

export async function createPlatformRole(request: {
  code: string;
  name: string;
  description: string;
}): Promise<number> {
  const response = await apiClient.post<ApiResponse<number>>('/console/platform-roles', request);
  return response.data.data;
}

export async function updatePlatformRole(
  roleId: number,
  request: { code: string; name: string; description: string; status: PlatformRole['status'] },
): Promise<void> {
  await apiClient.put(`/console/platform-roles/${roleId}`, request);
}

export async function getPermissions(): Promise<PermissionItem[]> {
  const response = await apiClient.get<ApiResponse<PermissionItem[]>>('/console/permissions');
  return response.data.data;
}

export async function replaceRolePermissions(roleId: number, permissionIds: number[]): Promise<void> {
  await apiClient.put(`/console/platform-roles/${roleId}/permissions`, { permissionIds });
}

export async function deletePlatformRole(roleId: number, replacementRoleId: number | null): Promise<void> {
  await apiClient.delete(`/console/platform-roles/${roleId}`, { data: { replacementRoleId } });
}

export async function getAccountOverview(): Promise<AccountOverview> {
  const response = await apiClient.get<ApiResponse<AccountOverview>>('/console/account-overview');
  return response.data.data;
}

export async function getLoginHistory(userId?: number, page = 0, size = 20, all = false): Promise<LoginHistoryPage> {
  const response = await apiClient.get<ApiResponse<LoginHistoryPage>>('/console/login-history', {
    params: { ...(userId === undefined ? {} : { userId }), ...(all ? { all: true } : {}), page, size },
  });
  return response.data.data;
}
