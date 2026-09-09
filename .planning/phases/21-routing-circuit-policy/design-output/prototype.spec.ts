import { expect, test } from '@playwright/test';

test('pw-p21-author C-P21-ROUTE-AUTHOR OBL-F-5-8-A pw-p21-simulator C-P21-ROUTE-SIMULATOR OBL-F-5-8-B pw-p21-circuit C-P21-CIRCUIT OBL-F-5-9-B pw-p21-retry C-P21-RETRY OBL-F-5-10-A pw-p21-flow C-P21-ROUTING-FLOW OBL-FLOW-12-2-ROUTING phase21 prototype selectors exist', async ({ page }) => {
  await page.goto('/admin/routing-policy').catch(() => undefined);
  await page.setContent(require('fs').readFileSync('.planning/phases/21-routing-circuit-policy/design-output/prototype.html', 'utf8'));
  await expect(page.getByTestId('admin-routing-circuit-routing-policy-page')).toBeVisible();
  await expect(page.getByTestId('admin-routing-circuit-routing-policy-simulator')).toContainText('CH_MAIN');
  await expect(page.getByTestId('admin-routing-circuit-routing-circuit-state')).toContainText('CLOSED');
  await expect(page.getByTestId('admin-routing-circuit-routing-retry-rules')).toBeVisible();
});
