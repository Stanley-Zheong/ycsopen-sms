import { Navigate, Outlet, useNavigate } from 'react-router-dom';
import { useAuthStore, isPlatformRole } from '@/store/authStore';
import { logout as revokeSession } from '@/api/auth';
import type { LoginResponse } from '@/types/api';
import SidebarMenu, { type SidebarMenuGroup, type SidebarMenuItem } from './SidebarMenu';

type TenantUserType = Extract<LoginResponse['userType'], 'TENANT_ADMIN' | 'TENANT_USER' | 'TENANT_DEV'>;

interface TenantNavItem extends SidebarMenuItem {
  roles?: TenantUserType[];
}

interface TenantNavGroup extends Omit<SidebarMenuGroup, 'items'> {
  items: TenantNavItem[];
}

/** The two levels follow the Tenant information architecture in PRD section 8.2. */
const NAV_GROUPS: TenantNavGroup[] = [
  {
    id: 'account-settings', label: '账户设置', items: [
      { to: '/tenant/qualification', label: '资质认证', roles: ['TENANT_ADMIN'], testId: 'tenant-tenant-qualification-qualification-nav-menu' },
      { to: '/tenant/administrators', label: '子账号管理', roles: ['TENANT_ADMIN'] },
    ],
  },
  {
    id: 'overview', label: '概览', items: [
      { to: '/tenant/overview', label: '账户总览' },
    ],
  },
  {
    id: 'send-management', label: '发送管理', items: [
      { to: '/tenant/send', label: '在线发送' },
      { to: '/tenant/bulk/send', label: '批量发送', testId: 'tenant-bulk-scheduled-bulk-send-nav-menu' },
      { to: '/tenant/scheduled/tasks', label: '定时任务', testId: 'tenant-bulk-scheduled-scheduled-tasks-nav-menu' },
    ],
  },
  {
    id: 'template-management', label: '模板管理', items: [
      { to: '/tenant/templates', label: '我的模板', roles: ['TENANT_ADMIN'], testId: 'tenant-template-lifecycle-templates-nav-menu' },
    ],
  },
  {
    id: 'signature-management', label: '签名管理', items: [
      { to: '/tenant/signatures', label: '我的签名', roles: ['TENANT_ADMIN'], testId: 'tenant-signature-lifecycle-signatures-nav-menu' },
    ],
  },
  {
    id: 'account-management', label: '账户管理', items: [
      { to: '/tenant/recharge', label: '账户充值', roles: ['TENANT_ADMIN'], testId: 'tenant-recharge-operations-recharge-nav-menu' },
      { to: '/tenant/consumption-ledger', label: '消费账本', roles: ['TENANT_ADMIN', 'TENANT_USER'], testId: 'tenant-trial-prepaid-consumption-ledger-nav-menu' },
    ],
  },
  {
    id: 'configuration', label: '配置管理', items: [
      { to: '/tenant/config', label: '配置总览', roles: ['TENANT_ADMIN', 'TENANT_DEV'] },
      { to: '/tenant/webhooks', label: 'Webhook 回调', roles: ['TENANT_ADMIN', 'TENANT_DEV'], testId: 'tenant-webhook-delivery-webhooks-nav-menu' },
      { to: '/tenant/api/keys', label: 'API 密钥', roles: ['TENANT_ADMIN', 'TENANT_DEV'], testId: 'tenant-tenant-access-api-keys-nav-menu' },
      { to: '/tenant/cmpp/access', label: 'CMPP 接入', roles: ['TENANT_ADMIN', 'TENANT_DEV'], testId: 'tenant-tenant-access-cmpp-access-nav-menu' },
    ],
  },
  {
    id: 'uplink-query', label: '上行消息查询', items: [
      { to: '/tenant/uplink', label: '上行记录', roles: ['TENANT_ADMIN', 'TENANT_DEV'], testId: 'tenant-uplink-normalization-uplinks-nav-menu' },
      { to: '/tenant/unsubscribes', label: '退订合规', roles: ['TENANT_ADMIN', 'TENANT_DEV'], testId: 'tenant-unsubscribe-compliance-unsubscribes-nav-menu' },
    ],
  },
  {
    id: 'short-links', label: '短链管理', items: [
      { to: '/tenant/shortlink', label: '短链列表', roles: ['TENANT_ADMIN', 'TENANT_USER'] },
    ],
  },
];

/** Shared layout for every authenticated Tenant route. */
export default function TenantLayout() {
  const userType = useAuthStore((state) => state.userType);
  const clearSession = useAuthStore((state) => state.logout);
  const navigate = useNavigate();
  if (isPlatformRole(userType) || !userType) {
    return <Navigate to="/login" replace />;
  }

  const groups = NAV_GROUPS.map((group) => ({
    ...group,
    items: group.items.filter((item) => !item.roles || item.roles.includes(userType as TenantUserType)),
  })).filter((group) => group.items.length > 0);

  return (
    <div className="layout">
      <aside className="sidebar">
        <div className="sidebar-brand">YCSAN-SMS 机构端</div>
        <SidebarMenu ariaLabel="机构主导航" groups={groups} testIdPrefix="tenant-console-navigation" />
        <button className="sidebar-logout" data-testid="shared-console-identity-profile-logout" type="button" onClick={async () => {
          try { await revokeSession(); } finally { clearSession(); navigate('/login'); }
        }}>退出登录</button>
      </aside>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
