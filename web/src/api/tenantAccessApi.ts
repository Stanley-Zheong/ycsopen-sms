import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export interface TenantAdministrator { id:number; username:string; realName:string; userType:string; status:string; createdAt:string; roleNames:string|null }
export interface TenantApiKey { id:number; appKey:string; name:string; description:string|null; status:string; ipWhitelist:string|null; perSecond:number; perMinute:number; perHour:number; perDay:number; expireTime:string|null; lastUsedTime:string|null; secret?:string }
export interface TenantCmppCredential { id:number; protocol:string; account:string; spid:string; endpointHost:string; endpointPort:number; maxConnections:number; tpsLimit:number; windowSize:number; ipWhitelist:string|null; status:string; password?:string }
const data = <T>(response: { data: ApiResponse<T> }) => response.data.data;
export async function listTenantAdministrators() { return data(await apiClient.get<ApiResponse<TenantAdministrator[]>>('/console/tenant/administrators')); }
export async function createTenantAdministrator(input: Record<string,unknown>) { return data(await apiClient.post<ApiResponse<TenantAdministrator>>('/console/tenant/administrators', input)); }
export async function updateTenantAdministrator(id:number,input:Record<string,unknown>) { return data(await apiClient.patch<ApiResponse<TenantAdministrator>>(`/console/tenant/administrators/${id}`,input)); }
export async function listTenantApiKeys() { return data(await apiClient.get<ApiResponse<TenantApiKey[]>>('/console/tenant/api-keys')); }
export async function createTenantApiKey(input:Record<string,unknown>) { return data(await apiClient.post<ApiResponse<TenantApiKey>>('/console/tenant/api-keys',input)); }
export async function revokeTenantApiKey(id:number) { return data(await apiClient.post<ApiResponse<null>>(`/console/tenant/api-keys/${id}/revoke`)); }
export async function listTenantCmpp() { return data(await apiClient.get<ApiResponse<TenantCmppCredential[]>>('/console/tenant/cmpp-credentials')); }
export async function createTenantCmpp(input:Record<string,unknown>) { return data(await apiClient.post<ApiResponse<TenantCmppCredential>>('/console/tenant/cmpp-credentials',input)); }
export async function revokeTenantCmpp(id:number) { return data(await apiClient.post<ApiResponse<null>>(`/console/tenant/cmpp-credentials/${id}/revoke`)); }
