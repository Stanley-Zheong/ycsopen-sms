import { test, expect } from '@playwright/test';

test('pw-p14-exemption-config C-P14-EXEMPTION-CONFIG OBL-F-3-6-A', async ({ page }) => {
  await page.goto('/admin/exemption/policy');
  await expect(page.getByTestId('admin-auditable-exemption-exemption-policy-page')).toBeVisible();
});

test('pw-p14-exemption-preview C-P14-EXEMPTION-PREVIEW OBL-F-3-6-B', async ({ page }) => {
  await page.goto('/admin/exemption/policy');
  await expect(page.getByTestId('admin-auditable-exemption-exemption-effective-preview')).toBeVisible();
});

test('pw-p14-exemption-usage-history C-P14-EXEMPTION-USAGE-HISTORY OBL-F-3-6-C', async ({ page }) => {
  await page.goto('/admin/exemption/policy');
  await expect(page.getByTestId('admin-auditable-exemption-exemption-usage-history')).toBeVisible();
});
