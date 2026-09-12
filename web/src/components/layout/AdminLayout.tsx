import { Navigate, Outlet, useNavigate } from 'react-router-dom';
import { useAuthStore, isPlatformRole } from '@/store/authStore';
import type { PlatformUserType } from '@/api/identity';
import { logout as revokeSession } from '@/api/auth';
import { IDENTITY_PERMISSIONS } from '@/api/identity';
import { useIdentityAccess } from '@/pages/admin/identity/useIdentityAccess';
import { AUDIT_PERMISSIONS } from '@/api/audit';
import { SYSTEM_CONFIGURATION_PERMISSIONS } from '@/api/systemConfiguration';
import { TENANT_PERMISSIONS } from '@/api/tenantQualificationApi';
import { EXEMPTION_PERMISSIONS } from '@/api/exemptionPolicyApi';
import { REVIEW_HISTORY_PERMISSIONS } from '@/api/resourceReviewHistoryApi';
import { BLACKLIST_RISK_PERMISSIONS } from '@/api/blacklistRiskControlApi';
import { CONTENT_SAFETY_PERMISSIONS } from '@/api/contentSafetyApi';
import { FREQUENCY_PERMISSIONS } from '@/api/frequencyRuleApi';
import { NUMBER_ATTRIBUTION_PERMISSIONS } from '@/api/numberAttributionApi';
import { PROVIDER_STATUS_PERMISSIONS } from '@/api/providerStatusApi';
import { ROUTING_POLICY_PERMISSIONS } from '@/api/routingPolicyApi';
import { TRIAL_PREPAID_PERMISSIONS } from '@/api/trialPrepaidApi';
import SidebarMenu, { type SidebarMenuGroup, type SidebarMenuItem } from './SidebarMenu';

const OPERATIONS: PlatformUserType[] = ['ADMIN', 'OPERATOR', 'FINANCE'];
const REVIEW_HISTORY_ROLES: PlatformUserType[] = ['ADMIN', 'OPERATOR'];

interface AdminNavItem extends SidebarMenuItem {
  permissions?: string[];
  roles?: PlatformUserType[];
}

interface AdminNavGroup extends Omit<SidebarMenuGroup, 'items'> {
  items: AdminNavItem[];
}

/** The two levels follow the Admin information architecture in PRD section 8.1. */
const NAV_GROUPS: AdminNavGroup[] = [
  {
    id: 'overview', label: '数据概览', items: [
      { to: '/admin/dashboard', label: '仪表盘', roles: OPERATIONS },
    ],
  },
  {
    id: 'tenant-management', label: '机构管理', items: [
      { to: '/admin/tenants', label: '机构列表', roles: OPERATIONS, permissions: [TENANT_PERMISSIONS.menu, TENANT_PERMISSIONS.read], testId: 'admin-tenant-qualification-tenants-nav-menu' },
      { to: '/admin/tenant-trial-contracts', label: '试用配置', roles: ['ADMIN', 'OPERATOR'], permissions: [TRIAL_PREPAID_PERMISSIONS.menu, TRIAL_PREPAID_PERMISSIONS.read], testId: 'admin-trial-prepaid-tenant-trial-nav-menu' },
      { to: '/admin/tenant-recharge-review', label: '充值审核', roles: ['ADMIN', 'FINANCE'], permissions: [TRIAL_PREPAID_PERMISSIONS.menu, TRIAL_PREPAID_PERMISSIONS.read], testId: 'admin-tenant-recharge-operations-review-nav-menu' },
    ],
  },
  {
    id: 'channel-management', label: '通道管理', items: [
      { to: '/admin/channel/configuration', label: '通道配置', roles: ['ADMIN', 'OPERATOR'], testId: 'admin-channel-configuration-nav-menu' },
      { to: '/admin/channel/health', label: '通道健康', roles: ['ADMIN', 'OPERATOR'], testId: 'admin-channel-health-nav-menu' },
      { to: '/admin/channel/pools', label: '通道池', roles: ['ADMIN', 'OPERATOR'], testId: 'admin-channel-health-pools-nav-menu' },
      { to: '/admin/routing-policy', label: '路由策略', roles: ['ADMIN', 'OPERATOR'], permissions: [ROUTING_POLICY_PERMISSIONS.menu, ROUTING_POLICY_PERMISSIONS.read], testId: 'admin-routing-circuit-routing-policy-nav-menu' },
    ],
  },
  {
    id: 'review-center', label: '审核中心', items: [
      { to: '/admin/signatures/review', label: '签名审核', roles: OPERATIONS, testId: 'admin-signature-lifecycle-signature-review-nav-menu' },
      { to: '/admin/templates/review', label: '模板审核', roles: OPERATIONS, testId: 'admin-template-lifecycle-template-review-nav-menu' },
      { to: '/admin/exemption/policy', label: '豁免策略', roles: OPERATIONS, permissions: [EXEMPTION_PERMISSIONS.menu, EXEMPTION_PERMISSIONS.read], testId: 'admin-auditable-exemption-exemption-policy-nav-menu' },
      { to: '/admin/review-history', label: '审核历史', roles: REVIEW_HISTORY_ROLES, permissions: [REVIEW_HISTORY_PERMISSIONS.menu, REVIEW_HISTORY_PERMISSIONS.read], testId: 'admin-resource-review-history-review-nav-menu' },
    ],
  },
  {
    id: 'validation-rules', label: '验证规则', items: [
      { to: '/admin/riskcontrol', label: '黑白名单', roles: ['ADMIN', 'OPERATOR'], permissions: [BLACKLIST_RISK_PERMISSIONS.menu, BLACKLIST_RISK_PERMISSIONS.read], testId: 'admin-blacklist-risk-nav-menu' },
      { to: '/admin/content-safety', label: '内容审核', roles: ['ADMIN', 'OPERATOR'], permissions: [CONTENT_SAFETY_PERMISSIONS.menu, CONTENT_SAFETY_PERMISSIONS.read], testId: 'admin-runtime-content-content-safety-nav-menu' },
      { to: '/admin/frequency/rules', label: '频控规则', roles: ['ADMIN', 'OPERATOR'], permissions: [FREQUENCY_PERMISSIONS.menu, FREQUENCY_PERMISSIONS.read], testId: 'admin-frequency-api-frequency-rules-nav-menu' },
      { to: '/admin/number-attribution', label: '号码归属', roles: ['ADMIN', 'OPERATOR'], permissions: [NUMBER_ATTRIBUTION_PERMISSIONS.menu, NUMBER_ATTRIBUTION_PERMISSIONS.read], testId: 'admin-number-attribution-nav-menu' },
      { to: '/admin/number-portability', label: '携号转网', roles: ['ADMIN', 'OPERATOR'], permissions: [NUMBER_ATTRIBUTION_PERMISSIONS.menu, NUMBER_ATTRIBUTION_PERMISSIONS.read], testId: 'admin-number-portability-nav-menu' },
    ],
  },
  {
    id: 'complaints', label: '投诉管理', items: [
      { to: '/admin/complaints', label: '投诉列表', roles: OPERATIONS },
    ],
  },
  {
    id: 'uplink-data', label: '上行数据', items: [
      { to: '/admin/uplink', label: '上行消息', roles: OPERATIONS, testId: 'admin-uplink-normalization-uplinks-nav-menu' },
      { to: '/admin/unsubscribes', label: '退订合规', roles: OPERATIONS, testId: 'admin-unsubscribe-compliance-unsubscribes-nav-menu' },
    ],
  },
  {
    id: 'message-details', label: '数据详单', items: [
      { to: '/admin/records', label: '详单总览', roles: OPERATIONS },
      { to: '/admin/submission/details', label: '提交详单', roles: OPERATIONS, testId: 'admin-message-operations-submission-details-nav-menu' },
      { to: '/admin/send/details', label: '发送详单', roles: OPERATIONS, testId: 'admin-message-operations-send-details-nav-menu' },
      { to: '/admin/receipt/details', label: '回执详单', roles: OPERATIONS, testId: 'admin-message-operations-receipt-details-nav-menu' },
      { to: '/admin/error/details', label: '错误详单', roles: OPERATIONS, testId: 'admin-message-operations-error-details-nav-menu' },
      { to: '/admin/bulk/details', label: '群发详单', roles: OPERATIONS, testId: 'admin-bulk-scheduled-bulk-details-nav-menu' },
    ],
  },
  {
    id: 'statistics', label: '数据统计', items: [
      { to: '/admin/statistics', label: '统计总览', roles: OPERATIONS },
    ],
  },
  {
    id: 'finance', label: '财务中心', items: [
      { to: '/admin/finance', label: '财务总览', roles: ['ADMIN', 'FINANCE'] },
      { to: '/admin/balance-audit', label: '余额审计', roles: OPERATIONS, permissions: [TRIAL_PREPAID_PERMISSIONS.menu, TRIAL_PREPAID_PERMISSIONS.read], testId: 'admin-trial-prepaid-balance-audit-nav-menu' },
    ],
  },
  {
    id: 'alerts', label: '告警管理', items: [
      { to: '/admin/alerts', label: '告警规则与历史', roles: OPERATIONS, testId: 'admin-alert-engine-alerts-nav-menu' },
      { to: '/admin/push/failures', label: '推送失败记录', roles: OPERATIONS, testId: 'admin-webhook-delivery-push-failures-nav-menu' },
    ],
  },
  {
    id: 'tools', label: '工具管理', items: [
      { to: '/admin/tools', label: '工具总览', roles: OPERATIONS },
      { to: '/admin/status-codes', label: '状态码映射', roles: ['ADMIN', 'OPERATOR'], permissions: [PROVIDER_STATUS_PERMISSIONS.menu, PROVIDER_STATUS_PERMISSIONS.read], testId: 'admin-provider-status-taxonomy-status-codes-nav-menu' },
      { to: '/admin/prefixes', label: '号段管理', roles: ['ADMIN', 'OPERATOR'], permissions: [NUMBER_ATTRIBUTION_PERMISSIONS.menu, NUMBER_ATTRIBUTION_PERMISSIONS.read], testId: 'admin-prefixes-nav-menu' },
      { to: '/admin/send/jobs', label: '发送任务', roles: OPERATIONS, testId: 'admin-bulk-scheduled-send-jobs-nav-menu' },
    ],
  },
  {
    id: 'system', label: '系统管理', items: [
      { to: '/admin/system/users', label: '系统账号', permissions: [IDENTITY_PERMISSIONS.identityMenu, IDENTITY_PERMISSIONS.usersRead] },
      { to: '/admin/system/roles', label: '系统角色', permissions: [IDENTITY_PERMISSIONS.identityMenu, IDENTITY_PERMISSIONS.rolesRead] },
      { to: '/admin/system/login-history', label: '登录历史', permissions: [IDENTITY_PERMISSIONS.identityMenu, IDENTITY_PERMISSIONS.historyRead] },
      { to: '/admin/system/logs', label: '操作日志', permissions: [AUDIT_PERMISSIONS.operationsRead] },
      { to: '/admin/system/security-events', label: '安全事件', permissions: [AUDIT_PERMISSIONS.securityEventsRead] },
      { to: '/admin/system/configuration', label: '系统配置', permissions: [SYSTEM_CONFIGURATION_PERMISSIONS.menu, SYSTEM_CONFIGURATION_PERMISSIONS.read], testId: 'admin-platform-system-configuration-nav-menu' },
      { to: '/admin/account-overview', label: '账号概览' },
    ],
  },
];

/** Shared layout for every authenticated Admin route. */
export default function AdminLayout() {
  const userType = useAuthStore((state) => state.userType);
  const clearSession = useAuthStore((state) => state.logout);
  const navigate = useNavigate();
  const access = useIdentityAccess(isPlatformRole(userType));
  if (!isPlatformRole(userType)) {
    return <Navigate to="/login" replace />;
  }

  const groups = NAV_GROUPS.map((group) => ({
    ...group,
    items: group.items.filter((item) => (
      (!item.roles || item.roles.includes(userType as PlatformUserType))
      && (!item.permissions || item.permissions.every(access.can))
    )),
  })).filter((group) => group.items.length > 0);

  return (
    <div className="layout">
      <aside className="sidebar">
        <div className="sidebar-brand">YCSAN-SMS 平台管理后台</div>
        <SidebarMenu ariaLabel="平台主导航" groups={groups} testIdPrefix="admin-console-navigation" />
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
