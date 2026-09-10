import { expect, test } from '@playwright/test';

test('pw-p43-custom-reports C-P43-CUSTOM-REPORTS OBL-F-11-4-A', async ({ page }) => {
  await page.goto('/admin/custom/reports');
  await expect(page.getByTestId('admin-custom-report-custom-reports-page')).toBeVisible();
  await expect(page.getByTestId('admin-custom-report-custom-reports-builder')).toContainText('period');
});

test('pw-p43-results C-P43-RESULTS OBL-F-11-4-B', async ({ page }) => {
  await page.goto('/admin/custom/reports');
  await expect(page.getByTestId('admin-custom-report-custom-reports-results')).toContainText('MOBILE');
  await expect(page.getByTestId('admin-custom-report-custom-reports-accessible-table')).toContainText('drill-43');
});
