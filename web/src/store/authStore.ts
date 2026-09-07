import { create } from 'zustand';
import type { LoginResponse } from '@/types/api';
import { queryClient } from '@/api/queryClient';

export const AUTH_SESSION_STORAGE_KEY = 'ycsopen.console.auth-session';

interface AuthSession {
  accessToken: string | null;
  userType: LoginResponse['userType'] | null;
  tenantId: number | null;
  expiresAt: number | null;
  principalKey: string | null;
}

interface AuthState extends AuthSession {
  setSession: (session: LoginResponse) => void;
  logout: () => void;
}

const EMPTY_SESSION: AuthSession = {
  accessToken: null,
  userType: null,
  tenantId: null,
  expiresAt: null,
  principalKey: null,
};

const USER_TYPES = new Set<LoginResponse['userType']>([
  'ADMIN',
  'OPERATOR',
  'FINANCE',
  'TENANT_ADMIN',
  'TENANT_USER',
  'TENANT_DEV',
]);

let expirationTimer: ReturnType<typeof setTimeout> | undefined;

function storage(): Storage | null {
  return typeof window === 'undefined' ? null : window.sessionStorage;
}

function clearPersistedSession(): void {
  storage()?.removeItem(AUTH_SESSION_STORAGE_KEY);
}

/**
 * Reads only the JWT expiry for client-side lifecycle handling. Signature and
 * authorization checks remain exclusively server-side.
 */
function getJwtPayload(accessToken: string): { exp?: unknown; sub?: unknown; jti?: unknown } | null {
  const parts = accessToken.split('.');
  if (parts.length !== 3 || !parts[1]) {
    return null;
  }

  try {
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=');
    return JSON.parse(atob(padded)) as { exp?: unknown; sub?: unknown; jti?: unknown };
  } catch {
    return null;
  }
}

export function getJwtExpiration(accessToken: string): number | null {
  const payload = getJwtPayload(accessToken);
  return typeof payload?.exp === 'number' && Number.isFinite(payload.exp) && payload.exp > 0
    ? payload.exp * 1_000
    : null;
}

function getJwtPrincipalKey(accessToken: string): string | null {
  const payload = getJwtPayload(accessToken);
  if (typeof payload?.sub !== 'string' || !payload.sub) return null;
  return `${payload.sub}:${typeof payload.jti === 'string' && payload.jti ? payload.jti : accessToken}`;
}

export function isSessionActive(
  session: Pick<AuthSession, 'accessToken' | 'userType' | 'expiresAt'>,
  now = Date.now(),
): boolean {
  return Boolean(
    session.accessToken
      && session.userType
      && session.expiresAt
      && session.expiresAt > now,
  );
}

function readPersistedSession(): AuthSession {
  const sessionStorage = storage();
  if (!sessionStorage) {
    return EMPTY_SESSION;
  }

  try {
    const raw = sessionStorage.getItem(AUTH_SESSION_STORAGE_KEY);
    if (!raw) {
      return EMPTY_SESSION;
    }
    const candidate = JSON.parse(raw) as Partial<AuthSession>;
    const expiresAt = typeof candidate.accessToken === 'string'
      ? getJwtExpiration(candidate.accessToken)
      : null;
    const restored: AuthSession = {
      accessToken: typeof candidate.accessToken === 'string' ? candidate.accessToken : null,
      userType: typeof candidate.userType === 'string' && USER_TYPES.has(candidate.userType as LoginResponse['userType'])
        ? candidate.userType as LoginResponse['userType']
        : null,
      tenantId: typeof candidate.tenantId === 'number' ? candidate.tenantId : null,
      expiresAt,
      principalKey: typeof candidate.accessToken === 'string' ? getJwtPrincipalKey(candidate.accessToken) : null,
    };
    if (isSessionActive(restored) && restored.principalKey) {
      return restored;
    }
  } catch {
    // Malformed browser state is treated as unauthenticated.
  }

  clearPersistedSession();
  return EMPTY_SESSION;
}

function persistSession(session: AuthSession): void {
  storage()?.setItem(AUTH_SESSION_STORAGE_KEY, JSON.stringify({
    accessToken: session.accessToken,
    userType: session.userType,
    tenantId: session.tenantId,
  }));
}

function scheduleExpiration(expiresAt: number): void {
  if (expirationTimer) {
    clearTimeout(expirationTimer);
  }
  const remaining = Math.max(0, expiresAt - Date.now());
  const maximumTimerDelay = 2_147_483_647;
  expirationTimer = setTimeout(() => {
    if (Date.now() >= expiresAt) {
      useAuthStore.getState().logout();
    } else {
      scheduleExpiration(expiresAt);
    }
  }, Math.min(remaining, maximumTimerDelay));
}

const initialSession = readPersistedSession();

/** 会话状态：刷新可恢复；JWT 到期、401 或主动登出时统一清空。 */
export const useAuthStore = create<AuthState>((set) => ({
  ...initialSession,
  setSession: (session) => {
    const expiresAt = getJwtExpiration(session.accessToken);
    const nextSession: AuthSession = {
      accessToken: session.accessToken,
      userType: session.userType,
      tenantId: session.tenantId,
      expiresAt,
      principalKey: getJwtPrincipalKey(session.accessToken),
    };
    if (!isSessionActive(nextSession) || !nextSession.principalKey) {
      clearPersistedSession();
      queryClient.clear();
      set(EMPTY_SESSION);
      return;
    }
    queryClient.clear();
    persistSession(nextSession);
    scheduleExpiration(expiresAt!);
    set(nextSession);
  },
  logout: () => {
    if (expirationTimer) {
      clearTimeout(expirationTimer);
      expirationTimer = undefined;
    }
    clearPersistedSession();
    queryClient.clear();
    set(EMPTY_SESSION);
  },
}));

if (isSessionActive(initialSession)) {
  scheduleExpiration(initialSession.expiresAt!);
}

/** Returns no stale token and eagerly clears an expired session before requests. */
export function getActiveAccessToken(): string | null {
  const state = useAuthStore.getState();
  if (!isSessionActive(state)) {
    if (state.accessToken) {
      state.logout();
    }
    return null;
  }
  return state.accessToken;
}

/** Principal/session-scoped prefix prevents protected data reuse across logins. */
export function protectedQueryKey(name: string, ...parts: unknown[]): readonly unknown[] {
  const principalKey = useAuthStore.getState().principalKey ?? 'anonymous';
  return ['protected', principalKey, name, ...parts] as const;
}

/** 平台方角色 vs 机构方角色，用于路由守卫（对应 PRD 3.1 节角色定义）。 */
export function isPlatformRole(userType: LoginResponse['userType'] | null): boolean {
  return userType === 'ADMIN' || userType === 'OPERATOR' || userType === 'FINANCE';
}
