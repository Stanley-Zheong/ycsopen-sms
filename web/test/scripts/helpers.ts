import { expect, type Page } from '@playwright/test';

type UserType = 'ADMIN' | 'OPERATOR' | 'FINANCE' | 'TENANT_ADMIN' | 'TENANT_USER' | 'TENANT_DEV';

function mockJwt(userType: UserType, tenantId: number | null): string {
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({
    sub: '7',
    userType,
    tenantId,
    exp: Math.floor(Date.now() / 1_000) + 3_600,
  })}.fixture-signature`;
}

export function apiResponse<T>(data: T, message = 'OK') {
  return { code: 0, message, data, timestamp: '2026-08-30T00:00:00Z', traceId: 'e2e-trace' };
}
export async function mockLogin(page: Page, userType: UserType) {
  const tenantId = userType.startsWith('TENANT_') ? 7 : null;
  if (tenantId === null) {
    await page.route('**/api/v1/console/account-overview', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(apiResponse({
          id: 7,
          username: `${userType.toLowerCase()}-user`,
          userType,
          roleNames: userType === 'ADMIN' ? ['系统管理员'] : [],
          permissions: [],
          lastLoginAt: null,
          lastLoginIp: null,
        })),
      }),
    );
  }
  await page.route('**/api/v1/console/auth/login', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(apiResponse({
        accessToken: mockJwt(userType, tenantId),
        userType,
        tenantId,
      })),
    }),
  );
}

export async function loginAs(page: Page, userType: UserType) {
  await mockLogin(page, userType);
  await page.goto('/login');
  await page.getByPlaceholder('用户名').fill(`${userType.toLowerCase()}-user`);
  await page.getByPlaceholder('密码').fill('valid-password');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(userType.startsWith('TENANT_') ? /\/tenant\/overview$/ : /\/admin\/dashboard$/);
}

export async function mockEmptyDashboard(page: Page) {
  await page.route('**/api/v1/console/dashboard/complaint-ratio/*', (route) =>
    route.fulfill({ contentType: 'application/json', body: JSON.stringify(apiResponse([])) }),
  );
}

export async function navigateWithinSpa(page: Page, path: string) {
  await page.evaluate((nextPath) => {
    window.history.pushState({}, '', nextPath);
    window.dispatchEvent(new PopStateEvent('popstate'));
  }, path);
}
