import { NavLink, Outlet, Navigate, useNavigate } from 'react-router-dom';
import { useAuthStore, isPlatformRole } from '@/store/authStore';
import type { PlatformUserType } from '@/api/identity';
import { logout as revokeSession } from '@/api/auth';
import { IDENTITY_PERMISSIONS } from '@/api/identity';
import { useIdentityAccess } from '@/pages/admin/identity/useIdentityAccess';
import { AUDIT_PERMISSIONS } from '@/api/audit';

const OPERATIONS: PlatformUserType[] = ['ADMIN', 'OPERATOR'];
const NAV_ITEMS: Array<{ to: string; label: string; permissions?: string[]; roles?: PlatformUserType[] }> = [
  { to: '/admin/dashboard', label: '数据概览（仪表盘）', roles: ['ADMIN', 'OPERATOR', 'FINANCE'] },
  { to: '/admin/tenants', label: '机构管理', roles: OPERATIONS },
  { to: '/admin/channels', label: '通道管理', roles: OPERATIONS },
  { to: '/admin/audit', label: '审核中心', roles: OPERATIONS },
  { to: '/admin/riskcontrol', label: '验证规则', roles: OPERATIONS },
  { to: '/admin/complaints', label: '投诉管理', roles: OPERATIONS },
  { to: '/admin/uplink', label: '上行数据', roles: OPERATIONS },
  { to: '/admin/records', label: '数据详单', roles: OPERATIONS },
  { to: '/admin/statistics', label: '数据统计', roles: OPERATIONS },
  { to: '/admin/finance', label: '财务中心', roles: ['ADMIN', 'FINANCE'] },
  { to: '/admin/alerts', label: '告警管理', roles: OPERATIONS },
  { to: '/admin/tools', label: '工具管理', roles: OPERATIONS },
  { to: '/admin/system/users', label: '系统账号', permissions: [IDENTITY_PERMISSIONS.identityMenu, IDENTITY_PERMISSIONS.usersRead] },
  { to: '/admin/system/roles', label: '系统角色', permissions: [IDENTITY_PERMISSIONS.identityMenu, IDENTITY_PERMISSIONS.rolesRead] },
  { to: '/admin/system/login-history', label: '登录历史', permissions: [IDENTITY_PERMISSIONS.identityMenu, IDENTITY_PERMISSIONS.historyRead] },
  { to: '/admin/system/logs', label: '操作日志', permissions: [AUDIT_PERMISSIONS.operationsRead] },
  { to: '/admin/system/security-events', label: '安全事件', permissions: [AUDIT_PERMISSIONS.securityEventsRead] },
  { to: '/admin/account-overview', label: '账号概览' },
];

/** 平台管理后台整体布局，导航结构与 ycsansms.md 8.1 节一一对应。 */
export default function AdminLayout() {
  const userType = useAuthStore((s) => s.userType);
  const clearSession = useAuthStore((s) => s.logout);
  const navigate = useNavigate();
  const access = useIdentityAccess(isPlatformRole(userType));
  if (!isPlatformRole(userType)) {
    return <Navigate to="/login" replace />;
  }
  return (
    <div className="layout">
      <nav className="sidebar">
        <div style={{ fontWeight: 700, marginBottom: 16 }}>YCSAN-SMS 平台管理后台</div>
        {NAV_ITEMS.filter((item) => (!item.roles || item.roles.includes(userType as PlatformUserType))
          && (!item.permissions || item.permissions.every(access.can))).map((item) => (
          <NavLink key={item.to} to={item.to} className={({ isActive }) => (isActive ? 'active' : '')}>
            {item.label}
          </NavLink>
        ))}
        <button data-testid="shared-console-identity-profile-logout" type="button" onClick={async () => {
          try { await revokeSession(); } finally { clearSession(); navigate('/login'); }
        }}>退出登录</button>
      </nav>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
