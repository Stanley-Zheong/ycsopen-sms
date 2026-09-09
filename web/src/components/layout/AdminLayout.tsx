import { NavLink, Outlet, Navigate, useNavigate } from 'react-router-dom';
import { useAuthStore, isPlatformRole } from '@/store/authStore';
import type { PlatformUserType } from '@/api/identity';
import { logout as revokeSession } from '@/api/auth';
import { IDENTITY_PERMISSIONS } from '@/api/identity';
import { useIdentityAccess } from '@/pages/admin/identity/useIdentityAccess';
import { AUDIT_PERMISSIONS } from '@/api/audit';
import { SYSTEM_CONFIGURATION_PERMISSIONS } from '@/api/systemConfiguration';
import { TENANT_PERMISSIONS } from '@/api/tenantQualificationApi';
import { EXEMPTION_PERMISSIONS } from '@/api/exemptionPolicyApi';

const OPERATIONS: PlatformUserType[] = ['ADMIN', 'OPERATOR', 'FINANCE'];
const NAV_ITEMS: Array<{ to: string; label: string; permissions?: string[]; roles?: PlatformUserType[] }> = [
  { to: '/admin/dashboard', label: '数据概览（仪表盘）', roles: ['ADMIN', 'OPERATOR', 'FINANCE'] },
  { to: '/admin/tenants', label: '机构管理', roles: OPERATIONS, permissions: [TENANT_PERMISSIONS.menu, TENANT_PERMISSIONS.read] },
  { to: '/admin/channel/configuration', label: '通道管理', roles: ['ADMIN', 'OPERATOR'] },
  { to: '/admin/channel/health', label: '通道健康', roles: ['ADMIN', 'OPERATOR'] },
  { to: '/admin/channel/pools', label: '通道池', roles: ['ADMIN', 'OPERATOR'] },
  { to: '/admin/signatures/review', label: '签名审核', roles: OPERATIONS },
  { to: '/admin/templates/review', label: '模板审核', roles: OPERATIONS },
  { to: '/admin/exemption/policy', label: '豁免策略', roles: OPERATIONS, permissions: [EXEMPTION_PERMISSIONS.menu, EXEMPTION_PERMISSIONS.read] },
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
  { to: '/admin/system/configuration', label: '系统配置', permissions: [SYSTEM_CONFIGURATION_PERMISSIONS.menu, SYSTEM_CONFIGURATION_PERMISSIONS.read] },
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
          item.to === '/admin/tenants' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-tenant-qualification-tenants-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/channel/configuration' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-channel-configuration-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/channel/health' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-channel-health-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/channel/pools' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-channel-health-pools-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/signatures/review' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-signature-lifecycle-signature-review-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/templates/review' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-template-lifecycle-template-review-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/exemption/policy' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-auditable-exemption-exemption-policy-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/system/configuration' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-platform-system-configuration-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : (
            <NavLink key={item.to} to={item.to} className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          )
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
