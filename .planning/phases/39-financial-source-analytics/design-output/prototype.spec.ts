import { expect, test } from '@playwright/test';

test('pw-p39-finance-analytics C-P39-FINANCE-ANALYTICS OBL-F-8-8-A pw-p39-channel-statistics C-P39-CHANNEL-STATISTICS OBL-F-4-5-A pw-p39-drilldown C-P39-DRILLDOWN OBL-F-8-8-B prototype', async ({ page }) => {
  await page.goto('/admin/finance');
  await page.setContent(document.documentElement.outerHTML);
  await expect(page.getByTestId('admin-financial-source-financial-analytics-page')).toBeVisible();
  await page.goto('/admin/statistics');
  await page.setContent(document.documentElement.outerHTML);
  await expect(page.getByTestId('admin-financial-source-channel-statistics-page')).toBeVisible();
  await page.goto('/admin/finance');
  await page.setContent(document.documentElement.outerHTML);
  await page.getByTestId('admin-financial-source-financial-analytics-apply').click();
  await page.getByTestId('admin-financial-source-financial-analytics-drilldown').click();
});
