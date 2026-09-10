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
import { REVIEW_HISTORY_PERMISSIONS } from '@/api/resourceReviewHistoryApi';
import { BLACKLIST_RISK_PERMISSIONS } from '@/api/blacklistRiskControlApi';
import { CONTENT_SAFETY_PERMISSIONS } from '@/api/contentSafetyApi';
import { FREQUENCY_PERMISSIONS } from '@/api/frequencyRuleApi';
import { NUMBER_ATTRIBUTION_PERMISSIONS } from '@/api/numberAttributionApi';
import { PROVIDER_STATUS_PERMISSIONS } from '@/api/providerStatusApi';
import { ROUTING_POLICY_PERMISSIONS } from '@/api/routingPolicyApi';
import { TRIAL_PREPAID_PERMISSIONS } from '@/api/trialPrepaidApi';
import { TENANT_RISK_PERMISSIONS } from '@/api/tenantRiskAutoPauseApi';
import { CUSTOM_REPORT_PERMISSIONS } from '@/api/customReportApi';
import { OPERATIONAL_DASHBOARD_PERMISSIONS } from '@/api/operationalDashboardApi';
import { SECURE_ASYNC_EXPORT_PERMISSIONS } from '@/api/secureAsyncExportApi';
import { RETENTION_ARCHIVE_PERMISSIONS } from '@/api/retentionArchiveApi';
import { SHORTLINK_PERMISSIONS } from '@/api/shortLinkApi';

const OPERATIONS: PlatformUserType[] = ['ADMIN', 'OPERATOR', 'FINANCE'];
const REVIEW_HISTORY_ROLES: PlatformUserType[] = ['ADMIN', 'OPERATOR'];
const NAV_ITEMS: Array<{ to: string; label: string; permissions?: string[]; roles?: PlatformUserType[] }> = [
  { to: '/admin/dashboard', label: '数据概览（仪表盘）', roles: ['ADMIN', 'OPERATOR', 'FINANCE'], permissions: [OPERATIONAL_DASHBOARD_PERMISSIONS.menu, OPERATIONAL_DASHBOARD_PERMISSIONS.read] },
  { to: '/admin/dashboard/configuration', label: '仪表盘配置', roles: ['ADMIN', 'OPERATOR'], permissions: [OPERATIONAL_DASHBOARD_PERMISSIONS.menu, OPERATIONAL_DASHBOARD_PERMISSIONS.write] },
  { to: '/admin/api/status', label: 'API 状态', roles: ['ADMIN', 'OPERATOR'], permissions: [OPERATIONAL_DASHBOARD_PERMISSIONS.menu, OPERATIONAL_DASHBOARD_PERMISSIONS.read] },
  { to: '/admin/tenants', label: '机构管理', roles: OPERATIONS, permissions: [TENANT_PERMISSIONS.menu, TENANT_PERMISSIONS.read] },
  { to: '/admin/channel/configuration', label: '通道管理', roles: ['ADMIN', 'OPERATOR'] },
  { to: '/admin/channel/health', label: '通道健康', roles: ['ADMIN', 'OPERATOR'] },
  { to: '/admin/channel/pools', label: '通道池', roles: ['ADMIN', 'OPERATOR'] },
  { to: '/admin/routing-policy', label: '路由策略', roles: ['ADMIN', 'OPERATOR'], permissions: [ROUTING_POLICY_PERMISSIONS.menu, ROUTING_POLICY_PERMISSIONS.read] },
  { to: '/admin/signatures/review', label: '签名审核', roles: OPERATIONS },
  { to: '/admin/templates/review', label: '模板审核', roles: OPERATIONS },
  { to: '/admin/exemption/policy', label: '豁免策略', roles: OPERATIONS, permissions: [EXEMPTION_PERMISSIONS.menu, EXEMPTION_PERMISSIONS.read] },
  { to: '/admin/review-history', label: '审核历史', roles: REVIEW_HISTORY_ROLES, permissions: [REVIEW_HISTORY_PERMISSIONS.menu, REVIEW_HISTORY_PERMISSIONS.read] },
  { to: '/admin/riskcontrol', label: '验证规则', roles: ['ADMIN', 'OPERATOR'], permissions: [BLACKLIST_RISK_PERMISSIONS.menu, BLACKLIST_RISK_PERMISSIONS.read] },
  { to: '/admin/content-safety', label: '内容审核', roles: ['ADMIN', 'OPERATOR'], permissions: [CONTENT_SAFETY_PERMISSIONS.menu, CONTENT_SAFETY_PERMISSIONS.read] },
  { to: '/admin/frequency/rules', label: '频控规则', roles: ['ADMIN', 'OPERATOR'], permissions: [FREQUENCY_PERMISSIONS.menu, FREQUENCY_PERMISSIONS.read] },
  { to: '/admin/number-attribution', label: '号码归属', roles: ['ADMIN', 'OPERATOR'], permissions: [NUMBER_ATTRIBUTION_PERMISSIONS.menu, NUMBER_ATTRIBUTION_PERMISSIONS.read] },
  { to: '/admin/number-portability', label: '携号转网', roles: ['ADMIN', 'OPERATOR'], permissions: [NUMBER_ATTRIBUTION_PERMISSIONS.menu, NUMBER_ATTRIBUTION_PERMISSIONS.read] },
  { to: '/admin/prefixes', label: '号段管理', roles: ['ADMIN', 'OPERATOR'], permissions: [NUMBER_ATTRIBUTION_PERMISSIONS.menu, NUMBER_ATTRIBUTION_PERMISSIONS.read] },
  { to: '/admin/status-codes', label: '状态码映射', roles: ['ADMIN', 'OPERATOR'], permissions: [PROVIDER_STATUS_PERMISSIONS.menu, PROVIDER_STATUS_PERMISSIONS.read] },
  { to: '/admin/tenant-trial-contracts', label: '试用配置', roles: ['ADMIN', 'OPERATOR'], permissions: [TRIAL_PREPAID_PERMISSIONS.menu, TRIAL_PREPAID_PERMISSIONS.read] },
  { to: '/admin/balance-audit', label: '余额审计', roles: ['ADMIN', 'OPERATOR', 'FINANCE'], permissions: [TRIAL_PREPAID_PERMISSIONS.menu, TRIAL_PREPAID_PERMISSIONS.read] },
  { to: '/admin/tenant-recharge-review', label: '充值审核', roles: ['ADMIN', 'FINANCE'], permissions: [TRIAL_PREPAID_PERMISSIONS.menu, TRIAL_PREPAID_PERMISSIONS.read] },
  { to: '/admin/reconciliation', label: '对账结算', roles: ['ADMIN', 'FINANCE'], permissions: [TRIAL_PREPAID_PERMISSIONS.menu, TRIAL_PREPAID_PERMISSIONS.read] },
  { to: '/admin/invoices', label: '发票管理', roles: ['ADMIN', 'FINANCE'], permissions: [TRIAL_PREPAID_PERMISSIONS.menu, TRIAL_PREPAID_PERMISSIONS.read] },
  { to: '/admin/complaints', label: '投诉管理', roles: OPERATIONS },
  { to: '/admin/complaint/analytics', label: '投诉分析', roles: OPERATIONS },
  { to: '/admin/tenant-risk', label: '机构风险', roles: OPERATIONS, permissions: [TENANT_RISK_PERMISSIONS.menu, TENANT_RISK_PERMISSIONS.read] },
  { to: '/admin/uplink', label: '上行数据', roles: OPERATIONS },
  { to: '/admin/unsubscribes', label: '退订合规', roles: OPERATIONS },
  { to: '/admin/records', label: '数据详单', roles: OPERATIONS },
  { to: '/admin/statistics', label: '数据统计', roles: ['ADMIN', 'FINANCE'] },
  { to: '/admin/statistics/resources', label: '资源统计', roles: ['ADMIN', 'OPERATOR', 'FINANCE'], permissions: [OPERATIONAL_DASHBOARD_PERMISSIONS.menu, OPERATIONAL_DASHBOARD_PERMISSIONS.read] },
  { to: '/admin/custom/reports', label: '自定义报表', roles: ['ADMIN', 'OPERATOR', 'FINANCE'], permissions: [CUSTOM_REPORT_PERMISSIONS.menu, CUSTOM_REPORT_PERMISSIONS.read] },
  { to: '/admin/export-center', label: '导出中心', roles: ['ADMIN', 'OPERATOR', 'FINANCE'], permissions: [SECURE_ASYNC_EXPORT_PERMISSIONS.menu, SECURE_ASYNC_EXPORT_PERMISSIONS.read] },
  { to: '/admin/archive', label: '保留归档', roles: ['ADMIN', 'OPERATOR', 'FINANCE'], permissions: [RETENTION_ARCHIVE_PERMISSIONS.menu, RETENTION_ARCHIVE_PERMISSIONS.read] },
  { to: '/admin/shortlinks/review', label: '短链审核', roles: ['ADMIN', 'OPERATOR'], permissions: [SHORTLINK_PERMISSIONS.reviewMenu, SHORTLINK_PERMISSIONS.reviewRead] },
  { to: '/admin/finance', label: '财务中心', roles: ['ADMIN', 'FINANCE'] },
  { to: '/admin/fee/warning', label: '费用预警', roles: ['ADMIN', 'FINANCE'] },
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
          item.to === '/admin/dashboard' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-operational-dashboards-dashboard-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/dashboard/configuration' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-operational-dashboards-dashboard-configuration-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/api/status' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-operational-dashboards-api-status-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/tenants' ? (
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
          ) : item.to === '/admin/routing-policy' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-routing-circuit-routing-policy-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
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
          ) : item.to === '/admin/review-history' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-resource-review-history-review-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/riskcontrol' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-blacklist-risk-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/content-safety' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-runtime-content-content-safety-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/frequency/rules' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-frequency-api-frequency-rules-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/number-attribution' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-number-attribution-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/number-portability' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-number-portability-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/prefixes' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-prefixes-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/status-codes' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-provider-status-taxonomy-status-codes-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/tenant-trial-contracts' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-trial-prepaid-tenant-trial-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/balance-audit' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-trial-prepaid-balance-audit-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/tenant-recharge-review' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-tenant-recharge-operations-review-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/reconciliation' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-reconciliation-settlement-reconciliation-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/invoices' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-reconciliation-settlement-invoices-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/complaints' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-complaint-case-complaints-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/complaint/analytics' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-complaint-case-analytics-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/tenant-risk' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-tenant-risk-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/statistics' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-financial-source-channel-statistics-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/statistics/resources' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-operational-dashboards-statistics-resources-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/custom/reports' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-custom-report-custom-reports-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/archive' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-retention-archive-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/shortlinks/review' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-shortlink-safety-review-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/finance' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-financial-source-financial-analytics-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/fee/warning' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-fee-warning-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/uplink' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-uplink-normalization-uplinks-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/unsubscribes' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-unsubscribe-compliance-unsubscribes-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ) : item.to === '/admin/alerts' ? (
            <NavLink key={item.to} to={item.to} data-testid="admin-alert-engine-alerts-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>
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
