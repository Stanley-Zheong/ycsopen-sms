import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { logout as revokeSession } from '@/api/auth';
import { useAuthStore } from '@/store/authStore';
import SidebarMenu, { type SidebarMenuGroup } from './SidebarMenu';

export interface AppShellProps {
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
  workspaceLabel,
  workspaceKind,
  navAriaLabel,
  navTestIdPrefix,
  groups,
}: AppShellProps) {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const clearSession = useAuthStore((state) => state.logout);

  const activeGroup = groups.find((group) => group.items.some((item) => item.to === pathname));
  const activeItem = activeGroup?.items.find((item) => item.to === pathname);
  const breadcrumbTrail = [activeGroup?.label, activeItem?.label]
    .filter((label): label is string => Boolean(label))
    .filter((label, index, all) => all.indexOf(label) === index);

  return (
    <div className="layout app-shell" data-testid="shared-console-shell">
      <aside className="sidebar app-sidebar" data-testid="shared-console-shell-sidebar">
        <div className="sidebar-brand app-sidebar-brand" data-testid="shared-console-shell-brand">
          <span className="app-sidebar-mark" aria-hidden="true">SMS</span>
          <span className="app-sidebar-brand-text">
            <strong>{workspaceLabel}</strong>
            <span className="app-sidebar-brand-kind">{workspaceKind}</span>
          </span>
        </div>
        <SidebarMenu ariaLabel={navAriaLabel} groups={groups} testIdPrefix={navTestIdPrefix} />
        <div className="app-sidebar-footer">
          <button
            className="sidebar-logout app-sidebar-logout"
            data-testid="shared-console-identity-profile-logout"
            type="button"
            onClick={async () => {
              try {
                await revokeSession();
              } finally {
                clearSession();
                navigate('/login');
              }
            }}
          >
            退出登录
          </button>
        </div>
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
        </header>
        <div className="app-content-container" data-testid="shared-console-shell-content-container">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
