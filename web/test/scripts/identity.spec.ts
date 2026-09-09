// @ts-expect-error The frontend project intentionally does not install Node type declarations.
import { createServer } from 'node:http';
import { expect, test, type Page } from '@playwright/test';
import { apiResponse, loginAs } from './helpers';

type FailureServer = { close(callback: (error?: Error) => void): void };
type FixtureRequest = { url?: string };
type FixtureResponse = {
  statusCode: number;
  setHeader(name: string, value: string): void;
  end(body: string): void;
};

const overview = {
  id: 7,
  username: 'admin-user',
  userType: 'ADMIN',
  roleNames: ['系统管理员'],
  permissions: [],
  lastLoginAt: '2026-09-07T08:30:00+08:00',
  lastLoginIp: '192.0.2.10',
};

const roles = [
  { id: 10, code: 'ADMIN', name: '系统管理员', description: '', status: 'ACTIVE', userCount: 2, permissionIds: [101] },
  { id: 11, code: 'OPS', name: '运营人员', description: '', status: 'ACTIVE', userCount: 0, permissionIds: [102] },
];

const permissions = [
  { id: 101, code: 'identity:menu', name: '账号与权限菜单', resourceType: 'MENU', resourcePath: '/admin/system', httpMethod: null, parentId: null, sortOrder: 1, status: 'ACTIVE' },
  { id: 102, code: 'identity:accounts:update', name: '编辑账号', resourceType: 'BUTTON', resourcePath: 'edit', httpMethod: null, parentId: null, sortOrder: 2, status: 'ACTIVE' },
  { id: 103, code: 'identity:accounts:read', name: '查询账号', resourceType: 'API', resourcePath: '/api/v1/console/platform-accounts', httpMethod: 'GET', parentId: null, sortOrder: 3, status: 'ACTIVE' },
  { id: 104, code: 'identity:accounts:all', name: '平台账号全量范围', resourceType: 'DATA', resourcePath: 'platform-accounts:*', httpMethod: null, parentId: null, sortOrder: 4, status: 'ACTIVE' },
];

async function mockIdentityApis(page: Page) {
  await page.route('**/api/v1/console/dashboard/complaint-ratio/*', (route) =>
    route.fulfill({ contentType: 'application/json', body: JSON.stringify(apiResponse([])) }));
  await page.route('**/api/v1/console/account-overview', (route) =>
    route.fulfill({ contentType: 'application/json', body: JSON.stringify(apiResponse(overview)) }));
  await page.route('**/api/v1/console/platform-accounts', (route) => {
    if (route.request().method() === 'GET') {
      return route.fulfill({ contentType: 'application/json', body: JSON.stringify(apiResponse([
        { id: 1, username: 'locked_admin', email: 'locked@example.test', realName: '锁定管理员', maskedPhone: '138****0001', userType: 'ADMIN', status: 'LOCKED', roleIds: [10], validUntil: null, createdBy: 'system', createdAt: '2026-01-01T08:00:00+08:00' },
        { id: 2, username: 'operator_user', email: 'operator@example.test', realName: '运营人员', maskedPhone: '139****0002', userType: 'OPERATOR', status: 'ACTIVE', roleIds: [11], validUntil: '2027-01-01', createdBy: 'system', createdAt: '2026-01-02T08:00:00+08:00' },
        { id: 3, username: 'finance_user', email: 'finance@example.test', realName: '财务人员', maskedPhone: '137****0003', userType: 'FINANCE', status: 'DISABLED', roleIds: [11], validUntil: null, createdBy: 'system', createdAt: '2026-01-03T08:00:00+08:00' },
      ])) });
    }
    return route.fulfill({ contentType: 'application/json', body: JSON.stringify(apiResponse({})) });
  });
  await page.route('**/api/v1/console/platform-accounts/**', (route) =>
    route.fulfill({ contentType: 'application/json', body: JSON.stringify(apiResponse(null)) }));
  await page.route('**/api/v1/console/platform-roles', (route) =>
    route.fulfill({ contentType: 'application/json', body: JSON.stringify(apiResponse(roles)) }));
  await page.route('**/api/v1/console/platform-roles/**', (route) =>
    route.fulfill({ contentType: 'application/json', body: JSON.stringify(apiResponse(null)) }));
  await page.route('**/api/v1/console/permissions', (route) =>
    route.fulfill({ contentType: 'application/json', body: JSON.stringify(apiResponse(permissions)) }));
  await page.route('**/api/v1/console/login-history**', (route) =>
    route.fulfill({ contentType: 'application/json', body: JSON.stringify(apiResponse({
      items: [{ id: 50, userId: 7, username: 'admin-user', loginIp: '192.0.2.10', userAgent: 'Google Chrome', outcome: 'SUCCESS_UNUSUAL', occurredAt: '2026-09-07T08:30:00+08:00' }],
      page: 0, size: 20, totalElements: 1,
    })) }));
  await page.route('**/api/v1/console/session/logout', (route) =>
    route.fulfill({ contentType: 'application/json', body: JSON.stringify(apiResponse(null)) }));
}

test('pw-p5-account-create C-P5-ACCOUNT-CREATE OBL-F-1-1-A', async ({ page }) => {
  await mockIdentityApis(page);
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/system/users');

  await page.getByTestId('admin-console-identity-users-create').click();
  await page.getByTestId('admin-console-identity-users-form-username').fill('new_admin');
  await page.getByTestId('admin-console-identity-users-form-password').fill('StrongPass1');
  await page.getByTestId('admin-console-identity-users-form-phone').fill('13800138000');
  await page.getByLabel('邮箱').fill('admin@example.test');
  await page.getByLabel('真实姓名').fill('新管理员');
  await page.getByTestId('admin-console-identity-users-form-type').selectOption('ADMIN');
  await page.getByTestId('admin-console-identity-users-form-validity').fill('2027-01-01');
  await page.getByTestId('admin-console-identity-users-form-roles').selectOption(['10']);
  const createRequest = page.waitForRequest((request) => request.url().endsWith('/api/v1/console/platform-accounts') && request.method() === 'POST');
  await page.getByRole('button', { name: '保存账号' }).click();
  expect((await createRequest).postDataJSON()).toMatchObject({ username: 'new_admin', roleIds: [10], userType: 'ADMIN' });

  await page.getByTestId('admin-console-identity-users-disable').click();
  await expect(page.getByText('禁用后，该账号的现有会话将立即失效。确认禁用？')).toBeVisible();
  const disableRequest = page.waitForRequest('**/api/v1/console/platform-accounts/2/disable');
  await page.getByRole('button', { name: '确认禁用' }).click();
  await disableRequest;
});

test('pw-p5-permission-tree C-P5-PERMISSION-TREE OBL-F-1-2-A', async ({ page }) => {
  await mockIdentityApis(page);
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/system/roles');

  const tree = page.getByTestId('admin-console-identity-roles-permission-tree');
  await expect(page.getByTestId('admin-console-identity-roles-permission-tree')).toBeVisible();
  await expect(tree.getByText('菜单权限')).toBeVisible();
  await expect(tree.getByText('按钮权限')).toBeVisible();
  await expect(tree.getByText('接口权限')).toBeVisible();
  await expect(tree.getByText('数据权限')).toBeVisible();
  await tree.getByLabel('编辑账号').check();
  const grantRequest = page.waitForRequest('**/api/v1/console/platform-roles/10/permissions');
  await page.getByTestId('admin-console-identity-roles-save').click();
  expect((await grantRequest).postDataJSON()).toEqual({ permissionIds: [101, 102] });

  await page.getByRole('button', { name: '删除系统管理员' }).click();
  const migration = page.getByTestId('admin-console-identity-roles-migrate-dialog');
  await migration.getByLabel('迁移到角色').selectOption('11');
  const deleteRequest = page.waitForRequest((request) => request.url().endsWith('/api/v1/console/platform-roles/10') && request.method() === 'DELETE');
  await migration.getByRole('button', { name: '迁移并删除' }).click();
  expect((await deleteRequest).postDataJSON()).toEqual({ replacementRoleId: 11 });
});

test('pw-p5-overview C-P5-OVERVIEW OBL-F-1-5-A', async ({ page }) => {
  await mockIdentityApis(page);
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/account-overview');

  const account = page.getByTestId('admin-console-identity-account-overview');
  await expect(page.getByTestId('admin-console-identity-account-overview')).toBeVisible();
  await expect(account.getByText('admin-user')).toBeVisible();
  await expect(account.getByText('异常登录')).toBeVisible();
  await expect(account.getByText(/余额|授信|机构数据/)).toHaveCount(0);
});

test('pw-p5-internal-error C-P5-INTERNAL-ERROR OBL-EDGE-INTERNAL-ERROR', async ({ page }) => {
  const server = await startFailureServer();
  try {
    await page.goto('/login');
    await page.getByPlaceholder('用户名').fill('error-admin');
    await page.getByPlaceholder('密码').fill('valid-password');
    await page.getByRole('button', { name: '登录' }).click();
    await expect(page).toHaveURL(/\/admin\/dashboard$/);
    await page.goto('/admin/system/users');

    const notice = page.getByTestId('shared-console-identity-internal-error-message');
    await expect(page.getByTestId('shared-console-identity-internal-error-message')).toBeVisible();
    await expect(notice).toContainText('系统繁忙，请稍后再试');
    await expect(page.getByTestId('shared-console-identity-internal-error-trace-id')).toContainText('trace-real-500');
    await expect(notice).not.toContainText('database-password');
    await expect(notice).not.toContainText('RuntimeException');
  } finally {
    await new Promise<void>((resolve, reject) => server.close((error) => (error ? reject(error) : resolve())));
  }
});

test('pw-p5-account-disable-region C-P5-ACCOUNT-DISABLE-REGION OBL-F-1-1-B', async ({ page }) => {
  await mockIdentityApis(page);
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/system/users');
  await expect(page.getByTestId('admin-console-identity-users-row-disable')).toBeVisible();
});

test('pw-p5-permission-live-save C-P5-PERMISSION-LIVE OBL-F-1-2-B', async ({ page }) => {
  await mockIdentityApis(page);
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/system/roles');
  await page.getByTestId('admin-console-identity-roles-permission-tree').getByLabel('编辑账号').check();
  const request = page.waitForRequest('**/api/v1/console/platform-roles/10/permissions');
  await page.getByTestId('admin-console-identity-roles-save').click();
  await request;
});

test('pw-p5-role-migrate C-P5-ROLE-MIGRATE OBL-F-1-2-C', async ({ page }) => {
  await mockIdentityApis(page);
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/system/roles');
  await page.getByRole('button', { name: '删除系统管理员' }).click();
  await expect(page.getByTestId('admin-console-identity-roles-migrate-dialog')).toBeVisible();
});

test('pw-p5-login C-P5-LOGIN OBL-F-1-4-A', async ({ page }) => {
  await mockIdentityApis(page);
  await page.route('**/api/v1/console/auth/login', (route) => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify(apiResponse({
      accessToken: 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI3IiwidXNlclR5cGUiOiJBRE1JTiIsImV4cCI6NDEwMjQ0NDgwMH0.fixture-signature',
      userType: 'ADMIN',
      tenantId: null,
    })),
  }));
  await page.goto('/admin/auth/login');
  await page.getByPlaceholder('用户名').fill('admin-user');
  await page.getByPlaceholder('密码').fill('valid-password');
  await page.getByTestId('admin-console-identity-auth-login-submit').click();
  await expect(page).toHaveURL(/\/admin\/dashboard$/);
});

test('pw-p5-logout C-P5-LOGOUT OBL-F-1-4-B', async ({ page }) => {
  await mockIdentityApis(page);
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/account-overview');
  const request = page.waitForRequest('**/api/v1/console/session/logout');
  await page.getByTestId('shared-console-identity-profile-logout').click();
  await request;
  await expect(page).toHaveURL(/\/login$/);
});

test('pw-p5-login-history C-P5-LOGIN-HISTORY OBL-F-1-4-C', async ({ page }) => {
  await mockIdentityApis(page);
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/system/login-history');
  await expect(page.getByTestId('shared-console-identity-profile-login-history')).toContainText('异常登录');
});

test('pw-p5-username C-P5-USERNAME OBL-FIELD-ACCOUNT-USERNAME', async ({ page }) => {
  await mockIdentityApis(page);
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/system/users');
  await page.getByTestId('admin-console-identity-users-create').click();
  await page.getByTestId('admin-console-identity-users-form-username').fill('valid_user');
  await expect(page.getByTestId('admin-console-identity-users-form-username')).toHaveValue('valid_user');
});

test('pw-p5-password C-P5-PASSWORD OBL-FIELD-ACCOUNT-PASSWORD', async ({ page }) => {
  await mockIdentityApis(page);
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/system/users');
  await page.getByTestId('admin-console-identity-users-create').click();
  await page.getByTestId('admin-console-identity-users-form-password').fill('StrongPass1');
  await expect(page.getByTestId('admin-console-identity-users-form-password')).toHaveValue('StrongPass1');
});

test('pw-p5-phone C-P5-PHONE OBL-FIELD-ACCOUNT-PHONE', async ({ page }) => {
  await mockIdentityApis(page);
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/system/users');
  await page.getByTestId('admin-console-identity-users-create').click();
  await page.getByTestId('admin-console-identity-users-form-phone').fill('13800138000');
  await expect(page.getByTestId('admin-console-identity-users-form-phone')).toHaveValue('13800138000');
});

test('pw-p5-type C-P5-TYPE OBL-FIELD-ACCOUNT-TYPE', async ({ page }) => {
  await mockIdentityApis(page);
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/system/users');
  await page.getByTestId('admin-console-identity-users-create').click();
  await page.getByTestId('admin-console-identity-users-form-type').selectOption('FINANCE');
  await expect(page.getByTestId('admin-console-identity-users-form-type')).toHaveValue('FINANCE');
});

test('pw-p5-validity C-P5-VALIDITY OBL-FIELD-ACCOUNT-VALIDITY', async ({ page }) => {
  await mockIdentityApis(page);
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/system/users');
  await page.getByTestId('admin-console-identity-users-create').click();
  await page.getByTestId('admin-console-identity-users-form-validity').fill('2027-01-01');
  await expect(page.getByTestId('admin-console-identity-users-form-validity')).toHaveValue('2027-01-01');
});

test('pw-p5-lock C-P5-LOCK OBL-STATE-ACCOUNT-LOCK', async ({ page }) => {
  await mockIdentityApis(page);
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/system/users');
  await expect(page.getByTestId('admin-console-identity-users-state')).toContainText('锁定');
});

test('pw-p5-unlock C-P5-UNLOCK OBL-STATE-ACCOUNT-UNLOCK', async ({ page }) => {
  await mockIdentityApis(page);
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/system/users');
  const request = page.waitForRequest('**/api/v1/console/platform-accounts/1/unlock');
  await page.getByTestId('admin-console-identity-users-unlock').click();
  await request;
});

test('pw-p5-disable C-P5-DISABLE OBL-STATE-ACCOUNT-DISABLE', async ({ page }) => {
  await mockIdentityApis(page);
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/system/users');
  await page.getByTestId('admin-console-identity-users-disable').click();
  await expect(page.getByText('禁用后，该账号的现有会话将立即失效。确认禁用？')).toBeVisible();
});

test('pw-p5-enable C-P5-ENABLE OBL-STATE-ACCOUNT-ENABLE', async ({ page }) => {
  await mockIdentityApis(page);
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/system/users');
  const request = page.waitForRequest('**/api/v1/console/platform-accounts/3/enable');
  await page.getByTestId('admin-console-identity-users-enable').click();
  await request;
});

async function startFailureServer(): Promise<FailureServer> {
  const token = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI3IiwidXNlclR5cGUiOiJBRE1JTiIsImV4cCI6NDEwMjQ0NDgwMH0.fixture-signature';
  const server = createServer((request: FixtureRequest, response: FixtureResponse) => {
    response.setHeader('Content-Type', 'application/json; charset=utf-8');
    if (request.url === '/api/v1/console/auth/login') {
      response.end(JSON.stringify(apiResponse({ accessToken: token, userType: 'ADMIN', tenantId: null })));
      return;
    }
    if (request.url === '/api/v1/console/account-overview') {
      response.end(JSON.stringify(apiResponse(overview)));
      return;
    }
    if (request.url === '/api/v1/console/platform-roles') {
      response.end(JSON.stringify(apiResponse([])));
      return;
    }
    if (request.url === '/api/v1/console/platform-accounts') {
      response.statusCode = 500;
      response.setHeader('X-Correlation-Id', 'trace-real-500');
      response.end(JSON.stringify({ code: 500, message: '系统繁忙，请稍后再试', data: null, traceId: 'trace-real-500' }));
      return;
    }
    response.statusCode = 404;
    response.end(JSON.stringify({ code: 404, message: 'not found', data: null }));
  });
  await new Promise<void>((resolve, reject) => {
    server.once('error', reject);
    server.listen(8080, '127.0.0.1', resolve);
  });
  return server;
}
