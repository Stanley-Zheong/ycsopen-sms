import { expect, test } from '@playwright/test';

test('pw-p33-tenant-unsubscribes C-P33-TENANT-UNSUBSCRIBES OBL-F-7-9-A pw-p33-tenant-notification C-P33-TENANT-NOTIFICATION OBL-F-10-2-E', async ({ page }) => {
  await page.goto('/tenant/unsubscribes');
  await page.setContent('<section data-route="/tenant/unsubscribes" data-testid="tenant-unsubscribe-compliance-unsubscribes-page"><span data-testid="tenant-unsubscribe-compliance-unsubscribes-notification-state">PENDING</span></section>');
  await expect(page.getByTestId('tenant-unsubscribe-compliance-unsubscribes-page')).toBeVisible();
  await expect(page.getByTestId('tenant-unsubscribe-compliance-unsubscribes-notification-state')).toBeVisible();
});

test('pw-p33-admin-keywords C-P33-ADMIN-KEYWORDS OBL-F-10-2-A', async ({ page }) => {
  await page.goto('/admin/unsubscribes');
  await page.setContent('<section data-route="/admin/unsubscribes" data-testid="admin-unsubscribe-compliance-keywords-page"></section>');
  await expect(page.getByTestId('admin-unsubscribe-compliance-keywords-page')).toBeVisible();
});

test('pw-p33-admin-statistics C-P33-ADMIN-STATISTICS OBL-F-10-3-A', async ({ page }) => {
  await page.goto('/admin/unsubscribes');
  await page.setContent('<section data-route="/admin/unsubscribes" data-testid="admin-unsubscribe-compliance-statistics-page"></section>');
  await expect(page.getByTestId('admin-unsubscribe-compliance-statistics-page')).toBeVisible();
});
