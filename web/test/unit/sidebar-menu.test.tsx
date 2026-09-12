import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { MemoryRouter, useNavigate } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import SidebarMenu, { type SidebarMenuGroup } from '@/components/layout/SidebarMenu';

const GROUPS: SidebarMenuGroup[] = [
  {
    id: 'overview',
    label: '数据概览',
    items: [{ to: '/admin/dashboard', label: '仪表盘', testId: 'admin-dashboard-nav-menu' }],
  },
  {
    id: 'channels',
    label: '通道管理',
    items: [
      { to: '/admin/channel/configuration', label: '通道配置' },
      { to: '/admin/channel/health', label: '通道健康', testId: 'admin-channel-health-nav-menu' },
    ],
  },
  {
    id: 'system',
    label: '系统管理',
    items: [{ to: '/admin/system/users', label: '系统账号' }],
  },
];

function MenuHarness({ groups = GROUPS }: { groups?: SidebarMenuGroup[] }) {
  const navigate = useNavigate();
  return (
    <>
      <SidebarMenu ariaLabel="平台主导航" groups={groups} testIdPrefix="admin-console-navigation" />
      <button type="button" onClick={() => navigate('/admin/system/users')}>转到系统账号</button>
    </>
  );
}

function renderMenu(path = '/admin/channel/health', groups = GROUPS) {
  return render(
    <MemoryRouter initialEntries={[path]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <MenuHarness groups={groups} />
    </MemoryRouter>,
  );
}

describe('shared two-level sidebar menu', () => {
  it('opens the current route group and exposes an accessible group relationship', () => {
    renderMenu();

    const channelToggle = screen.getByTestId('admin-console-navigation-channels-group-toggle');
    const channelPanel = screen.getByTestId('admin-console-navigation-channels-group-panel');
    expect(channelToggle).toHaveAttribute('aria-expanded', 'true');
    expect(channelToggle).toHaveAttribute('aria-controls', channelPanel.id);
    expect(channelPanel).toHaveAttribute('aria-labelledby', channelToggle.id);
    expect(channelPanel).not.toHaveAttribute('hidden');
    expect(screen.getByTestId('admin-channel-health-nav-menu')).toHaveAttribute('aria-current', 'page');
    expect(screen.getByTestId('admin-console-navigation-overview-group-panel')).toHaveAttribute('hidden');
  });

  it('keeps at most one top-level group expanded', () => {
    renderMenu();

    fireEvent.click(screen.getByTestId('admin-console-navigation-overview-group-toggle'));
    expect(screen.getByTestId('admin-console-navigation-overview-group-panel')).not.toHaveAttribute('hidden');
    expect(screen.getByTestId('admin-console-navigation-channels-group-panel')).toHaveAttribute('hidden');
    expect(screen.getByTestId('admin-console-navigation-system-group-panel')).toHaveAttribute('hidden');
  });

  it('switches the expanded group when the current route changes', () => {
    renderMenu();

    fireEvent.click(screen.getByRole('button', { name: '转到系统账号' }));
    expect(screen.getByTestId('admin-console-navigation-system-group-toggle')).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByTestId('admin-console-navigation-system-group-panel')).not.toHaveAttribute('hidden');
    expect(screen.getByTestId('admin-console-navigation-channels-group-panel')).toHaveAttribute('hidden');
    expect(screen.getByRole('link', { name: '系统账号' })).toHaveAttribute('aria-current', 'page');
  });

  it('reconciles the active group when permission-filtered groups arrive asynchronously', () => {
    function PermissionHarness() {
      const navigate = useNavigate();
      const [allowed, setAllowed] = useState(false);
      return (
        <>
          <SidebarMenu
            ariaLabel="平台主导航"
            groups={allowed ? GROUPS : GROUPS.filter((group) => group.id !== 'system')}
            testIdPrefix="admin-console-navigation"
          />
          <button type="button" onClick={() => {
            navigate('/admin/system/users');
            setAllowed(true);
          }}>加载系统权限</button>
        </>
      );
    }

    render(
      <MemoryRouter initialEntries={['/admin/dashboard']} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <PermissionHarness />
      </MemoryRouter>,
    );
    fireEvent.click(screen.getByRole('button', { name: '加载系统权限' }));

    expect(screen.getByTestId('admin-console-navigation-system-group-toggle')).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByTestId('admin-console-navigation-overview-group-panel')).toHaveAttribute('hidden');
  });
});
