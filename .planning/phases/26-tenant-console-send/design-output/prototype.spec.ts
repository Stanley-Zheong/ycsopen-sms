import { expect, test } from '@playwright/test';

test('pw-p26-tenant-send-page C-P26-TENANT-SEND-PAGE OBL-F-6-10-A', async ({ page }) => {
  await page.goto('/tenant/send');
  await expect(page.getByTestId('tenant-tenant-console-send-page')).toBeVisible();
});

test('pw-p26-tenant-send-submit C-P26-TENANT-SEND-SUBMIT OBL-F-6-10-B', async ({ page }) => {
  await page.goto('/tenant/send');
  await page.getByTestId('tenant-tenant-console-send-submit').click();
});

test('pw-p26-network-retry C-P26-NETWORK-RETRY OBL-EDGE-NETWORK-TIMEOUT', async ({ page }) => {
  await page.goto('/tenant/send');
  await page.getByTestId('shared-tenant-console-send-network-error-retry').click();
});
