import { NavLink, Outlet, Navigate } from 'react-router-dom';
import { useAuthStore, isPlatformRole } from '@/store/authStore';

const NAV_ITEMS: Array<{ to: string; label: string }> = [
  { to: '/tenant/overview', label: '概览 / 账户总览' },
  { to: '/tenant/send', label: '发送管理' },
  { to: '/tenant/templates', label: '模板管理' },
  { to: '/tenant/signatures', label: '签名管理' },
  { to: '/tenant/account', label: '账户管理' },
  { to: '/tenant/config', label: '配置管理' },
  { to: '/tenant/uplink', label: '上行消息查询' },
  { to: '/tenant/shortlink', label: '短链管理' },
];

/** 机构端整体布局，导航结构与 ycsansms.md 8.2 节一一对应。 */
export default function TenantLayout() {
  const userType = useAuthStore((s) => s.userType);
  if (isPlatformRole(userType) || !userType) {
    return <Navigate to="/login" replace />;
  }
  return (
    <div className="layout">
      <nav className="sidebar">
        <div style={{ fontWeight: 700, marginBottom: 16 }}>YCSAN-SMS 机构端</div>
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
