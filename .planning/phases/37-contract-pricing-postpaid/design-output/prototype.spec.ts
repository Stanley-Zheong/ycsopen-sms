import { expect, test } from '@playwright/test';

test('pw-p37-admin-contract C-P37-ADMIN-CONTRACT OBL-F-2-9-A pw-p37-billing-mode C-P37-BILLING-MODE OBL-F-2-5-A pw-p37-credit-period C-P37-CREDIT-PERIOD OBL-F-2-5-B pw-p37-postpaid-fields C-P37-POSTPAID-FIELDS OBL-F-2-9-B pw-p37-flow-contract C-P37-FLOW-CONTRACT OBL-FLOW-12-1-CONTRACT', async ({ page }) => {
  await page.goto('/admin/tenant-trial-contracts');
  await page.setContent(await import('node:fs/promises').then((fs) => fs.readFile('.planning/phases/37-contract-pricing-postpaid/design-output/prototype.html', 'utf8')));
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-page')).toBeVisible();
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-billing-mode')).toBeVisible();
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-price-version')).toBeVisible();
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-number')).toBeVisible();
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-signed-date')).toBeVisible();
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-attachment')).toBeVisible();
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-postpaid-fields')).toBeVisible();
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-credit-limit')).toBeVisible();
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-credit-period')).toBeVisible();
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-billing-period')).toBeVisible();
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-approve')).toBeVisible();
});

test('pw-p37-tenant-contract-status C-P37-TENANT-CONTRACT-STATUS OBL-F-2-9-C pw-p37-state-contract C-P37-STATE-CONTRACT OBL-STATE-TENANT-CONTRACT', async ({ page }) => {
  await page.goto('/tenant/overview');
  await page.setContent(await import('node:fs/promises').then((fs) => fs.readFile('.planning/phases/37-contract-pricing-postpaid/design-output/prototype.html', 'utf8')));
  await expect(page.getByTestId('tenant-contract-pricing-overview-contract-status')).toContainText('CONTRACTED');
});
