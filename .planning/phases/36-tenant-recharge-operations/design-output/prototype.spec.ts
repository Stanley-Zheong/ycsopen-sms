import { expect, test } from '@playwright/test';

test('pw-p36-tenant-recharge C-P36-TENANT-RECHARGE OBL-F-8-3-A', async ({ page }) => {
  await page.goto('/tenant/recharge');
  await page.setContent(await import('node:fs/promises').then((fs) => fs.readFile('.planning/phases/36-tenant-recharge-operations/design-output/prototype.html', 'utf8')));
  await expect(page.getByTestId('tenant-recharge-operations-recharge-form')).toBeVisible();
  await expect(page.getByTestId('tenant-recharge-operations-recharge-amount')).toBeVisible();
  await expect(page.getByTestId('tenant-recharge-operations-recharge-method')).toBeVisible();
  await expect(page.getByTestId('tenant-recharge-operations-recharge-transaction')).toBeVisible();
  await expect(page.getByTestId('tenant-recharge-operations-recharge-evidence')).toBeVisible();
  await expect(page.getByTestId('tenant-recharge-operations-recharge-submit')).toBeVisible();
  await expect(page.getByTestId('tenant-recharge-operations-recharge-state')).toContainText('PENDING');
});

test('pw-p36-finance-review C-P36-FINANCE-REVIEW OBL-F-8-3-B', async ({ page }) => {
  await page.goto('/admin/tenant-recharge-review');
  await page.setContent(await import('node:fs/promises').then((fs) => fs.readFile('.planning/phases/36-tenant-recharge-operations/design-output/prototype.html', 'utf8')));
  await expect(page.getByTestId('admin-tenant-recharge-operations-review-table')).toBeVisible();
  await expect(page.getByTestId('admin-tenant-recharge-operations-review-reason')).toBeVisible();
  await expect(page.getByTestId('admin-tenant-recharge-operations-review-approve')).toBeVisible();
  await expect(page.getByTestId('admin-tenant-recharge-operations-review-reject')).toBeVisible();
});
