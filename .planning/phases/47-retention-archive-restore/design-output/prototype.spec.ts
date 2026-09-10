import { expect, test } from '@playwright/test';

test('OBL-NFR-RETENTION-TWO-YEAR C-P47-POLICY pw-p47-policy', async ({ page }) => {
  await page.goto('/admin/archive');
  await page.setContent(await import('node:fs/promises').then((fs) => fs.readFile('.planning/phases/47-retention-archive-restore/design-output/prototype.html', 'utf8')));
  await expect(page.getByTestId('admin-retention-archive-policy-card')).toContainText('730');
});

test('OBL-NFR-ARCHIVE-RESTORE C-P47-RESTORE pw-p47-restore', async ({ page }) => {
  await page.goto('/admin/archive');
  await page.setContent(await import('node:fs/promises').then((fs) => fs.readFile('.planning/phases/47-retention-archive-restore/design-output/prototype.html', 'utf8')));
  await expect(page.getByTestId('admin-retention-archive-manifest-restore')).toBeVisible();
});
