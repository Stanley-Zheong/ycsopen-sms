import { createBrowserRouter, Navigate } from 'react-router-dom';
import AdminLayout from '@/components/layout/AdminLayout';
import TenantLayout from '@/components/layout/TenantLayout';
import PlaceholderPage from '@/components/common/PlaceholderPage';
import LoginPage from '@/pages/LoginPage';
import DashboardPage from '@/pages/admin/dashboard/DashboardPage';
import ApiStatusPage from '@/pages/admin/dashboard/ApiStatusPage';
import DashboardConfigurationPage from '@/pages/admin/dashboard/DashboardConfigurationPage';
import ResourceStatisticsPage from '@/pages/admin/dashboard/ResourceStatisticsPage';
import TenantListPage from '@/pages/admin/tenants/TenantListPage';
import OverviewPage from '@/pages/tenant/overview/OverviewPage';
import SendPage from '@/pages/tenant/send/SendPage';
import ProtectedRoute from './ProtectedRoute';
import UserManagementPage from '@/pages/admin/identity/UserManagementPage';
import RoleManagementPage from '@/pages/admin/identity/RoleManagementPage';
import AccountOverviewPage from '@/pages/admin/identity/AccountOverviewPage';
import LoginHistoryPage from '@/pages/admin/identity/LoginHistoryPage';
import OperationAuditPage from '@/pages/admin/security/OperationAuditPage';
import SecurityEventsPage from '@/pages/admin/security/SecurityEventsPage';
import SystemConfigurationPage from '@/pages/admin/system/SystemConfigurationPage';
import TenantRegistrationPage from '@/pages/tenant/TenantRegistrationPage';
import TenantQualificationPage from '@/pages/tenant/TenantQualificationPage';
import TenantAdministratorsPage from '@/pages/tenant/TenantAdministratorsPage';
import TenantApiKeysPage from '@/pages/tenant/TenantApiKeysPage';
import TenantCmppAccessPage from '@/pages/tenant/TenantCmppAccessPage';
import ChannelConfigurationPage from '@/pages/admin/channels/ChannelConfigurationPage';
import ChannelHealthPage from '@/pages/admin/channels/ChannelHealthPage';
import ChannelPoolsPage from '@/pages/admin/channels/ChannelPoolsPage';
import RoutingPolicyPage from '@/pages/admin/channels/RoutingPolicyPage';
import SignatureReviewPage from '@/pages/admin/signatures/SignatureReviewPage';
import SignatureLifecyclePage from '@/pages/tenant/signatures/SignatureLifecyclePage';
import TemplateReviewPage from '@/pages/admin/templates/TemplateReviewPage';
import TemplateLifecyclePage from '@/pages/tenant/templates/TemplateLifecyclePage';
import TenantTemplateStatisticsPage from '@/pages/tenant/templates/TenantTemplateStatisticsPage';
import ExemptionPolicyPage from '@/pages/admin/exemptions/ExemptionPolicyPage';
import ResourceReviewHistoryPage from '@/pages/admin/review/ResourceReviewHistoryPage';
import BlacklistRiskControlPage from '@/pages/admin/risk/BlacklistRiskControlPage';
import ContentSafetyPage from '@/pages/admin/risk/ContentSafetyPage';
import FrequencyRulesPage from '@/pages/admin/risk/FrequencyRulesPage';
import NumberAttributionPage from '@/pages/admin/tools/NumberAttributionPage';
import ProviderStatusPage from '@/pages/admin/tools/ProviderStatusPage';
import TrialPrepaidAdminPage from '@/pages/admin/billing/TrialPrepaidAdminPage';
import TenantConsumptionLedgerPage from '@/pages/tenant/ledger/TenantConsumptionLedgerPage';
import TenantRechargePage from '@/pages/tenant/recharge/TenantRechargePage';
import MessageOperationsPage from '@/pages/admin/records/MessageOperationsPage';
import TenantWebhooksPage from '@/pages/tenant/webhooks/TenantWebhooksPage';
import AdminPushFailuresPage from '@/pages/admin/webhooks/AdminPushFailuresPage';
import TenantBulkSendPage from '@/pages/tenant/bulk/TenantBulkSendPage';
import TenantScheduledTasksPage from '@/pages/tenant/bulk/TenantScheduledTasksPage';
import AdminBulkDetailsPage from '@/pages/admin/bulk/AdminBulkDetailsPage';
import AdminSendJobsPage from '@/pages/admin/bulk/AdminSendJobsPage';
import AdminUplinksPage from '@/pages/admin/uplinks/AdminUplinksPage';
import TenantUplinksPage from '@/pages/tenant/uplinks/TenantUplinksPage';
import AdminUnsubscribesPage from '@/pages/admin/unsubscribes/AdminUnsubscribesPage';
import TenantUnsubscribesPage from '@/pages/tenant/unsubscribes/TenantUnsubscribesPage';
import AdminAlertsPage from '@/pages/admin/alerts/AdminAlertsPage';
import AdminRechargeReviewPage from '@/pages/admin/billing/AdminRechargeReviewPage';
import AdminReconciliationSettlementPage from '@/pages/admin/billing/AdminReconciliationSettlementPage';
import TenantStatementsInvoicesPage from '@/pages/tenant/billing/TenantStatementsInvoicesPage';
import AdminFinancialAnalyticsPage from '@/pages/admin/billing/AdminFinancialAnalyticsPage';
import AdminFeeWarningPage from '@/pages/admin/billing/AdminFeeWarningPage';
import TenantFeeWarningPage from '@/pages/tenant/billing/TenantFeeWarningPage';
import AdminComplaintsPage from '@/pages/admin/complaints/AdminComplaintsPage';
import AdminComplaintAnalyticsPage from '@/pages/admin/complaints/AdminComplaintAnalyticsPage';
import AdminTenantRiskPage from '@/pages/admin/risk/AdminTenantRiskPage';
import AdminCustomReportsPage from '@/pages/admin/reports/AdminCustomReportsPage';
import AdminExportCenterPage from '@/pages/admin/exports/AdminExportCenterPage';
import AdminRetentionArchivePage from '@/pages/admin/archive/AdminRetentionArchivePage';
import TenantShortLinkPage from '@/pages/tenant/shortlinks/TenantShortLinkPage';
import AdminShortLinkReviewPage from '@/pages/admin/shortlinks/AdminShortLinkReviewPage';
import PublicShortLinkSafePage from '@/pages/public/PublicShortLinkSafePage';
import AdminTenantTerminationPage from '@/pages/admin/tenants/AdminTenantTerminationPage';

/**
 * 路由树严格对齐 ycsansms.md 第 8 章 Web 管理端信息架构。
 * 已有真实页面实现的挂对应组件；其余按 F-x 编号挂 PlaceholderPage，
 * 保证"导航结构完整、可点击"，同时不假装未实现的页面已经完成——见 web/docs/ROADMAP.md。
 */
export const router = createBrowserRouter([
  { path: '/', element: <Navigate to="/login" replace /> },
  { path: '/login', element: <LoginPage /> },
  { path: '/admin/auth/login', element: <LoginPage /> },
  { path: '/s/:code', element: <PublicShortLinkSafePage /> },
  { path: '/admin/users', element: <Navigate to="/admin/system/users" replace /> },
  { path: '/admin/roles', element: <Navigate to="/admin/system/roles" replace /> },
  { path: '/tenant/register', element: <TenantRegistrationPage /> },

  {
    path: '/admin',
    element: (
      <ProtectedRoute audience="platform">
        <AdminLayout />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <Navigate to="/admin/dashboard" replace /> },
      { path: '/admin/dashboard', element: <DashboardPage /> },
      { path: '/admin/dashboard/configuration', element: <DashboardConfigurationPage /> },
      { path: '/admin/api/status', element: <ApiStatusPage /> },
      { path: '/admin/tenants', element: <TenantListPage /> },
      { path: 'channels', element: <Navigate to="/admin/channel/configuration" replace /> },
      { path: '/admin/channel/configuration', element: <ChannelConfigurationPage /> },
      { path: '/admin/channel/health', element: <ChannelHealthPage /> },
      { path: '/admin/channel/pools', element: <ChannelPoolsPage /> },
      { path: '/admin/routing-policy', element: <RoutingPolicyPage /> },
      { path: 'audit', element: <Navigate to="/admin/signatures/review" replace /> },
      { path: '/admin/signatures/review', element: <SignatureReviewPage /> },
      { path: '/admin/templates/review', element: <TemplateReviewPage /> },
      { path: '/admin/exemption/policy', element: <ExemptionPolicyPage /> },
      { path: '/admin/review-history', element: <ResourceReviewHistoryPage /> },
      { path: '/admin/riskcontrol', element: <BlacklistRiskControlPage /> },
      { path: '/admin/content-safety', element: <ContentSafetyPage /> },
      { path: '/admin/frequency/rules', element: <FrequencyRulesPage /> },
      { path: '/admin/number-attribution', element: <NumberAttributionPage /> },
      { path: '/admin/number-portability', element: <NumberAttributionPage /> },
      { path: '/admin/prefixes', element: <NumberAttributionPage /> },
      { path: '/admin/status-codes', element: <ProviderStatusPage /> },
      { path: '/admin/tenant-trial-contracts', element: <TrialPrepaidAdminPage /> },
      { path: '/admin/balance-audit', element: <TrialPrepaidAdminPage /> },
      { path: '/admin/tenant-recharge-review', element: <AdminRechargeReviewPage /> },
      { path: '/admin/reconciliation', element: <AdminReconciliationSettlementPage /> },
      { path: '/admin/settlements', element: <AdminReconciliationSettlementPage /> },
      { path: '/admin/invoices', element: <AdminReconciliationSettlementPage /> },
      { path: '/admin/submission/details', element: <MessageOperationsPage initialSection="submissions" /> },
      { path: '/admin/send/details', element: <MessageOperationsPage initialSection="sends" /> },
      { path: '/admin/receipt/details', element: <MessageOperationsPage initialSection="receipts" /> },
      { path: '/admin/error/details', element: <MessageOperationsPage initialSection="errors" /> },
      { path: '/admin/push/failures', element: <AdminPushFailuresPage /> },
      { path: '/admin/bulk/details', element: <AdminBulkDetailsPage /> },
      { path: '/admin/send/jobs', element: <AdminSendJobsPage /> },
      { path: '/admin/complaints', element: <AdminComplaintsPage /> },
      { path: '/admin/complaint/analytics', element: <AdminComplaintAnalyticsPage /> },
      { path: '/admin/tenant-risk', element: <AdminTenantRiskPage /> },
      { path: '/admin/uplink', element: <AdminUplinksPage /> },
      { path: '/admin/unsubscribes', element: <AdminUnsubscribesPage /> },
      { path: 'records', element: <PlaceholderPage title="数据详单" prdRef="F-7" /> },
      { path: '/admin/statistics', element: <AdminFinancialAnalyticsPage /> },
      { path: '/admin/statistics/resources', element: <ResourceStatisticsPage /> },
      { path: '/admin/custom/reports', element: <AdminCustomReportsPage /> },
      { path: '/admin/export-center', element: <AdminExportCenterPage /> },
      { path: '/admin/archive', element: <AdminRetentionArchivePage /> },
      { path: '/admin/shortlinks/review', element: <AdminShortLinkReviewPage /> },
      { path: '/admin/tenant/terminations', element: <AdminTenantTerminationPage /> },
      { path: '/admin/finance', element: <AdminFinancialAnalyticsPage /> },
      { path: '/admin/fee/warning', element: <AdminFeeWarningPage /> },
      { path: '/admin/alerts', element: <AdminAlertsPage /> },
      { path: 'tools', element: <PlaceholderPage title="工具管理（短链/状态码/号段）" prdRef="F-13" /> },
      { path: 'system', element: <Navigate to="users" replace /> },
      { path: '/admin/system/users', element: <UserManagementPage /> },
      { path: '/admin/system/roles', element: <RoleManagementPage /> },
      { path: '/admin/system/login-history', element: <LoginHistoryPage /> },
      { path: '/admin/system/logs', element: <OperationAuditPage /> },
      { path: '/admin/system/security-events', element: <SecurityEventsPage /> },
      { path: '/admin/system/configuration', element: <SystemConfigurationPage /> },
      { path: '/admin/account-overview', element: <AccountOverviewPage /> },
    ],
  },

  {
    path: '/tenant',
    element: (
      <ProtectedRoute audience="tenant">
        <TenantLayout />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <Navigate to="overview" replace /> },
      { path: '/tenant/overview', element: <OverviewPage /> },
      { path: '/tenant/send', element: <SendPage /> },
      { path: '/tenant/bulk/send', element: <TenantBulkSendPage /> },
      { path: '/tenant/scheduled/tasks', element: <TenantScheduledTasksPage /> },
      { path: '/tenant/templates', element: <TemplateLifecyclePage /> },
      { path: '/tenant/templates/statistics', element: <TenantTemplateStatisticsPage /> },
      { path: '/tenant/signatures', element: <SignatureLifecyclePage /> },
      { path: '/tenant/recharge', element: <TenantRechargePage /> },
      { path: '/tenant/balance', element: <TenantFeeWarningPage /> },
      { path: '/tenant/statements', element: <TenantStatementsInvoicesPage /> },
      { path: '/tenant/invoices', element: <TenantStatementsInvoicesPage /> },
      { path: 'account', element: <Navigate to="/tenant/recharge" replace /> },
      { path: '/tenant/consumption-ledger', element: <TenantConsumptionLedgerPage /> },
      { path: 'config', element: <PlaceholderPage title="配置管理（黑名单/回调/API Key）" prdRef="F-2.6/F-5.2/F-6.6" /> },
      { path: '/tenant/uplink', element: <TenantUplinksPage /> },
      { path: '/tenant/unsubscribes', element: <TenantUnsubscribesPage /> },
      { path: '/tenant/shortlink', element: <TenantShortLinkPage /> },
      { path: '/tenant/qualification', element: <TenantQualificationPage /> },
      { path: '/tenant/administrators', element: <TenantAdministratorsPage /> },
      { path: '/tenant/api/keys', element: <TenantApiKeysPage /> },
      { path: '/tenant/cmpp/access', element: <TenantCmppAccessPage /> },
      { path: '/tenant/webhooks', element: <TenantWebhooksPage /> },
    ],
  },
]);
