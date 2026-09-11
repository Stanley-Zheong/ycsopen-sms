import { expect, test } from '@playwright/test';

test('OBL-IA-TENANT-HELP-GUIDE C-P50-GUIDE pw-p50-guide', async ({ page }) => {
  await page.goto('/tenant/help/guide');
  await expect(page.getByTestId('tenant-tenant-help-guide-page')).toBeVisible();
});

test('OBL-IA-TENANT-HELP-API C-P50-API pw-p50-api', async ({ page }) => {
  await page.goto('/tenant/help/api');
  await expect(page.getByTestId('tenant-tenant-help-api-docs-page')).toBeVisible();
});

test('OBL-IA-TENANT-HELP-SERVICE C-P50-SERVICE pw-p50-service', async ({ page }) => {
  await page.goto('/tenant/help/customer-service');
  await expect(page.getByTestId('tenant-tenant-help-customer-service-page')).toBeVisible();
});
