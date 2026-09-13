import { useEffect, useState } from 'react';
import { NavLink, useLocation } from 'react-router-dom';

export interface SidebarMenuItem {
  to: string;
  label: string;
  testId?: string;
}

export interface SidebarMenuGroup {
  id: string;
  label: string;
  items: SidebarMenuItem[];
}

interface SidebarMenuProps {
  ariaLabel: string;
  groups: SidebarMenuGroup[];
  testIdPrefix: string;
}

/** issue-51-shared-sidebar-01: one PRD module may be expanded at a time. */
export default function SidebarMenu({ ariaLabel, groups, testIdPrefix }: SidebarMenuProps) {
  const { pathname } = useLocation();
  const activeGroupId = groups.find((group) => group.items.some((item) => item.to === pathname))?.id;
  const groupIds = groups.map((group) => group.id).join('|');
  const firstGroupId = groups[0]?.id;
  const [openGroupId, setOpenGroupId] = useState<string | null>(() => activeGroupId ?? groups[0]?.id ?? null);

  useEffect(() => {
    if (activeGroupId) {
      setOpenGroupId(activeGroupId);
      return;
    }
    setOpenGroupId((current) => (
      current && groupIds.split('|').includes(current) ? current : firstGroupId ?? null
    ));
  }, [activeGroupId, firstGroupId, groupIds]);

  return (
    <nav className="sidebar-menu" aria-label={ariaLabel}>
      {groups.map((group) => {
        const expanded = openGroupId === group.id;
        const groupContainsActiveRoute = activeGroupId === group.id;
        const toggleId = `${testIdPrefix}-${group.id}-group-toggle`;
        const panelId = `${testIdPrefix}-${group.id}-group-panel`;
        return (
          <div className="sidebar-menu-group" key={group.id}>
            <button
              id={toggleId}
              className={`sidebar-menu-trigger${groupContainsActiveRoute ? ' active-group' : ''}`}
              type="button"
              aria-expanded={expanded}
              aria-controls={panelId}
              data-testid={toggleId}
              onClick={() => setOpenGroupId((current) => (current === group.id ? null : group.id))}
            >
              <span>{group.label}</span>
              <span className="sidebar-menu-chevron" aria-hidden="true">⌄</span>
            </button>
            <div
              id={panelId}
              className="sidebar-menu-panel"
              role="group"
              aria-labelledby={toggleId}
              data-testid={panelId}
              hidden={!expanded}
            >
              {group.items.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end
                  data-testid={item.testId}
                  className={({ isActive }) => (isActive ? 'active' : '')}
                >
                  {item.label}
                </NavLink>
              ))}
            </div>
          </div>
        );
      })}
    </nav>
  );
}
