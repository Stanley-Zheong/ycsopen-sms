import { expect, test } from '@playwright/test';
import { loginAs, mockEmptyDashboard, mockLogin, navigateWithinSpa } from './helpers';

test('WEB-AUTH-001 ADMIN login routes to the platform dashboard', async ({ page }) => {
  await mockEmptyDashboard(page);
  await mockLogin(page, 'ADMIN');
  await page.goto('/login');
  await page.getByPlaceholder('用户名').fill('admin');
  await page.getByPlaceholder('密码').fill('valid-password');
  const requestPromise = page.waitForRequest('**/api/v1/console/auth/login');
  await page.getByRole('button', { name: '登录' }).click();
  expect((await requestPromise).postDataJSON()).toEqual({ username: 'admin', password: 'valid-password' });
  await expect(page).toHaveURL(/\/admin\/dashboard$/);
  await expect(page.getByRole('heading', { name: '关键指标概览' })).toBeVisible();
  await expect(page.getByRole('link', { name: '机构管理' })).toBeVisible();
  await expect(page.getByRole('link', { name: '发送管理' })).toHaveCount(0);
});

test('WEB-AUTH-002 TENANT_ADMIN login routes to the tenant overview', async ({ page }) => {
  await loginAs(page, 'TENANT_ADMIN');
  await expect(page.getByRole('heading', { name: '账户总览' })).toBeVisible();
  await expect(page.getByRole('link', { name: '发送管理' })).toBeVisible();
  await expect(page.getByRole('link', { name: '机构管理' })).toHaveCount(0);
});

test('WEB-AUTH-003 rejected login remains on the login page', async ({ page }) => {
  await page.route('**/api/v1/console/auth/login', (route) =>
    route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ message: 'unauthorized' }) }),
  );
  await page.goto('/login');
  await page.getByPlaceholder('用户名').fill('invalid');
  await page.getByPlaceholder('密码').fill('invalid');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByText('用户名或密码错误，或账号已被锁定')).toBeVisible();
  await expect(page.locator('.sidebar')).toHaveCount(0);
});

test('WEB-AUTH-004 anonymous user cannot open a platform route', async ({ page }) => {
  await page.goto('/admin/dashboard');
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('heading', { name: '关键指标概览' })).toHaveCount(0);
});

test('WEB-AUTH-007 anonymous user cannot open a tenant route', async ({ page }) => {
  await page.goto('/tenant/overview');
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('heading', { name: '账户总览' })).toHaveCount(0);
});

test('WEB-AUTH-005 tenant role cannot open a platform route', async ({ page }) => {
  await loginAs(page, 'TENANT_USER');
  await expect(page.getByRole('heading', { name: '账户总览' })).toBeVisible();
  await navigateWithinSpa(page, '/admin/dashboard');
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('heading', { name: '关键指标概览' })).toHaveCount(0);
  await expect(page.getByRole('link', { name: '机构管理' })).toHaveCount(0);
});

test('WEB-AUTH-006 platform role cannot open a tenant route', async ({ page }) => {
  await mockEmptyDashboard(page);
  await loginAs(page, 'ADMIN');
  await expect(page.getByRole('heading', { name: '关键指标概览' })).toBeVisible();
  await navigateWithinSpa(page, '/tenant/overview');
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('heading', { name: '账户总览' })).toHaveCount(0);
  await expect(page.getByRole('link', { name: '发送管理' })).toHaveCount(0);
});
