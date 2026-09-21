import { FormEvent, useState } from 'react';
import { isAxiosError } from 'axios';
import { useNavigate } from 'react-router-dom';
import { login } from '@/api/auth';
import { isPlatformRole, useAuthStore } from '@/store/authStore';

export const REMEMBERED_USERNAME_STORAGE_KEY = 'ycsopen.console.remembered-username';
const CONTROLLED_AUTH_MESSAGES = new Set([
  '账号已被锁定，请联系管理员解锁',
  '账号已被禁用',
  '密码已过期，请联系管理员更新',
  '账号有效期已结束，请联系管理员',
]);

function readRememberedUsername(): string {
  if (typeof window === 'undefined') {
    return '';
  }
  return window.localStorage.getItem(REMEMBERED_USERNAME_STORAGE_KEY) ?? '';
}

/** F-1.4 控制台登录，登录成功后按 user_type 分流进入平台管理后台或机构端。 */
export default function LoginPage() {
  const rememberedUsername = readRememberedUsername();
  const [username, setUsername] = useState(rememberedUsername);
  const [password, setPassword] = useState('');
  const [rememberUsername, setRememberUsername] = useState(Boolean(rememberedUsername));
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const setSession = useAuthStore((s) => s.setSession);
  const navigate = useNavigate();

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setPending(true);
    try {
      const session = await login(username, password);
      setSession(session);
      if (rememberUsername) {
        window.localStorage.setItem(REMEMBERED_USERNAME_STORAGE_KEY, username);
      } else {
        window.localStorage.removeItem(REMEMBERED_USERNAME_STORAGE_KEY);
      }
      navigate(isPlatformRole(session.userType) ? '/admin/dashboard' : '/tenant/overview');
    } catch (failure) {
      const serverMessage = isAxiosError(failure) && typeof failure.response?.data?.message === 'string'
        ? failure.response.data.message
        : null;
      setError(serverMessage && CONTROLLED_AUTH_MESSAGES.has(serverMessage)
        ? serverMessage
        : '用户名或密码错误，或账号已被锁定');
      setPassword('');
    } finally {
      setPending(false);
    }
  }

  return (
    <div data-testid="shared-auth-login-page" className="login-page">
      <div data-testid="shared-auth-login-background" className="login-shell">
        <section className="login-intro" aria-label="平台能力">
          <div className="login-brand">
            <span className="login-brand-mark">SMS</span>
            <span>YCSOPEN 通信中台</span>
          </div>
          <div className="login-intro-content">
            <p className="login-eyebrow">Operations Console</p>
            <h1>统一管理短信发送、路由策略与财务结算</h1>
            <p className="login-intro-summary">
              面向运营、销售、财务与技术角色提供可审计的通道管理、机构协作和发送治理入口。
            </p>
            <ul className="login-capabilities">
              <li>
                <span className="login-capability-icon">路由</span>
                <span><strong>策略闭环</strong>有序分流、熔断与重试动作统一维护</span>
              </li>
              <li>
                <span className="login-capability-icon">账务</span>
                <span><strong>账务可追溯</strong>余额、补款、消耗和结算状态全链路留痕</span>
              </li>
              <li>
                <span className="login-capability-icon">权限</span>
                <span><strong>权限隔离</strong>平台与机构角色按菜单、接口和按钮授权</span>
              </li>
            </ul>
          </div>
          <p className="login-intro-footnote">Secure access for verified console users only</p>
        </section>
        <section className="login-form-panel" aria-label="登录表单">
          <form data-testid="shared-auth-login-card" onSubmit={handleSubmit} className="login-card">
            <div className="login-form-heading">
              <p className="login-eyebrow">Welcome back</p>
              <h2>YCSAN-SMS 登录</h2>
              <p>使用控制台账号继续访问测试环境。</p>
            </div>
            <label className="login-field">
              用户名
              <input
                data-testid="shared-auth-login-username"
                aria-label="用户名"
                autoComplete="username"
                required
                maxLength={20}
                placeholder="请输入用户名"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
              />
            </label>
            <label className="login-field">
              密码
              <input
                data-testid="shared-auth-login-password"
                aria-label="密码"
                required
                type="password"
                autoComplete="current-password"
                placeholder="请输入密码"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </label>
            <div className="login-options">
              <label className="login-remember">
                <input
                  data-testid="shared-auth-login-remember"
                  className="login-remember-input"
                  type="checkbox"
                  checked={rememberUsername}
                  onChange={(event) => setRememberUsername(event.target.checked)}
                />
                <span>记住用户名</span>
              </label>
              <span className="login-storage-note">仅保存用户名，不保存密码</span>
            </div>
            {error && <p data-testid="shared-auth-login-error" role="alert" className="login-error">{error}</p>}
            <button data-testid="admin-console-identity-auth-login-submit" className="login-submit" type="submit" disabled={pending}>
              <span data-testid="shared-auth-login-submit">{pending ? '登录中…' : '登录'}</span>
            </button>
            <p className="login-security-note">如账号锁定或密码过期，请联系平台管理员处理。</p>
          </form>
        </section>
      </div>
    </div>
  );
}
