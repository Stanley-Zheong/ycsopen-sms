import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { login } from '@/api/auth';
import LoginPage, { REMEMBERED_USERNAME_STORAGE_KEY } from '@/pages/LoginPage';
import { useAuthStore } from '@/store/authStore';
import type { LoginResponse } from '@/types/api';

vi.mock('@/api/auth', () => ({ login: vi.fn() }));

function jwtWithFutureExpiration(): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = btoa(JSON.stringify({ sub: '7', exp: Date.now() / 1000 + 120 }));
  return `${header}.${payload}.test-signature`;
}

function renderLoginPage() {
  return render(
    <MemoryRouter
      initialEntries={['/login']}
      future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
    >
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/admin/dashboard" element={<div data-testid="admin-destination">平台首页</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('LoginPage remembered username', () => {
  beforeEach(() => {
    vi.mocked(login).mockReset();
    act(() => useAuthStore.getState().logout());
    window.localStorage.clear();
  });

  it('renders the visual background shell and a compact remember checkbox', () => {
    renderLoginPage();

    expect(screen.getByTestId('shared-auth-login-page')).toHaveClass('login-page');
    expect(screen.getByTestId('shared-auth-login-background')).toBeVisible();
    expect(screen.getByTestId('shared-auth-login-card')).toHaveClass('login-card');
    expect(screen.getByTestId('shared-auth-login-remember')).toHaveClass('login-remember-input');
  });

  it('prefills the saved username without persisting a password', () => {
    window.localStorage.setItem('ycsopen.console.remembered-username', 'remembered-admin');

    renderLoginPage();

    expect(screen.getByTestId('shared-auth-login-username')).toHaveValue('remembered-admin');
    expect(screen.getByTestId('shared-auth-login-remember')).toBeChecked();
    expect(screen.getByTestId('shared-auth-login-password')).toHaveValue('');
    expect(screen.getByTestId('shared-auth-login-username')).toHaveAttribute('maxlength', '20');
  });

  it('stores the username after a successful remembered login', async () => {
    vi.mocked(login).mockResolvedValue({
      accessToken: jwtWithFutureExpiration(),
      userType: 'ADMIN',
      tenantId: null,
    });
    renderLoginPage();

    fireEvent.change(screen.getByTestId('shared-auth-login-username'), { target: { value: 'admin-user' } });
    fireEvent.change(screen.getByTestId('shared-auth-login-password'), { target: { value: 'secret-value' } });
    fireEvent.click(screen.getByTestId('shared-auth-login-remember'));
    fireEvent.click(screen.getByTestId('shared-auth-login-submit'));

    await waitFor(() => expect(screen.getByTestId('admin-destination')).toBeVisible());
    expect(window.localStorage.getItem(REMEMBERED_USERNAME_STORAGE_KEY)).toBe('admin-user');
    expect(window.localStorage.getItem(REMEMBERED_USERNAME_STORAGE_KEY)).not.toContain('secret-value');
  });

  it('removes a previously saved username when remember is unchecked', async () => {
    window.localStorage.setItem('ycsopen.console.remembered-username', 'old-admin');
    vi.mocked(login).mockResolvedValue({
      accessToken: jwtWithFutureExpiration(),
      userType: 'ADMIN',
      tenantId: null,
    });
    renderLoginPage();

    fireEvent.click(screen.getByTestId('shared-auth-login-remember'));
    fireEvent.change(screen.getByTestId('shared-auth-login-password'), { target: { value: 'secret-value' } });
    fireEvent.click(screen.getByTestId('shared-auth-login-submit'));

    await waitFor(() => expect(screen.getByTestId('admin-destination')).toBeVisible());
    expect(window.localStorage.getItem(REMEMBERED_USERNAME_STORAGE_KEY)).toBeNull();
  });

  it('renders the two-column sign-in surface with the form beside the brand panel', () => {
    renderLoginPage();

    expect(screen.getByTestId('shared-auth-login-background')).toHaveClass('login-shell');

    const intro = document.querySelector('.login-intro');
    const formPanel = document.querySelector('.login-form-panel');
    expect(intro).not.toBeNull();
    expect(formPanel).not.toBeNull();
    // Brand panel first, white form panel second, form panel owns the login card.
    expect(intro!.compareDocumentPosition(formPanel!)).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
    expect(formPanel!.contains(screen.getByTestId('shared-auth-login-card'))).toBe(true);
    expect(formPanel!.querySelector('h2')).toHaveTextContent('YCSAN-SMS 登录');
  });

  it('reserves one error line so showing an error never moves the submit button', () => {
    renderLoginPage();

    expect(document.querySelector('.login-error-slot')).not.toBeNull();
    expect(screen.queryByTestId('shared-auth-login-error')).not.toBeInTheDocument();
    expect(screen.getByTestId('admin-console-identity-auth-login-submit')).toBeEnabled();
    expect(screen.getByTestId('shared-auth-login-submit')).toHaveTextContent('登录');
  });

  it('shows a visible pending state and blocks duplicate submission', async () => {
    let release!: (value: LoginResponse) => void;
    vi.mocked(login).mockImplementation(() => new Promise<LoginResponse>((resolve) => { release = resolve; }));
    renderLoginPage();

    fireEvent.change(screen.getByTestId('shared-auth-login-username'), { target: { value: 'admin-user' } });
    fireEvent.change(screen.getByTestId('shared-auth-login-password'), { target: { value: 'secret-value' } });
    fireEvent.click(screen.getByTestId('admin-console-identity-auth-login-submit'));

    const submit = screen.getByTestId('admin-console-identity-auth-login-submit');
    expect(submit).toBeDisabled();
    expect(screen.getByTestId('shared-auth-login-submit')).toHaveTextContent('登录中…');
    expect(screen.getByTestId('shared-auth-login-card')).toHaveAttribute('aria-busy', 'true');

    fireEvent.click(submit);
    expect(login).toHaveBeenCalledTimes(1);

    await act(async () => {
      release({ accessToken: jwtWithFutureExpiration(), userType: 'ADMIN', tenantId: null });
    });
    await waitFor(() => expect(screen.getByTestId('admin-destination')).toBeVisible());
  });

  it('keeps the error in the reserved slot, clears the password and re-enables submit', async () => {
    vi.mocked(login).mockRejectedValue(new Error('AUTH_INVALID_CREDENTIALS'));
    renderLoginPage();

    fireEvent.change(screen.getByTestId('shared-auth-login-username'), { target: { value: 'admin-user' } });
    fireEvent.change(screen.getByTestId('shared-auth-login-password'), { target: { value: 'secret-value' } });
    fireEvent.click(screen.getByTestId('admin-console-identity-auth-login-submit'));

    await waitFor(() => expect(screen.getByTestId('shared-auth-login-error')).toBeInTheDocument());
    const error = screen.getByTestId('shared-auth-login-error');
    expect(error).toHaveAttribute('role', 'alert');
    expect(error).toHaveTextContent('用户名或密码错误，或账号已被锁定');
    expect(document.querySelector('.login-error-slot')!.contains(error)).toBe(true);
    expect(screen.getByTestId('shared-auth-login-password')).toHaveValue('');
    expect(screen.getByTestId('shared-auth-login-username')).toHaveValue('admin-user');
    expect(screen.getByTestId('admin-console-identity-auth-login-submit')).toBeEnabled();
    expect(screen.getByTestId('shared-auth-login-card')).toHaveAttribute('aria-busy', 'false');
  });
});
