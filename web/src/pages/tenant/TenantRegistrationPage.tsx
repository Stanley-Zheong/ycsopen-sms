import { useRef, useState } from 'react';
import TenantQualificationForm from '@/components/tenant/TenantQualificationForm';
import { registerTenant, type TenantQualificationStatus } from '@/api/tenantQualificationApi';
import '@/styles/tenant-qualification.css';

const USERNAME = /^[A-Za-z][A-Za-z0-9_.-]{3,49}$/;
const EMAIL = /^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+$/;

export default function TenantRegistrationPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [email, setEmail] = useState('');
  const [credentialErrors, setCredentialErrors] = useState<Record<string, string>>({});
  const [result, setResult] = useState<TenantQualificationStatus | null>(null);
  const usernameRef = useRef<HTMLInputElement>(null);
  const passwordRef = useRef<HTMLInputElement>(null);
  const emailRef = useRef<HTMLInputElement>(null);

  function validateCredentials(): HTMLElement | null {
    const next: Record<string, string> = {};
    if (!USERNAME.test(username)) next.username = '用户名须以字母开头，由 4–50 位字母、数字、点、下划线或连字符组成';
    if (password.length < 8 || !/[a-z]/.test(password) || !/[A-Z]/.test(password) || !/\d/.test(password)) {
      next.password = '密码至少 8 位，并包含大写字母、小写字母和数字';
    }
    if (email.length > 100 || !EMAIL.test(email)) next.email = '请输入有效的管理员邮箱';
    setCredentialErrors(next);
    if (next.username) return usernameRef.current;
    if (next.password) return passwordRef.current;
    if (next.email) return emailRef.current;
    return null;
  }

  return (
    <main data-testid="public-tenant-qualification-register-page" className="public-registration-page">
      <header className="qualification-page-header">
        <div>
          <p className="qualification-eyebrow">YCSOpen SMS</p>
          <h1 data-testid="public-tenant-qualification-register-heading">企业注册</h1>
          <p className="page-description">在线完成管理员创建、联系人验证和企业资质提交。</p>
        </div>
        <a href="/login">已有账号，返回登录</a>
      </header>

      {result ? (
        <section data-testid="public-tenant-qualification-register-success" className="card qualification-success-panel" role="status">
          <h2>注册资料已提交</h2>
          <p>机构编号：{result.tenantNo}</p>
          <p>认证状态：待审核。审核通过后，管理员账号才可登录并使用试用能力。</p>
        </section>
      ) : (
        <section className="card">
          <TenantQualificationForm
            submitTestId="public-tenant-qualification-register-submit"
            submitLabel="提交注册资料"
            errorSummaryTestId="public-tenant-qualification-register-error-summary"
            validateBefore={validateCredentials}
            beforeFields={(
              <fieldset className="qualification-admin-fields">
                <legend>初始管理员</legend>
                <div className="qualification-grid">
                  <label htmlFor="public-tenant-qualification-register-admin-username">管理员用户名
                    <input
                      ref={usernameRef}
                      id="public-tenant-qualification-register-admin-username"
                      data-testid="public-tenant-qualification-register-admin-username"
                      value={username}
                      maxLength={50}
                      aria-invalid={Boolean(credentialErrors.username)}
                      onChange={(event) => setUsername(event.target.value)}
                    />
                    {credentialErrors.username && <span className="qualification-field-error">{credentialErrors.username}</span>}
                  </label>
                  <label htmlFor="public-tenant-qualification-register-admin-password">初始密码
                    <input
                      ref={passwordRef}
                      id="public-tenant-qualification-register-admin-password"
                      data-testid="public-tenant-qualification-register-admin-password"
                      type="password"
                      value={password}
                      autoComplete="new-password"
                      aria-invalid={Boolean(credentialErrors.password)}
                      onChange={(event) => setPassword(event.target.value)}
                    />
                    {credentialErrors.password && <span className="qualification-field-error">{credentialErrors.password}</span>}
                  </label>
                  <label htmlFor="public-tenant-qualification-register-admin-email">管理员邮箱
                    <input
                      ref={emailRef}
                      id="public-tenant-qualification-register-admin-email"
                      data-testid="public-tenant-qualification-register-admin-email"
                      type="email"
                      value={email}
                      maxLength={100}
                      aria-invalid={Boolean(credentialErrors.email)}
                      onChange={(event) => setEmail(event.target.value)}
                    />
                    {credentialErrors.email && <span className="qualification-field-error">{credentialErrors.email}</span>}
                  </label>
                </div>
                <p data-testid="public-tenant-qualification-register-privacy-notice" className="qualification-privacy-notice">
                  证件号码与证明材料仅用于资质审核，将加密保存；页面不会回显证件原文、对象标识或上传凭据。
                </p>
              </fieldset>
            )}
            onSubmit={async (context) => {
              const created = await registerTenant(context, {
                adminUsername: username,
                adminPassword: password,
                adminEmail: email,
              });
              setResult(created);
            }}
          />
        </section>
      )}
    </main>
  );
}
