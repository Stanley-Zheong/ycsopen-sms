import { expect, type Page } from '@playwright/test';

type UserType = 'ADMIN' | 'OPERATOR' | 'FINANCE' | 'TENANT_ADMIN' | 'TENANT_USER' | 'TENANT_DEV';

export function apiResponse<T>(data: T, message = 'OK') {
  return { code: 0, message, data, timestamp: '2026-08-30T00:00:00Z', traceId: 'e2e-trace' };
}
export async function mockLogin(page: Page, userType: UserType) {
  await page.route('**/api/v1/console/auth/login', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(apiResponse({
        accessToken: `token-${userType.toLowerCase()}`,
        userType,
        tenantId: userType.startsWith('TENANT_') ? 7 : null,
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
