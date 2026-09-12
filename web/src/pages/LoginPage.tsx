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
    <main data-testid="shared-auth-login-page" className="login-page">
      <div className="login-shell">
        <section className="login-intro" aria-labelledby="login-intro-title">
          <div className="login-brand">
            <span className="login-brand-mark" aria-hidden="true">信</span>
            <span>YCSOpen SMS</span>
          </div>

          <div className="login-intro-content">
            <p className="login-eyebrow">多租户 · 多通道</p>
            <h1 id="login-intro-title" data-testid="admin-console-identity-auth-intro-title">
              企业短信运营工作台
            </h1>
            <p className="login-intro-summary" data-testid="admin-console-identity-auth-intro-summary">
              面向多租户、多通道短信业务，为平台与机构用户提供统一的管理入口。
            </p>
            <ul className="login-capabilities" aria-label="平台能力概览">
              <li>
                <span className="login-capability-icon" aria-hidden="true">01</span>
                <span><strong>统一入口</strong>按账号类型进入对应的管理工作区</span>
              </li>
              <li>
                <span className="login-capability-icon" aria-hidden="true">02</span>
                <span><strong>角色隔离</strong>根据当前账号状态与权限控制访问范围</span>
              </li>
              <li>
                <span className="login-capability-icon" aria-hidden="true">03</span>
                <span><strong>安全登录</strong>勾选后仅保存用户名，不会保存密码</span>
              </li>
            </ul>
          </div>

          <p className="login-intro-footnote">YCSAN · 短信平台控制台</p>
        </section>

        <section className="login-form-panel" aria-labelledby="login-form-title">
          <form data-testid="shared-auth-login-card" onSubmit={handleSubmit} className="login-card">
            <div className="login-form-heading">
              <p className="login-eyebrow">安全访问</p>
              <h2 id="login-form-title">欢迎登录</h2>
              <p>使用已授权的控制台账号继续</p>
            </div>

            <label className="login-field">
              <span>用户名</span>
              <input
                data-testid="shared-auth-login-username"
                autoComplete="username"
                required
                maxLength={20}
                placeholder="用户名"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
              />
            </label>

            <label className="login-field">
              <span>密码</span>
              <input
                data-testid="shared-auth-login-password"
                required
                type="password"
                autoComplete="current-password"
                placeholder="密码"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </label>

            <div className="login-options">
              <label className="login-remember">
                <input
                  className="login-remember-input"
                  data-testid="shared-auth-login-remember"
                  type="checkbox"
                  checked={rememberUsername}
                  onChange={(event) => setRememberUsername(event.target.checked)}
                />
                <span>记住用户名</span>
              </label>
              <span className="login-storage-note">仅保存在此设备</span>
            </div>

            {error && (
              <p data-testid="shared-auth-login-error" className="login-error" role="alert">
                {error === '用户名或密码错误，或账号已被锁定'
                  ? <>用户名或密码错误，或账号已被锁定</>
                  : error}
              </p>
            )}
            <button
              className="login-submit"
              data-testid="admin-console-identity-auth-login-submit"
              type="submit"
              disabled={pending}
            >
              <span data-testid="shared-auth-login-submit">
                <span>登录</span>
                <span className={pending ? undefined : 'visually-hidden'} aria-hidden={!pending}>中…</span>
              </span>
            </button>
            <p className="login-security-note">“记住用户名”不会保存密码</p>
          </form>
        </section>
      </div>
    </main>
  );
}
