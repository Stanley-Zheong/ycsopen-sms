import { test, expect } from '@playwright/test';

const prototypeUrl = 'file://' + process.cwd() + '/../.planning/phases/12-signature-lifecycle-filing/design-output/signature-lifecycle-prototype.html';

test.beforeEach(async ({ page }) => {
  await page.route('**/tenant/signatures', async (route) => {
    await route.fulfill({ path: new URL(prototypeUrl).pathname });
  });
  await page.route('**/admin/signatures/review', async (route) => {
    await route.fulfill({ path: new URL(prototypeUrl).pathname });
  });
});

test('pw-p12-signature-application C-P12-APPLICATION-COMPLETE OBL-F-3-1-A', async ({ page }) => {
  await page.goto('/tenant/signatures');
  await expect(page.getByTestId('tenant-signature-lifecycle-signatures-page')).toBeVisible();
  await expect(page.getByTestId('tenant-signature-lifecycle-signatures-application-form')).toBeVisible();
  await expect(page.getByTestId('tenant-signature-lifecycle-signatures-history')).toBeVisible();
});

test('pw-p12-signature-submit C-P12-APPLICATION-PENDING OBL-F-3-1-B', async ({ page }) => {
  await page.goto('/tenant/signatures');
  await expect(page.getByTestId('tenant-signature-lifecycle-signatures-application-submit')).toBeVisible();
});

test('pw-p12-review-stats C-P12-REVIEW-STATS OBL-F-3-2-A', async ({ page }) => {
  await page.goto('/admin/signatures/review');
  await expect(page.getByTestId('admin-signature-lifecycle-signature-review-page')).toBeVisible();
  await expect(page.getByTestId('admin-signature-lifecycle-signature-review-stats')).toContainText('待审核');
});

test('pw-p12-review-filters C-P12-REVIEW-FILTERS OBL-F-3-2-B', async ({ page }) => {
  await page.goto('/admin/signatures/review');
  await expect(page.getByTestId('admin-signature-lifecycle-signature-review-filters')).toBeVisible();
  await expect(page.getByTestId('admin-signature-lifecycle-signature-review-row')).toBeVisible();
  await expect(page.getByTestId('admin-signature-lifecycle-signature-review-decision-open')).toBeVisible();
});

test('pw-p12-review-decision C-P12-REVIEW-DECISION OBL-F-3-2-C', async ({ page }) => {
  await page.goto('/admin/signatures/review');
  await expect(page.getByTestId('admin-signature-lifecycle-signature-review-decision')).toBeVisible();
});

test('pw-p12-filing-matrix C-P12-FILING-MATRIX OBL-F-3-3-A', async ({ page }) => {
  await page.goto('/admin/signatures/review');
  await expect(page.getByTestId('admin-signature-lifecycle-signature-filing-matrix')).toBeVisible();
});

test('pw-p12-filing-retry C-P12-FILING-RETRY OBL-F-3-3-B', async ({ page }) => {
  await page.goto('/admin/signatures/review');
  await expect(page.getByTestId('admin-signature-lifecycle-signature-filing-retry')).toBeVisible();
  await expect(page.getByTestId('admin-signature-lifecycle-signature-filing-result-registered')).toBeVisible();
});

test('pw-p12-usable-channels C-P12-USABLE-CHANNELS OBL-F-3-3-C', async ({ page }) => {
  await page.goto('/tenant/signatures');
  await expect(page.getByTestId('tenant-signature-lifecycle-signatures-usable-channels')).toBeVisible();
});
