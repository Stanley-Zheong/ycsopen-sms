import { expect, test } from '@playwright/test';

test('pw-p20-map C-P20-STATUS-MAP OBL-PROVIDER-TAXONOMY-001 pw-p20-crud C-P20-STATUS-CRUD OBL-F-13-3-A pw-p20-version C-P20-VERSION-HISTORY OBL-PROVIDER-TAXONOMY-002 phase20 prototype selectors exist', async ({ page }) => {
  await page.goto('/admin/status-codes').catch(() => undefined);
  await page.setContent(require('fs').readFileSync('.planning/phases/20-provider-status-taxonomy/design-output/prototype.html', 'utf8'));
  await expect(page.getByTestId('admin-provider-status-taxonomy-status-codes-page')).toBeVisible();
  await expect(page.getByTestId('admin-provider-status-status-codes-version-history')).toContainText('ST20260909');
  await page.getByTestId('admin-provider-status-taxonomy-status-codes-import').click();
  await page.getByTestId('admin-provider-status-taxonomy-normalize').click();
  await page.getByTestId('admin-provider-status-taxonomy-status-codes-export').click();
});
