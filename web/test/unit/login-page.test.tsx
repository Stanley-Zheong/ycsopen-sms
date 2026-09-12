import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { login } from '@/api/auth';
import LoginPage, { REMEMBERED_USERNAME_STORAGE_KEY } from '@/pages/LoginPage';
import { useAuthStore } from '@/store/authStore';

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

  it('presents the product context and keeps the remember option inside one compact row', () => {
    renderLoginPage();

    expect(screen.getByRole('heading', { name: '企业短信运营工作台' })).toBeVisible();
    expect(screen.getByText('面向多租户、多通道短信业务，为平台与机构用户提供统一的管理入口。')).toBeVisible();
    expect(screen.getByRole('list', { name: '平台能力概览' })).toBeVisible();
    expect(screen.getByRole('heading', { name: '欢迎登录' })).toBeVisible();

    const remember = screen.getByTestId('shared-auth-login-remember');
    expect(remember).toHaveAttribute('type', 'checkbox');
    expect(remember).toHaveClass('login-remember-input');
    expect(remember.closest('label')).toHaveTextContent('记住用户名');
    expect(remember.closest('label')).toHaveClass('login-remember');
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
});
