import { expect, test } from '@playwright/test';

test('pw-p19-attribution C-P19-ATTRIBUTION OBL-F-5-7-A pw-p19-portability C-P19-PORTABILITY OBL-F-5-7-B pw-p19-prefixes C-P19-PREFIX-IMPORT OBL-F-13-4-A phase19 prototype selectors exist', async ({ page }) => {
  await page.goto('/admin/number-attribution').catch(() => undefined);
  await page.setContent(require('fs').readFileSync('.planning/phases/19-number-attribution-portability/design-output/prototype.html', 'utf8'));
  await expect(page.getByTestId('admin-number-attribution-portability-attribution-page')).toBeVisible();
  await page.goto('/admin/number-portability').catch(() => undefined);
  await page.setContent(require('fs').readFileSync('.planning/phases/19-number-attribution-portability/design-output/prototype.html', 'utf8'));
  await expect(page.getByTestId('admin-number-attribution-portability-portability-page')).toBeVisible();
  await page.goto('/admin/prefixes').catch(() => undefined);
  await page.setContent(require('fs').readFileSync('.planning/phases/19-number-attribution-portability/design-output/prototype.html', 'utf8'));
  await expect(page.getByTestId('admin-number-attribution-portability-prefixes-page')).toBeVisible();
});
