import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import AdminLayout from '@/components/layout/AdminLayout';
import TenantLayout from '@/components/layout/TenantLayout';
import { logout as revokeSession } from '@/api/auth';
import { useAuthStore } from '@/store/authStore';

const access = vi.hoisted(() => ({ allowed: true }));

vi.mock('@/pages/admin/identity/useIdentityAccess', () => ({
  useIdentityAccess: () => ({ can: () => access.allowed }),
}));
vi.mock('@/api/auth', () => ({ logout: vi.fn().mockResolvedValue(undefined) }));

function token(subject: string): string {
  return `${btoa(JSON.stringify({ alg: 'HS256' }))}.${btoa(JSON.stringify({ sub: subject, exp: Date.now() / 1_000 + 300 }))}.signature`;
}

type PlatformUser = 'ADMIN' | 'OPERATOR' | 'FINANCE';
type TenantUser = 'TENANT_ADMIN' | 'TENANT_USER' | 'TENANT_DEV';

function renderAdmin(path: string, userType: PlatformUser = 'ADMIN') {
  useAuthStore.getState().setSession({ accessToken: token(userType), userType, tenantId: null });
  return render(
    <MemoryRouter initialEntries={[path]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <Routes>
        <Route path="/admin" element={<AdminLayout />}>
          <Route path="dashboard" element={<div>平台页面</div>} />
        </Route>
        <Route path="/login" element={<div data-testid="login-route">登录页</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

function renderTenant(path: string, userType: TenantUser = 'TENANT_ADMIN') {
  useAuthStore.getState().setSession({ accessToken: token(userType), userType, tenantId: 7 });
  return render(
    <MemoryRouter initialEntries={[path]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <Routes>
        <Route path="/tenant" element={<TenantLayout />}>
          <Route path="overview" element={<div>机构页面</div>} />
        </Route>
        <Route path="/login" element={<div data-testid="login-route">登录页</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

/** issue-108-deepseek-shell: the shell class contract both consoles must share. */
const SHARED_SHELL_CONTRACT = {
  shell: ['layout', 'app-shell'],
  sidebar: ['sidebar', 'app-sidebar'],
  content: ['content', 'app-content'],
};

function expectSharedShellContract() {
  const shell = screen.getByTestId('shared-console-shell');
  for (const name of SHARED_SHELL_CONTRACT.shell) expect(shell).toHaveClass(name);

  const sidebar = screen.getByTestId('shared-console-shell-sidebar');
  for (const name of SHARED_SHELL_CONTRACT.sidebar) expect(sidebar).toHaveClass(name);

  const content = screen.getByTestId('shared-console-shell-content');
  for (const name of SHARED_SHELL_CONTRACT.content) expect(content).toHaveClass(name);

  const topbar = screen.getByTestId('shared-console-shell-topbar');
  expect(topbar).toHaveClass('app-topbar');
  expect(topbar.tagName).toBe('HEADER');

  expect(screen.getByTestId('shared-console-shell-brand')).toHaveClass('sidebar-brand', 'app-sidebar-brand');
  expect(screen.getByTestId('shared-console-shell-breadcrumb')).toHaveAttribute('aria-label', '面包屑');
  expect(screen.getByTestId('shared-console-shell-content-container')).toHaveClass('app-content-container');

  // Frozen structural contract: issue-77-admin-query-contract.spec.ts asserts `.layout > main.content`.
  expect(shell.querySelector(':scope > main.content')).toBe(content);
  // Frozen structural contract: sidebar first, main second, so mobile stacking stays predictable.
  expect([...shell.children].map((child) => child.tagName)).toEqual(['ASIDE', 'MAIN']);
}

afterEach(() => {
  cleanup();
  access.allowed = true;
  vi.mocked(revokeSession).mockClear();
  useAuthStore.getState().logout();
});

describe('AppShell shared console frame (issue #108)', () => {
  it('renders the shared shell contract on the Admin console', () => {
    renderAdmin('/admin/dashboard');

    expectSharedShellContract();
    expect(screen.getByTestId('shared-console-shell-brand')).toHaveTextContent('YCSAN-SMS 平台管理后台');
    expect(screen.getByTestId('shared-console-shell-workspace')).toHaveTextContent('平台管理后台');
    expect(screen.getByTestId('admin-console-navigation-overview-group-toggle')).toBeInTheDocument();
  });

  it('renders the same shell contract on the Tenant console', () => {
    renderTenant('/tenant/overview');

    expectSharedShellContract();
    expect(screen.getByTestId('shared-console-shell-brand')).toHaveTextContent('YCSAN-SMS 机构端');
    expect(screen.getByTestId('shared-console-shell-workspace')).toHaveTextContent('机构端');
    expect(screen.getByTestId('tenant-console-navigation-overview-group-toggle')).toBeInTheDocument();
  });

  it('derives the page-header breadcrumb from the active navigation entry', () => {
    renderAdmin('/admin/dashboard');

    const breadcrumb = screen.getByTestId('shared-console-shell-breadcrumb');
    expect(breadcrumb).toHaveTextContent('平台管理后台');
    expect(breadcrumb).toHaveTextContent('数据概览');
    expect(breadcrumb).toHaveTextContent('仪表盘');

    cleanup();
    renderTenant('/tenant/overview');
    const tenantBreadcrumb = screen.getByTestId('shared-console-shell-breadcrumb');
    expect(tenantBreadcrumb).toHaveTextContent('机构端');
    expect(tenantBreadcrumb).toHaveTextContent('账户总览');
  });

  it('falls back to the workspace name when no navigation entry matches the route', () => {
    renderAdmin('/admin/dashboard');
    cleanup();
    useAuthStore.getState().setSession({ accessToken: token('ADMIN'), userType: 'ADMIN', tenantId: null });
    render(
      <MemoryRouter initialEntries={['/admin/unknown-module']} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <Routes>
          <Route path="/admin" element={<AdminLayout />}>
            <Route path="unknown-module" element={<div>未知模块</div>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    const breadcrumb = screen.getByTestId('shared-console-shell-breadcrumb');
    expect(breadcrumb).toHaveTextContent('平台管理后台');
    expect(breadcrumb.querySelectorAll('.app-breadcrumb-part')).toHaveLength(0);
  });

  it('signs out through the shared sidebar command and returns to login', async () => {
    renderAdmin('/admin/dashboard');

    fireEvent.click(screen.getByTestId('shared-console-identity-profile-logout'));

    await waitFor(() => expect(screen.getByTestId('login-route')).toBeInTheDocument());
    expect(revokeSession).toHaveBeenCalledTimes(1);
    expect(useAuthStore.getState().userType).toBeNull();
  });

  it('keeps the session command inside the sidebar footer', () => {
    renderAdmin('/admin/dashboard');

    const logout = screen.getByTestId('shared-console-identity-profile-logout');
    expect(logout).toHaveClass('app-sidebar-logout');
    expect(logout.closest('[data-testid="shared-console-shell-sidebar"]')).not.toBeNull();
    expect(logout.closest('.app-sidebar-footer')).not.toBeNull();
  });

  it('renders navigation inside the shell sidebar with the shared item hooks', () => {
    renderAdmin('/admin/dashboard');

    const sidebar = screen.getByTestId('shared-console-shell-sidebar');
    const nav = sidebar.querySelector('nav.app-sidebar-nav');
    expect(nav).not.toBeNull();
    expect(nav).toHaveAttribute('aria-label', '平台主导航');

    const toggle = screen.getByTestId('admin-console-navigation-overview-group-toggle');
    expect(toggle).toHaveClass('app-nav-trigger');
    expect(screen.getByTestId('admin-console-navigation-overview-group-panel')).toHaveClass('app-nav-panel');
    expect(screen.getByTestId('admin-console-navigation-tenant-management-group-panel')).toHaveAttribute('hidden');
  });
});
