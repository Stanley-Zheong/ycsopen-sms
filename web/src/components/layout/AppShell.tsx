import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import type { FormEvent } from 'react';
import { changePassword, logout as revokeSession } from '@/api/auth';
import { useAuthStore } from '@/store/authStore';
import SidebarMenu, { type SidebarMenuGroup } from './SidebarMenu';

export interface AppShellProps {
  /** Console audience used to scope audience-specific layout contracts without changing shell classes. */
  consoleKind: 'admin' | 'tenant';
  /** Product identity shown in the sidebar brand row and the page-header badge. */
  workspaceLabel: string;
  /** Short console name, e.g. 平台管理后台 / 机构端. */
  workspaceKind: string;
  /** Accessible name for the primary navigation. */
  navAriaLabel: string;
  /** Stable SidebarMenu test-id prefix, e.g. `admin-console-navigation`. */
  navTestIdPrefix: string;
  /** Already permission- and role-filtered navigation groups owned by the calling layout. */
  groups: SidebarMenuGroup[];
}

/**
 * issue-108-deepseek-shell: one presentation frame shared by the Admin and Tenant consoles.
 *
 * Ownership (see .planning/frontend-spirits/01-foundation-contract/SYSTEM-DESIGN.md):
 * the calling layout owns role gating, redirects and permission filtering; this component
 * owns brand, navigation frame, session command, page-header band and content container.
 * It renders from store and route data only and performs no fetch.
 *
 * Element hooks kept on purpose because existing specs depend on them:
 * `.layout` root, `<main class="content">` as a direct child of `.layout`, and the
 * `sidebar-*` / `sidebar-menu-panel` menu classes used by SidebarMenu.
 */
export default function AppShell({
  consoleKind,
  workspaceLabel,
  workspaceKind,
  navAriaLabel,
  navTestIdPrefix,
  groups,
}: AppShellProps) {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const clearSession = useAuthStore((state) => state.logout);
  const userType = useAuthStore((state) => state.userType);
  const [profileOpen, setProfileOpen] = useState(false);
  const [passwordOpen, setPasswordOpen] = useState(false);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [passwordMessage, setPasswordMessage] = useState<string | null>(null);
  const [passwordSubmitting, setPasswordSubmitting] = useState(false);

  async function signOut() {
    try {
      await revokeSession();
    } finally {
      clearSession();
      navigate('/login');
    }
  }

  async function submitPasswordChange(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPasswordSubmitting(true);
    setPasswordMessage(null);
    try {
      await changePassword(currentPassword, newPassword);
      clearSession();
      navigate('/login');
    } catch {
      setPasswordMessage('修改失败，请检查当前密码和新密码复杂度。');
    } finally {
      setPasswordSubmitting(false);
    }
  }

  const activeGroup = groups.find((group) => group.items.some((item) => item.to === pathname));
  const activeItem = activeGroup?.items.find((item) => item.to === pathname);
  const breadcrumbTrail = [activeGroup?.label, activeItem?.label]
    .filter((label): label is string => Boolean(label))
    .filter((label, index, all) => all.indexOf(label) === index);

  return (
    <div className="layout app-shell" data-console-kind={consoleKind} data-testid="shared-console-shell">
      <aside className="sidebar app-sidebar" data-testid="shared-console-shell-sidebar">
        <div className="sidebar-brand app-sidebar-brand" data-testid="shared-console-shell-brand">
          <span className="app-sidebar-mark" aria-hidden="true">SMS</span>
          <span className="app-sidebar-brand-text">
            <strong>{workspaceLabel}</strong>
            <span className="app-sidebar-brand-kind">{workspaceKind}</span>
          </span>
        </div>
        <SidebarMenu ariaLabel={navAriaLabel} groups={groups} testIdPrefix={navTestIdPrefix} />
      </aside>
      <main className="content app-content" data-testid="shared-console-shell-content">
        <header className="app-topbar" data-testid="shared-console-shell-topbar">
          <nav className="app-breadcrumb" aria-label="面包屑" data-testid="shared-console-shell-breadcrumb">
            <span className="app-breadcrumb-workspace">{workspaceKind}</span>
            {breadcrumbTrail.map((label, index) => (
              <span key={label} className="app-breadcrumb-part">
                <span className="app-breadcrumb-separator" aria-hidden="true">/</span>
                <span
                  className={index === breadcrumbTrail.length - 1 ? 'app-breadcrumb-current' : 'app-breadcrumb-item'}
                >
                  {label}
                </span>
              </span>
            ))}
          </nav>
          <span className="app-workspace-badge" data-testid="shared-console-shell-workspace">
            {workspaceKind}
          </span>
          <div className="app-user-center" data-testid="shared-console-identity-user-center">
            <button
              type="button"
              className="app-user-center-trigger"
              data-testid="shared-console-identity-user-center-trigger"
              aria-haspopup="menu"
              aria-expanded={profileOpen}
              onClick={() => setProfileOpen((open) => !open)}
            >
              <span className="app-user-avatar" aria-hidden="true">{(userType ?? 'U').slice(0, 1)}</span>
              <span className="app-user-label">用户中心</span>
            </button>
            {profileOpen && (
              <div className="app-user-menu" role="menu" data-testid="shared-console-identity-user-center-menu">
                <div className="app-user-menu-heading" data-testid="shared-console-identity-user-center-summary">
                  <strong>个人中心</strong>
                  <span>{userType ?? '未登录'}</span>
                </div>
                <button
                  type="button"
                  role="menuitem"
                  className="app-user-menu-item"
                  data-testid="shared-console-identity-profile-password"
                  onClick={() => {
                    setProfileOpen(false);
                    setPasswordOpen(true);
                  }}
                >
                  修改密码
                </button>
                <button
                  type="button"
                  role="menuitem"
                  className="app-user-menu-item danger"
                  data-testid="shared-console-identity-profile-logout"
                  onClick={() => void signOut()}
                >
                  退出登录
                </button>
              </div>
            )}
          </div>
          {passwordOpen && (
            <form
              className="app-password-popover"
              data-testid="shared-console-identity-password-form"
              onSubmit={(event) => void submitPasswordChange(event)}
            >
              <strong>修改密码</strong>
              <label>
                当前密码
                <input
                  data-testid="shared-console-identity-password-current"
                  type="password"
                  value={currentPassword}
                  onChange={(event) => setCurrentPassword(event.target.value)}
                />
              </label>
              <label>
                新密码
                <input
                  data-testid="shared-console-identity-password-new"
                  type="password"
                  value={newPassword}
                  onChange={(event) => setNewPassword(event.target.value)}
                />
              </label>
              {passwordMessage && <p role="alert">{passwordMessage}</p>}
              <div className="form-actions">
                <button type="button" className="button-secondary" onClick={() => setPasswordOpen(false)}>取消</button>
                <button data-testid="shared-console-identity-password-submit" type="submit" disabled={passwordSubmitting}>
                  {passwordSubmitting ? '提交中' : '保存'}
                </button>
              </div>
            </form>
          )}
        </header>
        <div className="app-content-container" data-testid="shared-console-shell-content-container">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
