import { NavLink, Outlet, Navigate } from 'react-router-dom';
import { useAuthStore, isPlatformRole } from '@/store/authStore';

const NAV_ITEMS: Array<{ to: string; label: string }> = [
  { to: '/admin/dashboard', label: '数据概览（仪表盘）' },
  { to: '/admin/tenants', label: '机构管理' },
  { to: '/admin/channels', label: '通道管理' },
  { to: '/admin/audit', label: '审核中心' },
  { to: '/admin/riskcontrol', label: '验证规则' },
  { to: '/admin/complaints', label: '投诉管理' },
  { to: '/admin/uplink', label: '上行数据' },
  { to: '/admin/records', label: '数据详单' },
  { to: '/admin/statistics', label: '数据统计' },
  { to: '/admin/finance', label: '财务中心' },
  { to: '/admin/alerts', label: '告警管理' },
  { to: '/admin/tools', label: '工具管理' },
  { to: '/admin/system', label: '系统管理' },
];

/** 平台管理后台整体布局，导航结构与 ycsansms.md 8.1 节一一对应。 */
export default function AdminLayout() {
  const userType = useAuthStore((s) => s.userType);
  if (!isPlatformRole(userType)) {
    return <Navigate to="/login" replace />;
  }
  return (
    <div className="layout">
      <nav className="sidebar">
        <div style={{ fontWeight: 700, marginBottom: 16 }}>YCSAN-SMS 平台管理后台</div>
        {NAV_ITEMS.map((item) => (
          <NavLink key={item.to} to={item.to} className={({ isActive }) => (isActive ? 'active' : '')}>
            {item.label}
          </NavLink>
        ))}
      </nav>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
