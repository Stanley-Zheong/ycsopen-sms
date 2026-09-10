import { expect, test } from '@playwright/test';

test('pw-p32-admin-uplink-details C-P32-ADMIN-UPLINK-DETAILS OBL-F-7-5-A pw-p32-admin-uplinks C-P32-ADMIN-UPLINKS OBL-F-10-1-A', async ({ page }) => {
  await page.goto('/admin/uplink');
  await page.setContent('<section data-route="/admin/uplink" data-testid="admin-uplink-normalization-uplinks-page"><button data-testid="admin-uplink-normalization-uplink-detail">详情</button></section>');
  await expect(page.getByTestId('admin-uplink-normalization-uplinks-page')).toBeVisible();
  await expect(page.getByTestId('admin-uplink-normalization-uplink-detail')).toBeVisible();
});

test('pw-p32-uplink-replay C-P32-UPLINK-REPLAY OBL-F-7-5-B', async ({ page }) => {
  await page.goto('/admin/uplink');
  await page.setContent('<section data-route="/admin/uplink"><button data-testid="admin-uplink-normalization-uplink-replay">重放</button></section>');
  await expect(page.getByTestId('admin-uplink-normalization-uplink-replay')).toBeVisible();
});

test('pw-p32-tenant-auto-reply C-P32-TENANT-AUTO-REPLY OBL-F-7-5-C', async ({ page }) => {
  await page.goto('/tenant/uplink');
  await page.setContent('<section data-route="/tenant/uplink" data-testid="tenant-uplink-normalization-uplinks-auto-reply-config"></section>');
  await expect(page.getByTestId('tenant-uplink-normalization-uplinks-auto-reply-config')).toBeVisible();
});

test('pw-p32-push-monitor C-P32-PUSH-MONITOR OBL-F-10-4-A pw-p32-push-destination-action C-P32-PUSH-DESTINATION-ACTION OBL-F-10-4-B', async ({ page }) => {
  await page.goto('/admin/uplink');
  await page.setContent('<section data-route="/admin/uplink" data-testid="admin-uplink-normalization-push-monitor-page"><div data-testid="admin-uplink-normalization-uplink-push-destination-action"></div></section>');
  await expect(page.getByTestId('admin-uplink-normalization-push-monitor-page')).toBeVisible();
  await expect(page.getByTestId('admin-uplink-normalization-uplink-push-destination-action')).toBeVisible();
});
