import { expect, test } from '@playwright/test';

test('OBL-F-2-10-A C-P49-REQUEST pw-p49-request OBL-FLOW-12-1-TERMINATION C-P49-FLOW pw-p49-flow', async ({ page }) => {
  await page.goto('/admin/tenant/terminations');
  await expect(page.getByTestId('admin-tenant-cooperation-tenant-termination-page')).toBeVisible();
  await expect(page.getByTestId('admin-tenant-cooperation-tenant-termination-request')).toBeVisible();
});

test('OBL-F-2-10-B C-P49-CLEARANCE pw-p49-clearance', async ({ page }) => {
  await page.goto('/admin/tenant/terminations');
  await expect(page.getByTestId('admin-tenant-cooperation-tenant-termination-clearance')).toContainText('阻塞');
  await expect(page.getByTestId('admin-tenant-cooperation-tenant-termination-participants')).toContainText('API_KEYS');
});

test('OBL-F-2-10-D C-P49-TIMELINE pw-p49-timeline', async ({ page }) => {
  await page.goto('/admin/tenant/terminations');
  await expect(page.getByTestId('admin-tenant-cooperation-tenant-termination-timeline')).toContainText('retained evidence');
});

test('OBL-STATE-TENANT-TERMINATE C-P49-APPROVE pw-p49-approve', async ({ page }) => {
  await page.goto('/admin/tenant/terminations');
  await page.getByTestId('admin-tenant-cooperation-tenant-termination-approve').click();
});
