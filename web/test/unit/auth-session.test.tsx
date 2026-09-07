import { act, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

function jwtWithExpiration(expiresAtSeconds: number): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = btoa(JSON.stringify({ sub: '7', exp: expiresAtSeconds }));
  return `${header}.${payload}.test-signature`;
}

describe('console authentication session', () => {
  beforeEach(() => {
    vi.resetModules();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-06T08:00:00.000Z'));
    window.sessionStorage.clear();
    window.localStorage.clear();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('restores a non-expired session after a page reload', async () => {
    const firstModule = await import('@/store/authStore');
    const accessToken = jwtWithExpiration(Date.now() / 1000 + 120);

    act(() => {
      firstModule.useAuthStore.getState().setSession({ accessToken, userType: 'ADMIN', tenantId: null });
    });
    expect(window.sessionStorage.getItem(firstModule.AUTH_SESSION_STORAGE_KEY)).toContain(accessToken);

    vi.resetModules();
    const reloadedModule = await import('@/store/authStore');
    expect(reloadedModule.useAuthStore.getState()).toMatchObject({
      accessToken,
      userType: 'ADMIN',
      tenantId: null,
    });
  });

  it('rejects expired persisted material instead of restoring it', async () => {
    const expiredToken = jwtWithExpiration(Date.now() / 1000 - 1);
    window.sessionStorage.setItem(
      'ycsopen.console.auth-session',
      JSON.stringify({ accessToken: expiredToken, userType: 'ADMIN', tenantId: null }),
    );

    const { AUTH_SESSION_STORAGE_KEY, useAuthStore } = await import('@/store/authStore');
    expect(useAuthStore.getState()).toMatchObject({ accessToken: null, userType: null, tenantId: null });
    expect(window.sessionStorage.getItem(AUTH_SESSION_STORAGE_KEY)).toBeNull();
  });

  it('automatically logs out when the JWT expiry is reached', async () => {
    const { useAuthStore } = await import('@/store/authStore');
    const accessToken = jwtWithExpiration(Date.now() / 1000 + 2);

    act(() => {
      useAuthStore.getState().setSession({ accessToken, userType: 'TENANT_ADMIN', tenantId: 7 });
      vi.advanceTimersByTime(2_000);
    });

    expect(useAuthStore.getState()).toMatchObject({ accessToken: null, userType: null, tenantId: null });
    expect(window.sessionStorage.length).toBe(0);
  });

  it('does not establish a session from a malformed token', async () => {
    const { useAuthStore } = await import('@/store/authStore');

    act(() => {
      useAuthStore.getState().setSession({ accessToken: 'not-a-jwt', userType: 'ADMIN', tenantId: null });
    });

    expect(useAuthStore.getState()).toMatchObject({ accessToken: null, userType: null, tenantId: null });
  });

  it('changes protected query identity and clears the shared cache on principal change', async () => {
    const { protectedQueryKey, useAuthStore } = await import('@/store/authStore');
    const { queryClient } = await import('@/api/queryClient');
    const accountA = jwtWithExpiration(Date.now() / 1000 + 120);
    useAuthStore.getState().setSession({ accessToken: accountA, userType: 'ADMIN', tenantId: null });
    const accountAKey = protectedQueryKey('account-overview');
    queryClient.setQueryData(accountAKey, { username: 'account-a' });

    const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
    const payload = btoa(JSON.stringify({ sub: '8', exp: Date.now() / 1000 + 120 }));
    useAuthStore.getState().setSession({
      accessToken: `${header}.${payload}.test-signature`, userType: 'OPERATOR', tenantId: null,
    });

    expect(protectedQueryKey('account-overview')).not.toEqual(accountAKey);
    expect(queryClient.getQueryData(accountAKey)).toBeUndefined();
  });
});

describe('ProtectedRoute', () => {
  beforeEach(() => {
    vi.resetModules();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-06T08:00:00.000Z'));
    window.sessionStorage.clear();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('redirects a tenant session away from platform routes', async () => {
    const { useAuthStore } = await import('@/store/authStore');
    const { default: ProtectedRoute } = await import('@/router/ProtectedRoute');
    act(() => {
      useAuthStore.getState().setSession({
        accessToken: jwtWithExpiration(Date.now() / 1000 + 120),
        userType: 'TENANT_USER',
        tenantId: 7,
      });
    });

    render(
      <MemoryRouter
        initialEntries={['/admin/dashboard']}
        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
      >
        <ProtectedRoute audience="platform">
          <div data-testid="protected-content">平台内容</div>
        </ProtectedRoute>
      </MemoryRouter>,
    );

    expect(screen.queryByTestId('protected-content')).not.toBeInTheDocument();
  });

  it('renders the protected branch for an active matching session', async () => {
    const { useAuthStore } = await import('@/store/authStore');
    const { default: ProtectedRoute } = await import('@/router/ProtectedRoute');
    act(() => {
      useAuthStore.getState().setSession({
        accessToken: jwtWithExpiration(Date.now() / 1000 + 120),
        userType: 'ADMIN',
        tenantId: null,
      });
    });

    render(
      <MemoryRouter
        initialEntries={['/admin/dashboard']}
        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
      >
        <ProtectedRoute audience="platform">
          <div data-testid="protected-content">平台内容</div>
        </ProtectedRoute>
      </MemoryRouter>,
    );

    expect(screen.getByTestId('protected-content')).toBeVisible();
  });
});

describe('authenticated API client', () => {
  beforeEach(() => {
    vi.resetModules();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-06T08:00:00.000Z'));
    window.sessionStorage.clear();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('adds the current bearer token to an API request', async () => {
    const { useAuthStore } = await import('@/store/authStore');
    const { apiClient } = await import('@/api/client');
    const accessToken = jwtWithExpiration(Date.now() / 1000 + 120);
    useAuthStore.getState().setSession({ accessToken, userType: 'ADMIN', tenantId: null });

    let authorization: unknown;
    await apiClient.get('/probe', {
      adapter: async (config) => {
        authorization = config.headers.Authorization;
        return { data: {}, status: 200, statusText: 'OK', headers: {}, config };
      },
    });

    expect(authorization).toBe(`Bearer ${accessToken}`);
  });

  it('removes an expired token before dispatching an API request', async () => {
    const { useAuthStore } = await import('@/store/authStore');
    const { apiClient } = await import('@/api/client');
    const accessToken = jwtWithExpiration(Date.now() / 1000 + 1);
    useAuthStore.getState().setSession({ accessToken, userType: 'ADMIN', tenantId: null });
    vi.setSystemTime(new Date('2026-09-06T08:00:02.000Z'));

    let authorization: unknown = 'unexpected';
    await apiClient.get('/probe', {
      headers: { Authorization: 'Bearer stale-caller-token' },
      adapter: async (config) => {
        authorization = config.headers.Authorization;
        return { data: {}, status: 200, statusText: 'OK', headers: {}, config };
      },
    });

    expect(authorization).toBeUndefined();
    expect(useAuthStore.getState().accessToken).toBeNull();
  });

  it('clears the session after a server rejects it with 401', async () => {
    const { useAuthStore } = await import('@/store/authStore');
    const { apiClient } = await import('@/api/client');
    useAuthStore.getState().setSession({
      accessToken: jwtWithExpiration(Date.now() / 1000 + 120),
      userType: 'ADMIN',
      tenantId: null,
    });

    await expect(apiClient.get('/probe', {
      adapter: async () => Promise.reject({ response: { status: 401 } }),
    })).rejects.toMatchObject({ response: { status: 401 } });

    expect(useAuthStore.getState().accessToken).toBeNull();
    expect(window.sessionStorage.length).toBe(0);
  });
});
