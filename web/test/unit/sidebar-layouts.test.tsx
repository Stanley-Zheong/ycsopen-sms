import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import AdminLayout from '@/components/layout/AdminLayout';
import TenantLayout from '@/components/layout/TenantLayout';
import { useAuthStore } from '@/store/authStore';

const access = vi.hoisted(() => ({ allowed: true }));

vi.mock('@/pages/admin/identity/useIdentityAccess', () => ({
  useIdentityAccess: () => ({ can: () => access.allowed }),
}));
vi.mock('@/api/auth', () => ({ logout: vi.fn().mockResolvedValue(undefined) }));

function token(subject: string): string {
  return `${btoa(JSON.stringify({ alg: 'HS256' }))}.${btoa(JSON.stringify({ sub: subject, exp: Date.now() / 1_000 + 300 }))}.signature`;
}

function renderAdmin(path: string, userType: 'ADMIN' | 'OPERATOR' | 'FINANCE' = 'ADMIN') {
  useAuthStore.getState().setSession({ accessToken: token(userType), userType, tenantId: null });
  return render(
    <MemoryRouter initialEntries={[path]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <Routes>
        <Route path="/admin" element={<AdminLayout />}>
          <Route path="*" element={<div>平台页面</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

function renderTenant(path: string, userType: 'TENANT_ADMIN' | 'TENANT_USER' | 'TENANT_DEV' = 'TENANT_USER') {
  useAuthStore.getState().setSession({ accessToken: token(userType), userType, tenantId: 7 });
  return render(
    <MemoryRouter initialEntries={[path]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <Routes>
        <Route path="/tenant" element={<TenantLayout />}>
          <Route path="*" element={<div>机构页面</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

afterEach(() => {
  cleanup();
  access.allowed = true;
  useAuthStore.getState().logout();
});

describe('Admin and Tenant sidebar integration', () => {
  it('groups every routed Admin detail page and preserves existing leaf selectors', () => {
    renderAdmin('/admin/submission/details');

    expect(screen.getByTestId('admin-console-navigation-message-details-group-toggle')).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByTestId('admin-message-operations-submission-details-nav-menu')).toHaveAttribute('aria-current', 'page');
    expect(screen.getByTestId('admin-channel-health-nav-menu')).toBeInTheDocument();
    expect(screen.getByTestId('admin-tenant-qualification-tenants-nav-menu')).toBeInTheDocument();

    fireEvent.click(screen.getByTestId('admin-console-navigation-review-center-group-toggle'));
    expect(screen.getByTestId('admin-console-navigation-message-details-group-toggle')).toHaveAttribute('aria-expanded', 'false');
    expect(screen.getByTestId('admin-console-navigation-review-center-group-toggle')).toHaveAttribute('aria-expanded', 'true');
  });

  it('removes Admin groups left empty by existing permission filtering', () => {
    access.allowed = false;
    renderAdmin('/admin/dashboard', 'OPERATOR');

    expect(screen.queryByTestId('admin-console-navigation-tenant-management-group-toggle')).not.toBeInTheDocument();
    expect(screen.queryByTestId('admin-console-navigation-validation-rules-group-toggle')).not.toBeInTheDocument();
    expect(screen.getByTestId('admin-console-navigation-channel-management-group-toggle')).toBeInTheDocument();
  });

  it('opens routed Tenant children and hides groups restricted from business users', () => {
    renderTenant('/tenant/scheduled/tasks');

    expect(screen.queryByTestId('tenant-console-navigation-account-settings-group-toggle')).not.toBeInTheDocument();
    expect(screen.queryByTestId('tenant-console-navigation-configuration-group-toggle')).not.toBeInTheDocument();
    expect(screen.queryByTestId('tenant-console-navigation-template-management-group-toggle')).not.toBeInTheDocument();
    expect(screen.queryByTestId('tenant-console-navigation-signature-management-group-toggle')).not.toBeInTheDocument();
    expect(screen.queryByTestId('tenant-console-navigation-uplink-query-group-toggle')).not.toBeInTheDocument();
    expect(screen.queryByTestId('tenant-webhook-delivery-webhooks-nav-menu')).not.toBeInTheDocument();
    expect(screen.queryByTestId('tenant-tenant-access-api-keys-nav-menu')).not.toBeInTheDocument();
    expect(screen.queryByTestId('tenant-tenant-access-cmpp-access-nav-menu')).not.toBeInTheDocument();
    expect(screen.getByTestId('tenant-console-navigation-account-management-group-toggle')).toBeInTheDocument();
    expect(screen.queryByTestId('tenant-recharge-operations-recharge-nav-menu')).not.toBeInTheDocument();
    expect(screen.getByTestId('tenant-trial-prepaid-consumption-ledger-nav-menu')).toBeInTheDocument();
    expect(screen.getByTestId('tenant-console-navigation-short-links-group-toggle')).toBeInTheDocument();
    expect(screen.getByTestId('tenant-console-navigation-send-management-group-toggle')).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByTestId('tenant-bulk-scheduled-scheduled-tasks-nav-menu')).toHaveAttribute('aria-current', 'page');
  });

  it('keeps Tenant Admin account-setting selectors and active group behavior', () => {
    renderTenant('/tenant/qualification', 'TENANT_ADMIN');

    expect(screen.getByTestId('tenant-console-navigation-account-settings-group-toggle')).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByTestId('tenant-tenant-qualification-qualification-nav-menu')).toHaveAttribute('aria-current', 'page');
  });

  it('shows integration configuration to Tenant developers without administrator settings', () => {
    renderTenant('/tenant/webhooks', 'TENANT_DEV');

    expect(screen.queryByTestId('tenant-console-navigation-account-settings-group-toggle')).not.toBeInTheDocument();
    expect(screen.queryByTestId('tenant-console-navigation-template-management-group-toggle')).not.toBeInTheDocument();
    expect(screen.queryByTestId('tenant-console-navigation-signature-management-group-toggle')).not.toBeInTheDocument();
    expect(screen.queryByTestId('tenant-console-navigation-account-management-group-toggle')).not.toBeInTheDocument();
    expect(screen.queryByTestId('tenant-console-navigation-short-links-group-toggle')).not.toBeInTheDocument();
    expect(screen.getByTestId('tenant-console-navigation-configuration-group-toggle')).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByTestId('tenant-console-navigation-uplink-query-group-toggle')).toBeInTheDocument();
    expect(screen.getByTestId('tenant-webhook-delivery-webhooks-nav-menu')).toHaveAttribute('aria-current', 'page');
    expect(screen.getByTestId('tenant-tenant-access-api-keys-nav-menu')).toBeInTheDocument();
    expect(screen.getByTestId('tenant-tenant-access-cmpp-access-nav-menu')).toBeInTheDocument();
  });
});
