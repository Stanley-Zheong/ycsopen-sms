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
    <div data-testid="shared-auth-login-page" style={{ display: 'flex', justifyContent: 'center', paddingTop: 120 }}>
      <form data-testid="shared-auth-login-card" onSubmit={handleSubmit} className="card" style={{ width: 320 }}>
        <h1>YCSAN-SMS 登录</h1>
        <div style={{ marginBottom: 12 }}>
          <input
            data-testid="shared-auth-login-username"
            aria-label="用户名"
            autoComplete="username"
            required
            maxLength={20}
            placeholder="用户名"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            style={{ width: '100%', padding: 8 }}
          />
        </div>
        <div style={{ marginBottom: 12 }}>
          <input
            data-testid="shared-auth-login-password"
            aria-label="密码"
            required
            type="password"
            autoComplete="current-password"
            placeholder="密码"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            style={{ width: '100%', padding: 8 }}
          />
        </div>
        <label style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
          <input
            data-testid="shared-auth-login-remember"
            type="checkbox"
            checked={rememberUsername}
            onChange={(event) => setRememberUsername(event.target.checked)}
          />
          记住用户名
        </label>
        {error && <p data-testid="shared-auth-login-error" role="alert" style={{ color: '#e5484d' }}>{error}</p>}
        <button data-testid="admin-console-identity-auth-login-submit" type="submit" disabled={pending} style={{ width: '100%', padding: 8 }}>
          <span data-testid="shared-auth-login-submit">{pending ? '登录中…' : '登录'}</span>
        </button>
      </form>
    </div>
  );
}
