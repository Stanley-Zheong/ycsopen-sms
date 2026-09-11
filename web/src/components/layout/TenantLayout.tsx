import { NavLink, Outlet, Navigate, useNavigate } from 'react-router-dom';
import { useAuthStore, isPlatformRole } from '@/store/authStore';
import { logout as revokeSession } from '@/api/auth';

const NAV_ITEMS: Array<{ to: string; label: string; adminOnly?: boolean }> = [
  { to: '/tenant/overview', label: '概览 / 账户总览' },
  { to: '/tenant/send', label: '发送管理' },
  { to: '/tenant/templates', label: '模板管理' },
  { to: '/tenant/signatures', label: '签名管理' },
  { to: '/tenant/recharge', label: '账户充值' },
  { to: '/tenant/balance', label: '余额与费用预警' },
  { to: '/tenant/statements', label: '对账单' },
  { to: '/tenant/invoices', label: '发票申请' },
  { to: '/tenant/consumption-ledger', label: '消费账本' },
  { to: '/tenant/config', label: '配置管理' },
  { to: '/tenant/uplink', label: '上行消息查询' },
  { to: '/tenant/unsubscribes', label: '退订合规' },
  { to: '/tenant/shortlink', label: '短链管理' },
  { to: '/tenant/qualification', label: '资质认证', adminOnly: true },
  { to: '/tenant/administrators', label: '子账号管理', adminOnly: true },
  { to: '/tenant/api/keys', label: 'API 密钥' },
  { to: '/tenant/cmpp/access', label: 'CMPP 接入' },
];

/** 机构端整体布局，导航结构与 ycsansms.md 8.2 节一一对应。 */
export default function TenantLayout() {
  const userType = useAuthStore((s) => s.userType);
  const clearSession = useAuthStore((s) => s.logout);
  const navigate = useNavigate();
  if (isPlatformRole(userType) || !userType) {
    return <Navigate to="/login" replace />;
  }
  return (
    <div className="layout">
      <nav className="sidebar">
        <div style={{ fontWeight: 700, marginBottom: 16 }}>YCSAN-SMS 机构端</div>
        {NAV_ITEMS.filter((item) => !item.adminOnly || userType === 'TENANT_ADMIN').map((item) => (
          item.to === '/tenant/qualification' ? (
            <NavLink key={item.to} to={item.to} data-testid="tenant-tenant-qualification-qualification-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>{item.label}</NavLink>
          ) : item.to === '/tenant/signatures' ? (
            <NavLink key={item.to} to={item.to} data-testid="tenant-signature-lifecycle-signatures-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>{item.label}</NavLink>
          ) : item.to === '/tenant/templates' ? (
            <NavLink key={item.to} to={item.to} data-testid="tenant-template-lifecycle-templates-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>{item.label}</NavLink>
          ) : item.to === '/tenant/consumption-ledger' ? (
            <NavLink key={item.to} to={item.to} data-testid="tenant-trial-prepaid-consumption-ledger-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>{item.label}</NavLink>
          ) : item.to === '/tenant/recharge' ? (
            <NavLink key={item.to} to={item.to} data-testid="tenant-recharge-operations-recharge-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>{item.label}</NavLink>
          ) : item.to === '/tenant/balance' ? (
            <NavLink key={item.to} to={item.to} data-testid="tenant-fee-warning-overview-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>{item.label}</NavLink>
          ) : item.to === '/tenant/statements' ? (
            <NavLink key={item.to} to={item.to} data-testid="tenant-reconciliation-settlement-statements-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>{item.label}</NavLink>
          ) : item.to === '/tenant/invoices' ? (
            <NavLink key={item.to} to={item.to} data-testid="tenant-reconciliation-settlement-invoices-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>{item.label}</NavLink>
          ) : item.to === '/tenant/uplink' ? (
            <NavLink key={item.to} to={item.to} data-testid="tenant-uplink-normalization-uplinks-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>{item.label}</NavLink>
          ) : item.to === '/tenant/unsubscribes' ? (
            <NavLink key={item.to} to={item.to} data-testid="tenant-unsubscribe-compliance-unsubscribes-nav-menu" className={({ isActive }) => (isActive ? 'active' : '')}>{item.label}</NavLink>
          ) : (
            <NavLink key={item.to} to={item.to} className={({ isActive }) => (isActive ? 'active' : '')}>{item.label}</NavLink>
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
