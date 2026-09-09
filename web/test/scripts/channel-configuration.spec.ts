import { expect, test, type Page } from '@playwright/test';

// Phase 10 is desktop Chrome-only at the contracted console viewport.
test.use({ viewport: { width: 1440, height: 900 } });
test.describe.configure({ mode: 'serial' });

const runtimeEnvironment = (globalThis as typeof globalThis & {
  process?: { env?: Record<string, string | undefined> };
}).process?.env ?? {};
const password = runtimeEnvironment.PHASE10_TEST_PASSWORD ?? 'Phase10-Valid!123';
const adminUsername = runtimeEnvironment.PHASE10_ADMIN_USERNAME ?? 'phase10-admin';
const financeUsername = runtimeEnvironment.PHASE10_FINANCE_USERNAME ?? 'phase10-finance';

async function login(page: Page, username: string) {
  await page.goto('/login');
  await page.getByTestId('shared-auth-login-username').fill(username);
  await page.getByTestId('shared-auth-login-password').fill(password);
  await page.getByTestId('admin-console-identity-auth-login-submit').click();
  await expect(page).toHaveURL(/\/admin\/dashboard$/);
}

async function expectChannelPage(page: Page) {
  await expect(page.getByTestId('admin-channel-configuration-channel-configuration-page')).toBeVisible();
}

async function openCreateDialog(page: Page) {
  await page.getByTestId('admin-channel-configuration-channel-create-open').click();
  return page.getByRole('dialog');
}

test.describe('Phase 10 channel configuration lifecycle', () => {
  test('pw-p10-channel-configuration C-P10-CHANNEL-CONFIGURATION OBL-F-4-1-A', async ({ page }) => {
    await login(page, adminUsername);
    await page.goto('/admin/channel/configuration');
    await expect(page.getByTestId('admin-channel-configuration-channel-configuration-page')).toBeVisible();

    const channelName = `phase10-ui-${Date.now()}`;
    const dialog = await openCreateDialog(page);
    await dialog.getByTestId('admin-channel-configuration-channel-form-name').fill(channelName);
    await dialog.getByTestId('admin-channel-configuration-channel-form-protocol').selectOption('CMPP');
    await dialog.getByTestId('admin-channel-configuration-channel-form-operator').selectOption('MOBILE');
    await dialog.getByTestId('admin-channel-configuration-channel-form-host').fill('channel-fixture.local');
    await dialog.getByTestId('admin-channel-configuration-channel-form-port').fill('7890');
    await dialog.getByTestId('admin-channel-configuration-channel-form-account').fill('p10-account');
    await dialog.getByTestId('admin-channel-configuration-channel-form-password').fill('P10-Secret!123');
    await dialog.getByTestId('admin-channel-configuration-channel-form-sp-id').fill('P10');
    await dialog.getByTestId('admin-channel-configuration-channel-form-service-id').fill('svc');
    await dialog.getByTestId('admin-channel-configuration-channel-form-src-id').fill('10690010');
    await dialog.getByTestId('admin-channel-configuration-channel-form-max-connections').fill('4');
    await dialog.getByTestId('admin-channel-configuration-channel-form-window-size').fill('8');
    await dialog.getByTestId('admin-channel-configuration-channel-form-tps-limit').fill('100');
    await dialog.getByTestId('admin-channel-configuration-channel-form-price').fill('0.1200');
    await dialog.getByTestId('admin-channel-configuration-channel-form-priority').fill('70');
    await dialog.getByTestId('admin-channel-configuration-channel-form-availability').fill('AVAILABLE');
    await dialog.getByTestId('admin-channel-configuration-channel-form-extra-config').fill('{"region":"fixture"}');
    await dialog.getByTestId('admin-channel-configuration-channel-form-save').click();

    const row = page.getByTestId('admin-channel-configuration-channel-row').filter({ hasText: channelName });
    await expect(row).toBeVisible();
    await expect(page.locator('body')).not.toContainText('P10-Secret!123');
  });

  test('pw-p10-channel-connectivity C-P10-CONNECTIVITY OBL-F-4-1-B', async ({ page }) => {
    await login(page, adminUsername);
    await page.goto('/admin/channel/configuration');
    await expectChannelPage(page);
    const connectivityButtons = await page.getByTestId('admin-channel-configuration-channel-configuration-connectivity-test').count();
    await expect(page.getByTestId('admin-channel-configuration-channel-configuration-connectivity-test')).toHaveCount(connectivityButtons);
    await page.getByTestId('admin-channel-configuration-channel-configuration-connectivity-test').first().click();
    await expect(page.getByRole('status')).toContainText('连接性校验通过');
  });

  test('pw-p10-channel-pricing C-P10-PRICING OBL-F-4-1-C', async ({ page }) => {
    await login(page, adminUsername);
    await page.goto('/admin/channel/configuration');
    await expectChannelPage(page);
    const priceCells = await page.getByTestId('admin-channel-configuration-channel-configuration-price').count();
    await expect(page.getByTestId('admin-channel-configuration-channel-configuration-price')).toHaveCount(priceCells);
    await expect(page.getByTestId('admin-channel-configuration-channel-configuration-price').first())
      .toContainText(/\d+\.\d{4}/);
  });

  test('pw-p10-channel-activation-result C-P10-ACTIVATION-ROLLBACK OBL-F-4-2-B', async ({ page }) => {
    await login(page, adminUsername);
    await page.goto('/admin/channel/configuration');
    await expectChannelPage(page);
    const sourceRow = page.getByTestId('admin-channel-configuration-channel-row').filter({ hasText: 'phase10-source' });
    await sourceRow.getByTestId('admin-channel-configuration-channel-activate').click();
    await expect(page.getByTestId('admin-channel-configuration-channel-configuration-activation-result'))
      .toContainText('EFFECTIVE');
  });

  test('pw-p10-channel-dependency-preview C-P10-DEPENDENCY-PREVIEW OBL-F-4-4-A', async ({ page }) => {
    await login(page, adminUsername);
    await page.goto('/admin/channel/configuration');
    await expectChannelPage(page);
    const sourceRow = page.getByTestId('admin-channel-configuration-channel-row').filter({ hasText: 'phase10-source' });
    const dependencyButtons = await page.getByTestId('admin-channel-configuration-channel-dependency-preview').count();
    await expect(page.getByTestId('admin-channel-configuration-channel-dependency-preview')).toHaveCount(dependencyButtons);
    await sourceRow.getByTestId('admin-channel-configuration-channel-dependency-preview').click();
    await expect(page.getByTestId('admin-channel-configuration-channel-migration-wizard')).toContainText('未解决依赖：1');
  });

  test('pw-p10-channel-migration C-P10-DEPENDENCY-MIGRATION OBL-F-4-4-B', async ({ page }) => {
    await login(page, adminUsername);
    await page.goto('/admin/channel/configuration');
    await expectChannelPage(page);
    const sourceRow = page.getByTestId('admin-channel-configuration-channel-row').filter({ hasText: 'phase10-source' });
    await sourceRow.getByTestId('admin-channel-configuration-channel-dependency-preview').click();
    await expect(page.getByTestId('admin-channel-configuration-channel-migration-wizard')).toBeVisible();
    await page.getByTestId('admin-channel-configuration-channel-migration-target').selectOption({ label: 'phase10-destination' });
    await page.getByTestId('admin-channel-configuration-channel-migration-submit').click();
    await expect(page.getByRole('status')).toContainText('依赖已迁移');
  });

  test('pw-p10-channel-field-name C-P10-FIELD-NAME OBL-FIELD-CHANNEL-NAME', async ({ page }) => {
    await login(page, adminUsername);
    await page.goto('/admin/channel/configuration');
    await expectChannelPage(page);
    await openCreateDialog(page);
    await expect(page.getByTestId('admin-channel-configuration-channel-form-name')).toBeVisible();
  });

  test('pw-p10-channel-field-protocol C-P10-FIELD-PROTOCOL OBL-FIELD-CHANNEL-PROTOCOL', async ({ page }) => {
    await login(page, adminUsername);
    await page.goto('/admin/channel/configuration');
    await expectChannelPage(page);
    const dialog = await openCreateDialog(page);
    await expect(page.getByTestId('admin-channel-configuration-channel-form-protocol')).toBeVisible();
    await dialog.getByTestId('admin-channel-configuration-channel-form-protocol').selectOption('HTTP');
  });

  test('pw-p10-channel-field-endpoint C-P10-FIELD-ENDPOINT OBL-FIELD-CHANNEL-ENDPOINT', async ({ page }) => {
    await login(page, adminUsername);
    await page.goto('/admin/channel/configuration');
    await expectChannelPage(page);
    await openCreateDialog(page);
    await expect(page.getByTestId('admin-channel-configuration-channel-form-endpoint')).toBeVisible();
  });

  test('pw-p10-channel-field-credential C-P10-FIELD-CREDENTIAL OBL-FIELD-CHANNEL-CREDENTIAL', async ({ page }) => {
    await login(page, adminUsername);
    await page.goto('/admin/channel/configuration');
    await expectChannelPage(page);
    await openCreateDialog(page);
    await expect(page.getByTestId('admin-channel-configuration-channel-form-credential')).toBeVisible();
  });

  test('pw-p10-channel-field-connection C-P10-FIELD-CONNECTION OBL-FIELD-CHANNEL-CONNECTION', async ({ page }) => {
    await login(page, adminUsername);
    await page.goto('/admin/channel/configuration');
    await expectChannelPage(page);
    await openCreateDialog(page);
    await expect(page.getByTestId('admin-channel-configuration-channel-form-connection')).toBeVisible();
  });

  test('pw-p10-channel-field-price C-P10-FIELD-PRICE OBL-FIELD-CHANNEL-PRICE', async ({ page }) => {
    await login(page, adminUsername);
    await page.goto('/admin/channel/configuration');
    await expectChannelPage(page);
    const dialog = await openCreateDialog(page);
    await expect(page.getByTestId('admin-channel-configuration-channel-form-price')).toBeVisible();
    await dialog.getByTestId('admin-channel-configuration-channel-form-price').fill('0.1200');
  });

  test('pw-p10-channel-field-priority C-P10-FIELD-PRIORITY OBL-FIELD-CHANNEL-PRIORITY', async ({ page }) => {
    await login(page, adminUsername);
    await page.goto('/admin/channel/configuration');
    await expectChannelPage(page);
    const dialog = await openCreateDialog(page);
    await expect(page.getByTestId('admin-channel-configuration-channel-form-priority')).toBeVisible();
    await dialog.getByTestId('admin-channel-configuration-channel-form-priority').fill('70');
  });

  test('pw-p10-channel-offline C-P10-CHANNEL-OFFLINE OBL-STATE-CHANNEL-OFFLINE', async ({ page }) => {
    await login(page, adminUsername);
    await page.goto('/admin/channel/configuration');
    await expectChannelPage(page);
    const sourceRow = page.getByTestId('admin-channel-configuration-channel-row').filter({ hasText: 'phase10-source' });
    const offlineButtons = await page.getByTestId('admin-channel-configuration-channel-offline').count();
    await expect(page.getByTestId('admin-channel-configuration-channel-offline')).toHaveCount(offlineButtons);
    await sourceRow.getByTestId('admin-channel-configuration-channel-offline').click();
    await page.getByTestId('admin-channel-configuration-channel-offline-confirm').click();
    await expect(page.getByRole('status')).toContainText('通道已下线');
    await expect(sourceRow).toContainText('OFFLINE');
  });

  test('pw-p10-channel-access-denied C-P10-RBAC OBL-F-4-1-B', async ({ page }) => {
    await login(page, financeUsername);
    await page.goto('/admin/channel/configuration');
    await expect(page.getByTestId('admin-channel-configuration-channel-configuration-access-denied'))
      .toContainText('无权查看通道配置');
    await expect(page.getByTestId('admin-channel-configuration-channel-create-open')).toHaveCount(0);
  });
});
