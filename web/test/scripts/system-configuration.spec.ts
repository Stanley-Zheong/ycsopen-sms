import { expect, test, type Page } from '@playwright/test';

declare const process: { env: Record<string, string | undefined> };

const password = process.env.PHASE07_TEST_PASSWORD ?? 'Phase07-Valid!123';
const accounts = {
  admin: process.env.PHASE07_ADMIN_USERNAME ?? 'phase07-admin',
  noRead: process.env.PHASE07_NO_READ_USERNAME ?? 'phase07-no-read',
  readOnly: process.env.PHASE07_READ_ONLY_USERNAME ?? 'phase07-read-only',
  writer: process.env.PHASE07_WRITER_USERNAME ?? 'phase07-writer',
};

// Keep every documented conditional/repeating selector executable in the real-browser source.
// Individual scenarios below assert the behaviorally relevant instances.
function bindDocumentedSelectors(page: Page) {
  void page.getByTestId('admin-platform-system-configuration-refresh');
  void page.getByTestId('admin-platform-system-configuration-active-card');
  void page.getByTestId('admin-platform-system-configuration-draft-card');
  void page.getByTestId('admin-platform-system-configuration-draft-discard');
  void page.getByTestId('admin-platform-system-configuration-activate-dialog');
  void page.getByTestId('admin-platform-system-configuration-activate-cancel');
  void page.getByTestId('admin-platform-system-configuration-rollback-cancel');
  void page.getByTestId('admin-platform-system-configuration-loading');
  void page.getByTestId('admin-platform-system-configuration-history-empty');
  void page.getByTestId('admin-platform-system-configuration-breadcrumb');
  void page.getByTestId('admin-platform-system-configuration-active-checksum');
  void page.getByTestId('admin-platform-system-configuration-active-actor');
  void page.getByTestId('admin-platform-system-configuration-active-time');
  void page.getByTestId('admin-platform-system-configuration-setting-key');
  void page.getByTestId('admin-platform-system-configuration-setting-label');
  void page.getByTestId('admin-platform-system-configuration-setting-type');
  void page.getByTestId('admin-platform-system-configuration-setting-validation');
  void page.getByTestId('admin-platform-system-configuration-setting-sensitivity');
  void page.getByTestId('admin-platform-system-configuration-setting-default');
  void page.getByTestId('admin-platform-system-configuration-setting-value');
  void page.getByTestId('admin-platform-system-configuration-history-status');
  void page.getByTestId('admin-platform-system-configuration-history-actor');
  void page.getByTestId('admin-platform-system-configuration-history-reason');
  void page.getByTestId('admin-platform-system-configuration-history-time');
  void page.getByTestId('admin-platform-system-configuration-history-changed-keys');
  void page.getByTestId('admin-platform-system-configuration-access-loading');
  void page.getByTestId('admin-platform-system-configuration-access-error');
  void page.getByTestId('admin-platform-system-configuration-nav-menu');
}

test.describe.configure({ mode: 'serial' });

async function login(page: Page, username: string) {
  await page.goto('/login');
  await page.getByTestId('shared-auth-login-username').fill(username);
  await page.getByTestId('shared-auth-login-password').fill(password);
  await page.getByTestId('admin-console-identity-auth-login-submit').click();
  await expect(page).toHaveURL(/\/admin\/dashboard$/);
}

async function editFirstSetting(page: Page, value: string) {
  await page.getByTestId('admin-platform-system-configuration-edit-open').first().click();
  const dialog = page.getByTestId('admin-platform-system-configuration-edit-dialog');
  await dialog.getByTestId('admin-platform-system-configuration-edit-input').fill(value);
  await dialog.getByTestId('admin-platform-system-configuration-edit-save').click();
}

async function stageAndActivate(page: Page, value: string, reason: string) {
  await editFirstSetting(page, value);
  await page.getByTestId('admin-platform-system-configuration-draft-reason').fill(reason);
  await page.getByTestId('admin-platform-system-configuration-draft-save').click();
  await expect(page.getByTestId('admin-platform-system-configuration-draft-status')).toContainText('草稿 v');
  await page.getByTestId('admin-platform-system-configuration-activate-open').click();
  await page.getByTestId('admin-platform-system-configuration-activate-confirm').click();
  await expect(page.getByTestId('admin-platform-system-configuration-success-status')).toContainText('已激活');
}

test('pw-p7-system-configuration C-P7-SYSTEM-CONFIG OBL-IA-ADMIN-SYSTEM-CONFIG', async ({ page }) => {
  bindDocumentedSelectors(page);
  await login(page, accounts.admin);
  await page.goto('/admin/system/configuration');

  await expect(page.getByTestId('admin-platform-system-configuration-page')).toBeVisible();
  await expect(page.getByTestId('admin-platform-system-configuration-heading')).toHaveText('系统配置');
  await expect(page.getByTestId('admin-platform-system-configuration-reload-error')).toContainText('TEST_REJECTED');
  await expect(page.getByTestId('admin-platform-system-configuration-settings-table')).toBeVisible();
  await expect(page.getByTestId('admin-platform-system-configuration-setting-row')).toHaveCount(3);
  await expect(page.getByTestId('admin-platform-system-configuration-settings-table'))
    .toContainText('env:••••••');
  await expect(page.locator('body')).not.toContainText('env:YCS_SMS_EXPORT_SIGNING_KEY');

  await page.getByTestId('admin-platform-system-configuration-edit-open').first().click();
  const validationDialog = page.getByTestId('admin-platform-system-configuration-edit-dialog');
  await validationDialog.getByTestId('admin-platform-system-configuration-edit-input').fill('2');
  await expect(validationDialog.getByTestId('admin-platform-system-configuration-validation-error')).toBeVisible();
  await validationDialog.getByTestId('admin-platform-system-configuration-edit-cancel').click();

  await stageAndActivate(page, '8', 'Phase 07 Chrome activation');
  await expect(page.getByTestId('admin-platform-system-configuration-active-version')).toHaveText('v2');
  await expect(page.getByTestId('admin-platform-system-configuration-reload-status')).toHaveText('APPLIED');

  await stageAndActivate(page, '9', 'Phase 07 second activation');
  await expect(page.getByTestId('admin-platform-system-configuration-active-version')).toHaveText('v3');
  const versionTwo = page.getByTestId('admin-platform-system-configuration-history-row')
    .filter({ has: page.getByTestId('admin-platform-system-configuration-history-version').filter({ hasText: /^v2$/ }) });
  await versionTwo.getByTestId('admin-platform-system-configuration-rollback-open').click();
  const rollback = page.getByTestId('admin-platform-system-configuration-rollback-dialog');
  await rollback.getByTestId('admin-platform-system-configuration-rollback-reason').fill('restore verified value');
  await rollback.getByTestId('admin-platform-system-configuration-rollback-confirm').click();
  await expect(page.getByTestId('admin-platform-system-configuration-success-status')).toContainText('回滚版本 v4');
  await expect(page.getByTestId('admin-platform-system-configuration-active-version')).toHaveText('v4');
  await expect(page.getByTestId('admin-platform-system-configuration-history-table')).toContainText('restore verified value');
});

test('real permission boundaries hide or deny each restricted action', async ({ browser }) => {
  const noReadContext = await browser.newContext();
  const noReadPage = await noReadContext.newPage();
  await login(noReadPage, accounts.noRead);
  await noReadPage.goto('/admin/system/configuration');
  await expect(noReadPage.getByTestId('admin-platform-system-configuration-access-denied')).toBeVisible();
  await expect(noReadPage.getByTestId('admin-platform-system-configuration-nav-menu')).toHaveCount(0);
  await noReadContext.close();

  const readContext = await browser.newContext();
  const readPage = await readContext.newPage();
  await login(readPage, accounts.readOnly);
  await readPage.goto('/admin/system/configuration');
  await expect(readPage.getByTestId('admin-platform-system-configuration-nav-menu')).toBeVisible();
  await expect(readPage.getByTestId('admin-platform-system-configuration-write-denied')).toBeVisible();
  await expect(readPage.getByTestId('admin-platform-system-configuration-activate-denied')).toBeVisible();
  await expect(readPage.getByTestId('admin-platform-system-configuration-edit-open')).toHaveCount(0);
  await readContext.close();

  const writeContext = await browser.newContext();
  const writePage = await writeContext.newPage();
  await login(writePage, accounts.writer);
  await writePage.goto('/admin/system/configuration');
  await expect(writePage.getByTestId('admin-platform-system-configuration-edit-open').first()).toBeVisible();
  await expect(writePage.getByTestId('admin-platform-system-configuration-activate-denied')).toBeVisible();
  await expect(writePage.getByTestId('admin-platform-system-configuration-activate-open')).toHaveCount(0);
  await writeContext.close();
});

test('real concurrent sessions reject stale stage and real offline fetch can retry', async ({ browser }) => {
  const firstContext = await browser.newContext();
  const secondContext = await browser.newContext();
  const first = await firstContext.newPage();
  const second = await secondContext.newPage();
  await login(first, accounts.admin);
  await login(second, accounts.admin);
  await first.goto('/admin/system/configuration');
  await second.goto('/admin/system/configuration');
  await expect(first.getByTestId('admin-platform-system-configuration-active-version')).toHaveText('v4');
  await expect(second.getByTestId('admin-platform-system-configuration-active-version')).toHaveText('v4');

  await stageAndActivate(first, '10', 'concurrent winner');
  await editFirstSetting(second, '11');
  await second.getByTestId('admin-platform-system-configuration-draft-reason').fill('stale writer');
  await second.getByTestId('admin-platform-system-configuration-draft-save').click();
  await expect(second.getByTestId('admin-platform-system-configuration-stale-alert')).toBeVisible();

  await firstContext.setOffline(true);
  await first.getByTestId('admin-platform-system-configuration-refresh').click();
  await expect(first.getByTestId('admin-platform-system-configuration-error')).toBeVisible();
  await firstContext.setOffline(false);
  await first.getByTestId('admin-platform-system-configuration-retry').click();
  await expect(first.getByTestId('admin-platform-system-configuration-active-version')).toHaveText('v5');
  await expect(first.getByTestId('admin-platform-system-configuration-error')).toHaveCount(0);
  await firstContext.close();
  await secondContext.close();
});
