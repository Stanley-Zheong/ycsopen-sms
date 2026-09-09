import { test, expect } from '@playwright/test';

test('pw-p16-list C-P16-LIST OBL-F-5-2-A pw-p16-import C-P16-IMPORT-EXPORT OBL-F-5-2-B pw-p16-provider C-P16-PROVIDER-CONFIG OBL-F-5-3-A pw-p16-analytics C-P16-ANALYTICS OBL-F-5-4-A pw-p16-appeal C-P16-APPEAL OBL-F-5-4-B pw-p16-degraded C-P16-RISK-OUTAGE OBL-EDGE-RISK-OUTAGE prototype', async ({ page }) => {
  await page.goto('/admin/riskcontrol');
  await expect(page.getByTestId('admin-blacklist-risk-black-white-lists-page')).toBeVisible();
  await expect(page.getByTestId('admin-blacklist-risk-black-white-lists-save')).toBeVisible();
  await expect(page.getByTestId('admin-blacklist-risk-black-white-lists-import')).toBeVisible();
  await expect(page.getByTestId('admin-blacklist-risk-black-white-lists-export')).toBeVisible();
  await expect(page.getByTestId('admin-blacklist-risk-risk-provider-page')).toBeVisible();
  await expect(page.getByTestId('admin-blacklist-risk-risk-provider-save')).toBeVisible();
  await expect(page.getByTestId('admin-blacklist-risk-intercept-analytics-page')).toBeVisible();
  await expect(page.getByTestId('admin-blacklist-risk-intercept-check-run')).toBeVisible();
  await expect(page.getByTestId('admin-blacklist-risk-intercept-appeal')).toBeVisible();
  await expect(page.getByTestId('admin-blacklist-risk-risk-provider-degraded')).toBeVisible();
});
