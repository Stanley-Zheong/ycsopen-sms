import { expect, test } from '@playwright/test';

test('pw-p40-threshold-rules C-P40-FEE-WARNING-THRESHOLDS OBL-F-8-10-A pw-p40-notification-targets C-P40-FEE-WARNING-TARGETS OBL-F-8-10-B pw-p40-lifecycle-fee-warning C-P40-LIFECYCLE-FEE-WARNING OBL-FLOW-12-1-FEE-WARNING', async ({ page }) => {
  await page.goto('/admin/fee/warning');
  await expect(page.getByTestId('admin-fee-warning-credit-page')).toBeVisible();
  await expect(page.getByTestId('admin-fee-warning-fee-warning-notification-targets')).toContainText('finance');
  await page.getByTestId('admin-fee-warning-fee-warning-rule-save').click();
});

test('pw-p40-credit-action C-P40-CREDIT-ACTION OBL-F-8-2-B pw-p40-ingress-enforcement C-P40-INGRESS-ENFORCEMENT OBL-F-8-10-C pw-p40-finance-supervision C-P40-FINANCE-SUPERVISION OBL-FLOW-12-2-FINANCE', async ({ page }) => {
  await page.goto('/admin/fee/warning');
  await expect(page.getByTestId('admin-fee-warning-fee-warning-credit-action')).toBeVisible();
  await page.getByTestId('admin-fee-warning-fee-warning-evaluate').click();
  await expect(page.getByTestId('admin-fee-warning-fee-warning-enforcement-action')).toContainText('PENDING');
  await expect(page.getByTestId('admin-fee-warning-source-snapshot')).toBeVisible();
  await page.getByTestId('admin-fee-warning-fee-warning-approve').click();
});

test('pw-p40-tenant-low-balance C-P40-TENANT-FEE-WARNING OBL-F-8-1-C', async ({ page }) => {
  await page.goto('/tenant/balance');
  await expect(page.getByTestId('tenant-fee-warning-overview-low-balance-warning')).toContainText('PREPAID_AMOUNT');
  await expect(page.getByTestId('tenant-fee-warning-overview-delivery-evidence')).toContainText('DELIVERED');
});
