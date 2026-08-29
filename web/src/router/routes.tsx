import { createBrowserRouter, Navigate } from 'react-router-dom';
import AdminLayout from '@/components/layout/AdminLayout';
import TenantLayout from '@/components/layout/TenantLayout';
import PlaceholderPage from '@/components/common/PlaceholderPage';
import LoginPage from '@/pages/LoginPage';
import DashboardPage from '@/pages/admin/dashboard/DashboardPage';
import TenantListPage from '@/pages/admin/tenants/TenantListPage';
import ChannelListPage from '@/pages/admin/channels/ChannelListPage';
import OverviewPage from '@/pages/tenant/overview/OverviewPage';
import SendPage from '@/pages/tenant/send/SendPage';

/**
 * 路由树严格对齐 ycsansms.md 第 8 章 Web 管理端信息架构。
 * 已有真实页面实现的挂对应组件；其余按 F-x 编号挂 PlaceholderPage，
 * 保证"导航结构完整、可点击"，同时不假装未实现的页面已经完成——见 web/docs/ROADMAP.md。
 */
export const router = createBrowserRouter([
  { path: '/', element: <Navigate to="/login" replace /> },
  { path: '/login', element: <LoginPage /> },

  {
    path: '/admin',
    element: <AdminLayout />,
    children: [
      { index: true, element: <Navigate to="dashboard" replace /> },
      { path: 'dashboard', element: <DashboardPage /> },
      { path: 'tenants', element: <TenantListPage /> },
      { path: 'channels', element: <ChannelListPage /> },
      { path: 'audit', element: <PlaceholderPage title="审核中心（签名/模板/免审规则）" prdRef="F-3.2/F-3.5/F-3.6" /> },
      {
        path: 'riskcontrol',
        element: <PlaceholderPage title="验证规则（黑白名单/内容审核/频次/携号转网）" prdRef="F-5.1~F-5.7" />,
      },
      { path: 'complaints', element: <PlaceholderPage title="投诉管理" prdRef="F-9" /> },
      { path: 'uplink', element: <PlaceholderPage title="上行数据（含退订记录）" prdRef="F-10" /> },
      { path: 'records', element: <PlaceholderPage title="数据详单" prdRef="F-7" /> },
      { path: 'statistics', element: <PlaceholderPage title="数据统计" prdRef="F-11.1~F-11.4" /> },
      { path: 'finance', element: <PlaceholderPage title="财务中心" prdRef="F-8" /> },
      { path: 'alerts', element: <PlaceholderPage title="告警管理" prdRef="F-12" /> },
      { path: 'tools', element: <PlaceholderPage title="工具管理（短链/状态码/号段）" prdRef="F-13" /> },
      { path: 'system', element: <PlaceholderPage title="系统管理（账号/角色/日志）" prdRef="F-1/F-14" /> },
    ],
  },

  {
    path: '/tenant',
    element: <TenantLayout />,
    children: [
      { index: true, element: <Navigate to="overview" replace /> },
      { path: 'overview', element: <OverviewPage /> },
      { path: 'send', element: <SendPage /> },
      { path: 'templates', element: <PlaceholderPage title="模板管理" prdRef="F-3.4" /> },
      { path: 'signatures', element: <PlaceholderPage title="签名管理" prdRef="F-3.1" /> },
      { path: 'account', element: <PlaceholderPage title="账户管理" prdRef="F-8" /> },
      { path: 'config', element: <PlaceholderPage title="配置管理（黑名单/回调/API Key）" prdRef="F-2.6/F-5.2/F-6.6" /> },
      { path: 'uplink', element: <PlaceholderPage title="上行消息查询" prdRef="F-7.5/F-7.9" /> },
      { path: 'shortlink', element: <PlaceholderPage title="短链管理" prdRef="F-13.1/F-13.2" /> },
    ],
  },
]);
