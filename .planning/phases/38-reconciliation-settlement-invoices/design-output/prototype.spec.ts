import { expect, test } from '@playwright/test';

test('pw-p38-statement-source C-P38-STATEMENT-SOURCE OBL-F-8-5-A pw-p38-source-close C-P38-SOURCE-CLOSE OBL-F-8-2-C pw-p38-finance-data C-P38-FINANCE-DATA OBL-DATA-10-8-FINANCE', async ({ page }) => {
  await page.goto('/admin/reconciliation');
  await expect(page.getByTestId('admin-reconciliation-settlement-reconciliation-page')).toBeVisible();
  await expect(page.getByTestId('admin-reconciliation-settlement-reconciliation-generate')).toBeVisible();
});

test('pw-p38-tenant-confirm C-P38-TENANT-CONFIRM OBL-F-8-5-B pw-p38-recon-confirm C-P38-RECON-CONFIRM OBL-STATE-RECON-CONFIRM', async ({ page }) => {
  await page.goto('/tenant/statements');
  await expect(page.getByTestId('tenant-reconciliation-settlement-statements-confirm')).toBeVisible();
});

test('pw-p38-recon-difference C-P38-RECON-DIFFERENCE OBL-STATE-RECON-DIFFERENCE', async ({ page }) => {
  await page.goto('/tenant/statements');
  await expect(page.getByTestId('tenant-reconciliation-settlement-statements-difference')).toBeVisible();
});

test('pw-p38-recon-resolve C-P38-RECON-RESOLVE OBL-STATE-RECON-RESOLVE', async ({ page }) => {
  await page.goto('/admin/reconciliation');
  await expect(page.getByTestId('admin-reconciliation-settlement-reconciliation-resolve')).toBeVisible();
});

test('pw-p38-admin-settlement C-P38-SETTLEMENT OBL-F-8-6-A', async ({ page }) => {
  await page.goto('/admin/reconciliation');
  await expect(page.getByTestId('admin-reconciliation-settlement-settlements-page')).toBeVisible();
});

test('pw-p38-settlement-start C-P38-SETTLEMENT-START OBL-STATE-SETTLEMENT-START', async ({ page }) => {
  await page.goto('/admin/reconciliation');
  await expect(page.getByTestId('admin-reconciliation-settlement-settlements-start')).toBeVisible();
});

test('pw-p38-settlement-done C-P38-SETTLEMENT-DONE OBL-STATE-SETTLEMENT-DONE', async ({ page }) => {
  await page.goto('/admin/reconciliation');
  await expect(page.getByTestId('admin-reconciliation-settlement-settlements-complete')).toBeVisible();
});

test('pw-p38-settlement-received C-P38-SETTLEMENT-RECEIVED OBL-STATE-SETTLEMENT-RECEIVED', async ({ page }) => {
  await page.goto('/admin/reconciliation');
  await expect(page.getByTestId('admin-reconciliation-settlement-settlements-received')).toBeVisible();
});

test('pw-p38-tenant-invoice C-P38-TENANT-INVOICE OBL-F-8-7-A pw-p38-invoice-request C-P38-TENANT-INVOICE OBL-F-8-7-A', async ({ page }) => {
  await page.goto('/tenant/statements');
  await expect(page.getByTestId('tenant-reconciliation-settlement-invoices-page')).toBeVisible();
  await expect(page.getByTestId('tenant-reconciliation-settlement-invoices-request')).toBeVisible();
});

test('pw-p38-admin-invoice-issue C-P38-TENANT-INVOICE OBL-F-8-7-A', async ({ page }) => {
  await page.goto('/admin/reconciliation');
  await expect(page.getByTestId('admin-reconciliation-settlement-invoices-issue')).toBeVisible();
});

test('pw-p38-monthly-pack C-P38-MONTHLY-PACK OBL-FLOW-12-1-MONTHLY-PACK', async ({ page }) => {
  await page.goto('/tenant/statements');
  await expect(page.getByTestId('tenant-reconciliation-settlement-statements-page')).toBeVisible();
});
