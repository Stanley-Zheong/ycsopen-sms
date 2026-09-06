import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { login } from '@/api/auth';
import { isPlatformRole, useAuthStore } from '@/store/authStore';

/** F-1.4 控制台登录，登录成功后按 user_type 分流进入平台管理后台或机构端。 */
export default function LoginPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [rememberUsername, setRememberUsername] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const setSession = useAuthStore((s) => s.setSession);
  const navigate = useNavigate();

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      const session = await login(username, password);
      setSession(session);
      navigate(isPlatformRole(session.userType) ? '/admin/dashboard' : '/tenant/overview');
    } catch {
      setError('用户名或密码错误，或账号已被锁定');
    }
  }

  return (
    <div data-testid="shared-auth-login-page" style={{ display: 'flex', justifyContent: 'center', paddingTop: 120 }}>
      <form data-testid="shared-auth-login-card" onSubmit={handleSubmit} className="card" style={{ width: 320 }}>
        <h2>YCSAN-SMS 登录</h2>
        <div style={{ marginBottom: 12 }}>
          <input
            data-testid="shared-auth-login-username"
            aria-label="用户名"
            required
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
        <button data-testid="shared-auth-login-submit" type="submit" style={{ width: '100%', padding: 8 }}>
          登录
        </button>
      </form>
    </div>
  );
}
