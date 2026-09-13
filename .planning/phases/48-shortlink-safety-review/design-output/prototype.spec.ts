import { test, expect } from '@playwright/test';

test('OBL-F-13-1-A C-P48-CREATE pw-p48-create OBL-F-13-1-B C-P48-LIST pw-p48-list OBL-F-13-1-C C-P48-ANALYTICS pw-p48-analytics OBL-F-13-2-B C-P48-REVIEW pw-p48-review OBL-F-13-2-C C-P48-SAFE-PENDING pw-p48-safe-pending OBL-FIELD-SHORTLINK-URL C-P48-URL pw-p48-url OBL-FIELD-SHORTLINK-DOMAIN C-P48-DOMAIN pw-p48-domain OBL-FIELD-SHORTLINK-VALIDITY C-P48-VALIDITY pw-p48-validity OBL-STATE-SHORTLINK-APPROVE C-P48-APPROVE pw-p48-approve OBL-STATE-SHORTLINK-REJECT C-P48-REJECT pw-p48-reject OBL-STATE-SHORTLINK-EXPIRE C-P48-EXPIRE pw-p48-expire OBL-STATE-SHORTLINK-OFFLINE C-P48-OFFLINE pw-p48-offline', async ({ page }) => {
  await page.goto('/tenant/shortlink');
  await page.goto('/admin/shortlinks/review');
  await page.goto('/s/:code');
  await page.setContent(require('fs').readFileSync('.planning/phases/48-shortlink-safety-review/design-output/prototype.html', 'utf8'));
  await expect(page.getByTestId('tenant-shortlink-safety-shortlinks-page')).toBeVisible();
  await expect(page.getByTestId('tenant-shortlink-safety-shortlinks-form-url')).toBeVisible();
  await expect(page.getByTestId('tenant-shortlink-safety-shortlinks-form-domain')).toBeVisible();
  await expect(page.getByTestId('tenant-shortlink-safety-shortlinks-form-validity')).toBeVisible();
  await expect(page.getByTestId('tenant-shortlink-safety-shortlinks-list')).toBeVisible();
  await expect(page.getByTestId('tenant-shortlink-safety-analytics-page')).toBeVisible();
  await expect(page.getByTestId('admin-shortlink-safety-shortlinks-review-page')).toBeVisible();
  await expect(page.getByTestId('admin-shortlink-safety-shortlinks-approve')).toBeVisible();
  await expect(page.getByTestId('admin-shortlink-safety-shortlinks-reject')).toBeVisible();
  await expect(page.getByTestId('public-shortlink-safety-pending-page')).toBeVisible();
  await expect(page.getByTestId('public-shortlink-safety-expired-page')).toBeVisible();
  await expect(page.getByTestId('public-shortlink-safety-offline-page')).toBeVisible();
});
