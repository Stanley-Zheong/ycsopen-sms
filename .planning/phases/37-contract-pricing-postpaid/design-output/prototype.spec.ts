import { expect, test } from '@playwright/test';

async function openPrototype(page: import('@playwright/test').Page) {
  await page.setContent(await import('node:fs/promises').then((fs) => fs.readFile(
    '.planning/phases/37-contract-pricing-postpaid/design-output/prototype.html', 'utf8',
  )));
}

test('pw-p37-admin-contract C-P37-ADMIN-CONTRACT OBL-F-2-9-A pw-p37-billing-mode C-P37-BILLING-MODE OBL-F-2-5-A pw-p37-credit-period C-P37-CREDIT-PERIOD OBL-F-2-5-B pw-p37-postpaid-fields C-P37-POSTPAID-FIELDS OBL-F-2-9-B pw-p37-flow-contract C-P37-FLOW-CONTRACT OBL-FLOW-12-1-CONTRACT', async ({ page }) => {
  await page.goto('/admin/tenant-trial-contracts');
  await openPrototype(page);
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-page')).toBeVisible();
  await expect(page.getByTestId('admin-trial-conversion-workbench-filters')).toBeVisible();
  await expect(page.getByTestId('admin-trial-conversion-workbench-query-status')).toHaveAttribute('data-state', 'success');
  await expect(page.getByTestId('data-table')).toContainText('TRIAL-SNAPSHOT-V1');
  await expect(page.getByTestId('table-empty')).toHaveCount(1);
  await expect(page.getByTestId('admin-trial-conversion-workbench-row')).toContainText('TENANT-42');

  await page.getByTestId('admin-trial-conversion-workbench-row-analysis').click();
  await expect(page.getByTestId('admin-trial-conversion-workbench-analysis-dialog')).toContainText('内容投诉');
  await page.getByTestId('admin-trial-conversion-workbench-analysis-close').click();

  await page.getByTestId('admin-trial-conversion-workbench-row-adjust').click();
  const adjustment = page.getByTestId('admin-trial-conversion-workbench-adjust-dialog');
  await expect(adjustment).toContainText('TENANT-42');
  await expect(adjustment.getByTestId('entity-form')).toBeVisible();
  await expect(adjustment.getByTestId('form-submit')).toBeVisible();
  await expect(adjustment.getByTestId('form-cancel')).toBeVisible();
  await adjustment.getByTestId('admin-trial-conversion-workbench-adjust-cancel').click();
  await expect(adjustment).toBeHidden();

  await page.getByTestId('admin-trial-conversion-workbench-row-convert').click();
  await expect(page.getByTestId('admin-trial-conversion-workbench-selected-tenant')).toContainText('TENANT-42');
  const conversion = page.locator('#conversion');
  await expect(conversion.getByTestId('entity-form')).toBeVisible();
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-billing-mode')).toBeVisible();
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-price-version')).toHaveValue('SMS_STANDARD_V1');
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-number')).toBeVisible();
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-signed-date')).toBeVisible();
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-attachment')).toBeVisible();
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-postpaid-fields')).toBeVisible();
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-credit-limit')).toBeVisible();
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-credit-period')).toBeVisible();
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-billing-period')).toBeVisible();
  await expect(page.getByTestId('admin-contract-pricing-tenant-contract-approve')).toBeVisible();
  await expect(page.getByTestId('admin-trial-conversion-workbench-price-retry')).toBeVisible();
  await expect(conversion.getByTestId('form-submit')).toBeVisible();
  await expect(conversion.getByTestId('form-cancel')).toBeVisible();
});

test('pw-p37-tenant-contract-status C-P37-TENANT-CONTRACT-STATUS OBL-F-2-9-C pw-p37-state-contract C-P37-STATE-CONTRACT OBL-STATE-TENANT-CONTRACT', async ({ page }) => {
  await page.goto('/tenant/overview');
  await openPrototype(page);
  await expect(page.getByTestId('tenant-contract-pricing-overview-contract-status'))
    .toContainText('CONTRACTED · POSTPAID · SMS_STANDARD_V1 · MONTHLY');
});
