import { expect, test } from '@playwright/test';

test('pw-p6-audit-page C-P6-AUDIT-CAPTURE OBL-F-14-1-A', async ({ page }) => {
  await page.goto('/admin/system/logs');
  await expect(page.getByTestId('admin-privileged-data-system-logs-page')).toBeVisible();
});

test('pw-p6-audit-detail C-P6-AUDIT-INTEGRITY OBL-F-14-1-B', async ({ page }) => {
  await page.goto('/admin/system/logs');
  await expect(page.getByTestId('admin-privileged-data-system-logs-detail-drawer')).toBeVisible();
});

test('pw-p6-reveal C-P6-REVEAL OBL-PRIVILEGED-REVEAL-001', async ({ page }) => {
  await page.goto('/admin/system/users');
  await page.getByTestId('shared-privileged-data-sensitive-value-reveal').click();
});

test('pw-p6-security-page C-P6-SECURITY-DETECTION OBL-F-14-2-A', async ({ page }) => {
  await page.goto('/admin/system/security-events');
  await expect(page.getByTestId('admin-privileged-data-security-events-page')).toBeVisible();
});

test('pw-p6-security-filter C-P6-SECURITY-FILTER OBL-F-14-2-B', async ({ page }) => {
  await page.goto('/admin/system/security-events');
  await page.getByTestId('admin-privileged-data-security-events-filter').focus();
});

test('pw-p6-masked-value C-P6-MASKING OBL-DISPLAY-MASKING', async ({ page }) => {
  await page.goto('/admin/system/users');
  await expect(page.getByTestId('shared-privileged-data-sensitive-value')).toContainText('****');
});

test('prototype inventories supplemental controls', async ({ page }) => {
  await page.goto('/admin/system/logs');
  await expect(page.getByTestId('admin-privileged-data-system-logs-heading')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-system-logs-filter')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-system-logs-actor-input')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-system-logs-operation-input')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-system-logs-result-select')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-system-logs-from-input')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-system-logs-to-input')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-system-logs-query')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-system-logs-reset')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-system-logs-loading')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-system-logs-error')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-system-logs-retry')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-system-logs-table')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-system-logs-row')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-system-logs-detail-open')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-system-logs-empty')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-system-logs-previous')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-system-logs-page-status')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-system-logs-next')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-system-logs-detail-fields')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-system-logs-detail-close')).toBeVisible();
  await page.goto('/admin/system/security-events');
  await expect(page.getByTestId('admin-privileged-data-security-events-heading')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-security-events-type-select')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-security-events-actor-input')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-security-events-result-select')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-security-events-from-input')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-security-events-to-input')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-security-events-query')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-security-events-reset')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-security-events-loading')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-security-events-error')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-security-events-retry')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-security-events-table')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-security-events-row')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-security-events-empty')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-security-events-previous')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-security-events-page-status')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-security-events-next')).toBeVisible();
  await page.goto('/admin/system/users');
  await expect(page.getByTestId('shared-privileged-data-sensitive-value-dialog')).toBeVisible();
  await expect(page.getByTestId('shared-privileged-data-sensitive-value-title')).toBeVisible();
  await expect(page.getByTestId('shared-privileged-data-sensitive-value-account')).toBeVisible();
  await expect(page.getByTestId('shared-privileged-data-sensitive-value-warning')).toBeVisible();
  await page.getByTestId('shared-privileged-data-sensitive-value-purpose').selectOption('CUSTOMER_SUPPORT');
  await expect(page.getByTestId('shared-privileged-data-sensitive-value-confirm')).toBeEnabled();
  await expect(page.getByTestId('shared-privileged-data-sensitive-value-result')).toBeVisible();
  await expect(page.getByTestId('shared-privileged-data-sensitive-value-error')).toBeVisible();
  await page.getByTestId('shared-privileged-data-sensitive-value-close').click();
});
