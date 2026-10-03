import { expect, test } from '@playwright/test';
import { mockEmptyDashboard, mockLogin } from './helpers';

const ISSUE_VIEWPORT = { width: 1280, height: 800 };

async function expectNoHorizontalOverflow(page: import('@playwright/test').Page) {
  const widths = await page.evaluate(() => ({
    documentScroll: document.documentElement.scrollWidth,
    documentClient: document.documentElement.clientWidth,
    bodyScroll: document.body.scrollWidth,
  }));
  expect(widths.documentScroll).toBeLessThanOrEqual(widths.documentClient);
  expect(widths.bodyScroll).toBeLessThanOrEqual(widths.documentClient);
}

test.describe('issue-76 login card regression', () => {
  test.use({ viewport: ISSUE_VIEWPORT });

  test('pw-issue-76-card keeps the standard card, compact checkbox, one-line label and containment', async ({ page }) => {
    await page.route('**/api/v1/console/auth/login', async (route) => {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'invalid credentials' }),
      });
    });
    await page.goto('/login');

    const card = page.getByTestId('login-card');
    const compatibilityForm = page.getByTestId('shared-auth-login-card');
    const checkbox = page.getByTestId('shared-auth-login-remember');

    await expect(card).toBeVisible();
    await expect(compatibilityForm).toBeVisible();
    await expect(card.locator('[data-testid="shared-auth-login-card"]')).toHaveCount(1);

    for (const testId of [
      'shared-auth-login-username',
      'shared-auth-login-password',
      'shared-auth-login-remember',
      'admin-console-identity-auth-login-submit',
    ]) {
      await expect(page.getByTestId(testId)).toBeVisible();
    }

    const checkboxBox = (await checkbox.boundingBox())!;
    expect(checkboxBox.width).toBeGreaterThanOrEqual(14);
    expect(checkboxBox.width).toBeLessThanOrEqual(20);
    expect(checkboxBox.height).toBeGreaterThanOrEqual(14);
    expect(checkboxBox.height).toBeLessThanOrEqual(20);

    const rememberText = page.locator('.login-remember > span');
    const rememberGeometry = await rememberText.evaluate((element) => {
      const range = document.createRange();
      range.selectNodeContents(element);
      return {
        lineBoxes: range.getClientRects().length,
        whiteSpace: getComputedStyle(element.parentElement!).whiteSpace,
      };
    });
    expect(rememberGeometry.whiteSpace).toBe('nowrap');
    expect(rememberGeometry.lineBoxes).toBe(1);

    const containment = await card.evaluate((element) => ({
      clientWidth: element.clientWidth,
      scrollWidth: element.scrollWidth,
    }));
    expect(containment.scrollWidth).toBeLessThanOrEqual(containment.clientWidth);
    await expectNoHorizontalOverflow(page);

    const fieldRhythm = await page.locator('.login-field').evaluateAll((fields) => fields.map((field) => {
      const style = getComputedStyle(field);
      return { rowGap: style.rowGap, marginBottom: style.marginBottom };
    }));
    expect(fieldRhythm).toEqual([
      { rowGap: '8px', marginBottom: '16px' },
      { rowGap: '8px', marginBottom: '16px' },
    ]);

    const submit = page.getByTestId('admin-console-identity-auth-login-submit');
    const errorSlot = page.locator('.login-error-slot');
    const spacingBeforeError = await Promise.all([
      errorSlot.boundingBox(),
      submit.boundingBox(),
    ]);
    await page.getByTestId('shared-auth-login-username').fill('unknown');
    await page.getByTestId('shared-auth-login-password').fill('incorrect');
    await submit.click();
    await expect(page.getByTestId('shared-auth-login-error')).toBeVisible();
    const spacingAfterError = await Promise.all([
      errorSlot.boundingBox(),
      submit.boundingBox(),
    ]);
    expect(spacingAfterError).toEqual(spacingBeforeError);
    await expectNoHorizontalOverflow(page);
  });

  test('pw-issue-76-admin-login submits the default credentials and enters the dashboard', async ({ page }) => {
    await mockEmptyDashboard(page);
    await mockLogin(page, 'ADMIN');
    await page.goto('/login');

    await page.getByTestId('shared-auth-login-username').fill('admin');
    await page.getByTestId('shared-auth-login-password').fill('Admin@123456');
    const requestPromise = page.waitForRequest('**/api/v1/console/auth/login');
    await page.getByTestId('admin-console-identity-auth-login-submit').click();

    expect((await requestPromise).postDataJSON()).toEqual({
      username: 'admin',
      password: 'Admin@123456',
    });
    await expect(page).toHaveURL(/\/admin\/dashboard$/);
    await expect(page.getByRole('heading', { name: '关键指标概览' })).toBeVisible();
    await expectNoHorizontalOverflow(page);
  });
});
