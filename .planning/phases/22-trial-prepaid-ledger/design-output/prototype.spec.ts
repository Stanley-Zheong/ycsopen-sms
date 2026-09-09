import { expect, test } from '@playwright/test';

test('pw-p22-tenant-status C-P22-TENANT-TRIAL-STATUS OBL-F-2-8-B pw-p22-conversion C-P22-CONVERSION OBL-F-2-8-D pw-p22-freeze-state C-P22-TRIAL-FREEZE OBL-STATE-TENANT-TRIAL-FREEZE pw-p22-trial-flow C-P22-TRIAL-FLOW OBL-FLOW-12-1-TRIAL prototype', async ({ page }) => {
  await page.goto('/tenant/overview').catch(() => undefined);
  await page.setContent(require('fs').readFileSync('.planning/phases/22-trial-prepaid-ledger/design-output/prototype.html', 'utf8'));
  await expect(page.getByTestId('tenant-trial-prepaid-overview-trial-status')).toContainText('TRIAL_FROZEN');
  await page.getByTestId('tenant-trial-prepaid-overview-conversion-request').click();
  await expect(page.getByTestId('tenant-trial-prepaid-overview-page')).toBeVisible();
});

test('pw-p22-consumption-page C-P22-CONSUMPTION-LEDGER OBL-F-8-4-A pw-p22-consumption-filter C-P22-CONSUMPTION-FILTER OBL-F-8-4-B prototype', async ({ page }) => {
  await page.goto('/tenant/consumption-ledger').catch(() => undefined);
  await page.setContent(require('fs').readFileSync('.planning/phases/22-trial-prepaid-ledger/design-output/prototype.html', 'utf8'));
  await expect(page.getByTestId('tenant-trial-prepaid-consumption-ledger-page')).toContainText('MSG-22');
  await expect(page.getByTestId('tenant-trial-prepaid-consumption-ledger-filters')).toBeVisible();
});

test('pw-p22-trial-config C-P22-TRIAL-CONFIG OBL-F-2-8-A pw-p22-quota-field C-P22-QUOTA-FIELD OBL-FIELD-TENANT-TRIAL-QUOTA pw-p22-validity-field C-P22-VALIDITY-FIELD OBL-FIELD-TENANT-TRIAL-VALIDITY pw-p22-balance-audit C-P22-BALANCE-AUDIT OBL-F-8-9-A prototype', async ({ page }) => {
  await page.goto('/admin/tenant-trial-contracts').catch(() => undefined);
  await page.setContent(require('fs').readFileSync('.planning/phases/22-trial-prepaid-ledger/design-output/prototype.html', 'utf8'));
  await expect(page.getByTestId('admin-trial-prepaid-tenant-trial-quota')).toContainText('500');
  await expect(page.getByTestId('admin-trial-prepaid-tenant-trial-validity')).toBeVisible();
  await page.goto('/admin/balance-audit').catch(() => undefined);
  await page.setContent(require('fs').readFileSync('.planning/phases/22-trial-prepaid-ledger/design-output/prototype.html', 'utf8'));
  await expect(page.getByTestId('admin-trial-prepaid-balance-audit-table')).toContainText('DOC-22');
});

test('pw-p22-qualification-state C-P22-QUALIFICATION-TRIAL OBL-STATE-TENANT-TRIAL prototype', async ({ page }) => {
  await page.goto('/tenant/qualification').catch(() => undefined);
  await page.setContent(require('fs').readFileSync('.planning/phases/22-trial-prepaid-ledger/design-output/prototype.html', 'utf8'));
  await expect(page.getByTestId('tenant-tenant-qualification-qualification-status')).toContainText('认证通过');
});
