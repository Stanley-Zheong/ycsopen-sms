import { expect, test } from '@playwright/test';
import fs from 'node:fs';

async function loadPrototype(page: import('@playwright/test').Page) {
  await page.setContent(fs.readFileSync('.planning/phases/46-secure-async-export/design-output/prototype.html', 'utf8'));
}

test('OBL-F-7-2-C C-P46-SEND-EXPORT pw-p46-send-export', async ({ page }) => {
  await page.goto('/admin/send/details');
  await loadPrototype(page);
  await expect(page.getByTestId('admin-secure-async-send-details-export')).toBeVisible();
});

test('OBL-F-7-4-C C-P46-RECEIPT-EXPORT pw-p46-receipt-export', async ({ page }) => {
  await page.goto('/admin/receipt/details');
  await loadPrototype(page);
  await expect(page.getByTestId('admin-secure-async-receipt-export')).toBeVisible();
});

test('OBL-F-7-8-A C-P46-CENTER pw-p46-export-center', async ({ page }) => {
  await page.goto('/admin/export-center');
  await loadPrototype(page);
  await expect(page.getByTestId('admin-secure-async-export-center-page')).toBeVisible();
  await expect(page.getByTestId('admin-secure-async-export-center-cards')).toContainText('EXCEL/CSV/JSON/PDF');
  await expect(page.getByTestId('admin-secure-async-export-center-table')).toContainText('发送详单导出');
});

test('OBL-F-7-8-C C-P46-DOWNLOAD pw-p46-download', async ({ page }) => {
  await page.goto('/admin/export-center');
  await loadPrototype(page);
  await expect(page.getByTestId('admin-secure-async-export-center-download')).toBeVisible();
});

test('OBL-F-7-9-B C-P46-UNSUBSCRIBE-EXPORT pw-p46-unsubscribe-export', async ({ page }) => {
  await page.goto('/tenant/unsubscribes');
  await loadPrototype(page);
  await expect(page.getByTestId('tenant-secure-async-unsubscribes-export')).toBeVisible();
});

test('OBL-F-8-9-B C-P46-BALANCE-EXPORT pw-p46-balance-export', async ({ page }) => {
  await page.goto('/admin/balance-audit');
  await loadPrototype(page);
  await expect(page.getByTestId('admin-secure-async-balance-audit-export')).toBeVisible();
});

test('OBL-EDGE-EXPORT-FAILURE C-P46-RETRY pw-p46-retry', async ({ page }) => {
  await page.goto('/admin/export-center');
  await loadPrototype(page);
  await expect(page.getByTestId('admin-secure-async-export-center-retry')).toBeVisible();
});
