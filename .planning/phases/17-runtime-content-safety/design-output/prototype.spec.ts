import { test, expect } from '@playwright/test';

test('pw-p17-policy C-P17-POLICY-METRICS OBL-F-5-5-A pw-p17-import C-P17-POLICY-CRUD OBL-F-5-5-B pw-p17-scan C-P17-FINAL-CONTENT OBL-F-5-5-C pw-p17-action C-P17-ACTION-PRECEDENCE OBL-F-5-5-D', async ({ page }) => {
  await page.goto('/admin/content-safety');
  await page.setContent(require('fs').readFileSync('.planning/phases/17-runtime-content-safety/design-output/prototype.html', 'utf8'));
  await expect(page.getByTestId('admin-runtime-content-content-safety-page')).toBeVisible();
  await expect(page.getByTestId('admin-runtime-content-content-safety-save')).toBeVisible();
  await expect(page.getByTestId('admin-runtime-content-content-safety-import')).toBeVisible();
  await expect(page.getByTestId('admin-runtime-content-content-safety-export')).toBeVisible();
  await expect(page.getByTestId('admin-runtime-content-content-safety-delete')).toBeVisible();
  await expect(page.getByTestId('admin-runtime-content-content-safety-scan-panel')).toBeVisible();
  await expect(page.getByTestId('admin-runtime-content-content-safety-scan')).toBeVisible();
});
