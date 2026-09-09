import { test, expect } from '@playwright/test';

test('pw-p15-review-history C-P15-REVIEW-HISTORY OBL-IA-ADMIN-REVIEW-HISTORY prototype', async ({ page }) => {
  await page.goto('/admin/review-history');
  await expect(page.getByTestId('admin-resource-review-history-review-page')).toBeVisible();
  await expect(page.getByTestId('admin-resource-review-history-review-filters')).toBeVisible();
  await expect(page.getByTestId('admin-resource-review-history-review-table')).toBeVisible();
  await expect(page.getByTestId('admin-resource-review-history-review-pagination')).toBeVisible();
  await expect(page.getByTestId('admin-resource-review-history-review-page-prev')).toBeVisible();
  await expect(page.getByTestId('admin-resource-review-history-review-page-next')).toBeVisible();
  await expect(page.getByTestId('admin-resource-review-history-review-detail-drawer')).toBeVisible();
});
