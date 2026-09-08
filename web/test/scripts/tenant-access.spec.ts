import { expect, test, type Page } from '@playwright/test';

// Phase 09 is intentionally desktop Chrome-only at the contracted viewport.
test.use({ viewport: { width: 1440, height: 900 } });

const runtimeEnvironment = (globalThis as typeof globalThis & {
  process?: { env?: Record<string, string | undefined> };
}).process?.env ?? {};
const password = runtimeEnvironment.PHASE09_TEST_PASSWORD ?? 'Phase09-Valid!123';
const adminUsername = runtimeEnvironment.PHASE09_ADMIN_USERNAME ?? 'phase09-admin';
const developerUsername = runtimeEnvironment.PHASE09_DEV_USERNAME ?? 'phase09-dev';
const foreignUsername = runtimeEnvironment.PHASE09_FOREIGN_USERNAME ?? 'phase09-foreign';

async function login(page: Page, username: string) {
  await page.goto('/login');
  await page.getByTestId('shared-auth-login-username').fill(username);
  await page.getByTestId('shared-auth-login-password').fill(password);
  await page.getByTestId('admin-console-identity-auth-login-submit').click();
  await expect(page).toHaveURL(/\/tenant\/overview$/);
}

test.describe('Phase 09 tenant access administration', () => {
  test('pw-p9-tenant-administrators-create C-P9-SUBACCOUNT-CREATE OBL-F-1-3-A', async ({ page }) => {
    await login(page, adminUsername);
    await page.goto('/tenant/administrators');
    await expect(page.getByTestId('tenant-tenant-access-administrators-page')).toBeVisible();
    await page.getByTestId('tenant-tenant-access-administrators-create').click();
    const dialog = page.getByTestId('tenant-tenant-access-administrators-create-dialog');
    await expect(dialog).toBeVisible();
    const username = `p09-user-${Date.now()}`;
    await dialog.getByTestId('tenant-tenant-access-administrators-username-field').fill(username);
    await dialog.locator('input').nth(1).fill('Phase 09 user');
    await dialog.locator('input').nth(2).fill('Phase09-Sub!123');
    await dialog.getByTestId('tenant-tenant-access-administrators-role-field').selectOption('TENANT_USER');
    await expect(dialog.getByTestId('tenant-tenant-access-administrators-role')).toBeVisible();
    await dialog.getByTestId('tenant-tenant-access-administrators-role').click();
    const row = page.getByTestId('tenant-tenant-access-administrators-row').filter({ hasText: username });
    await expect(row).toContainText(username);
    await row.getByTestId('tenant-tenant-access-administrators-status-action').click();
    await expect(row).toContainText('DISABLED');
    await row.getByTestId('tenant-tenant-access-administrators-status-action').click();
    await expect(row).toContainText('ACTIVE');
  });

  test('pw-p9-tenant-administrators-role C-P9-SUBACCOUNT-ISOLATION OBL-F-1-3-B', async ({ page }) => {
    await login(page, developerUsername);
    await page.goto('/tenant/administrators');
    await expect(page.getByTestId('tenant-tenant-access-administrators-page')).toBeVisible();
    await expect(page.getByTestId('tenant-tenant-access-administrators-create')).toHaveCount(0);
    await expect(page.getByTestId('tenant-tenant-access-administrators-role')).toHaveCount(0);
    await expect(page.getByTestId('tenant-tenant-access-administrators-page').getByText(foreignUsername)).toHaveCount(0);
  });

  test('pw-p9-api-key-page C-P9-API-KEY-POLICY OBL-F-2-6-A', async ({ page }) => {
    await login(page, developerUsername);
    await page.goto('/tenant/api/keys');
    await expect(page.getByTestId('tenant-tenant-access-api-keys-page')).toBeVisible();
    await page.getByTestId('tenant-tenant-access-api-keys-create-dialog').click();
    const dialog = page.getByRole('dialog');
    await dialog.getByTestId('tenant-tenant-access-api-keys-name-field').locator('input').fill(`p09-api-${Date.now()}`);
    await dialog.getByTestId('tenant-tenant-access-api-keys-expiry-field').locator('input').fill('2099-01-01T00:00');
    await dialog.getByTestId('tenant-tenant-access-api-keys-ip-allow-list-field').locator('input').fill('127.0.0.1/32');
    await dialog.getByTestId('tenant-tenant-access-api-keys-rate-policy-field').locator('input').fill('10');
    await dialog.getByRole('button', { name: '创建', exact: true }).click();
    await expect(page.getByTestId('tenant-tenant-access-api-keys-secret-once')).toBeVisible();
    await expect(page.getByTestId('tenant-tenant-access-api-keys-secret-dialog')).toBeVisible();
    await expect(page.getByTestId('tenant-tenant-access-api-keys-secret-once')).toBeVisible();
    await page.getByRole('button', { name: '我已保存', exact: true }).click();
    await expect(page.getByTestId('tenant-tenant-access-api-keys-secret-once')).toHaveCount(0);
    await expect(page.getByTestId('tenant-tenant-access-api-keys-row')).toHaveCount(1);
  });

  test('pw-p9-api-key-secret-once C-P9-API-KEY-SECRET OBL-F-2-6-B', async ({ page }) => {
    await login(page, developerUsername);
    await page.goto('/tenant/api/keys');
    await page.getByTestId('tenant-tenant-access-api-keys-create-dialog').click();
    const dialog = page.getByRole('dialog');
    const keyName = `p09-revoke-${Date.now()}`;
    await dialog.getByTestId('tenant-tenant-access-api-keys-name-field').locator('input').fill(keyName);
    await dialog.getByRole('button', { name: '创建', exact: true }).click();
    await expect(page.getByTestId('tenant-tenant-access-api-keys-secret-once')).toBeVisible();
    await page.getByRole('button', { name: '我已保存', exact: true }).click();
    const row = page.getByTestId('tenant-tenant-access-api-keys-row').filter({ hasText: keyName });
    await page.once('dialog', (browserDialog) => browserDialog.accept());
    const revokeResponse = page.waitForResponse((response) => response.url().includes('/revoke')
      && response.request().method() === 'POST');
    await row.getByTestId('tenant-tenant-access-api-keys-revoke').click();
    const apiRevoke = await revokeResponse;
    await expect(apiRevoke.status(), await apiRevoke.text()).toBe(200);
    await expect(row).toContainText('DISABLED');
  });

  test('pw-p9-cmpp-page C-P9-CMPP-CREDENTIAL OBL-F-2-6-C', async ({ page }) => {
    await login(page, developerUsername);
    await page.goto('/tenant/cmpp/access');
    await expect(page.getByTestId('tenant-tenant-access-cmpp-access-page')).toBeVisible();
    await page.getByTestId('tenant-tenant-access-cmpp-request-form').click();
    const dialog = page.getByRole('dialog');
    await dialog.getByTestId('tenant-tenant-access-cmpp-endpoint').locator('input').fill('127.0.0.1');
    await dialog.getByTestId('tenant-tenant-access-cmpp-connection-policy').locator('input').fill('4/100/8');
    await dialog.locator('input[type="password"]').fill('Phase09-CMPP!123');
    await dialog.getByRole('button', { name: '提交申请', exact: true }).click();
    await expect(page.getByTestId('tenant-tenant-access-cmpp-secret-once')).toBeVisible();
    await expect(page.getByTestId('tenant-tenant-access-cmpp-secret-once')).toContainText('Phase09-CMPP!123');
    await page.getByRole('button', { name: '我已保存', exact: true }).click();
    await expect(page.getByTestId('tenant-tenant-access-cmpp-secret-once')).toHaveCount(0);
    await expect(page.getByTestId('tenant-tenant-access-cmpp-row')).toContainText('******');
    const revokeResponse = page.waitForResponse((response) => response.url().includes('/revoke')
      && response.request().method() === 'POST');
    await page.getByTestId('tenant-tenant-access-cmpp-row').first().getByTestId('tenant-tenant-access-cmpp-revoke').click();
    const cmppRevoke = await revokeResponse;
    await expect(cmppRevoke.status(), await cmppRevoke.text()).toBe(200);
    await expect(page.getByTestId('tenant-tenant-access-cmpp-row')).toContainText('DISABLED');
  });

  test('pw-p9-tenant-administrators-page C-P9-ADMIN-SETTINGS OBL-F-2-7-A', async ({ page }) => {
    await login(page, adminUsername);
    await page.goto('/tenant/administrators');
    await expect(page.getByTestId('tenant-tenant-access-administrators-page')).toBeVisible();
    await expect(page.getByTestId('tenant-tenant-access-administrators-create')).toBeVisible();
  });
});
