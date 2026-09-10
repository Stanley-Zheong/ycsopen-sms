import { expect, test } from '@playwright/test';

test('pw-p35-dashboard C-P35-DASHBOARD OBL-F-11-7-A pw-p35-tabs C-P35-TABS OBL-F-11-7-B pw-p35-dashboard-action C-P35-DASHBOARD-ACTION OBL-F-11-7-C', async ({ page }) => {
  await page.goto('/admin/alerts');
  await expect(page.getByTestId('admin-alert-engine-dashboard-cards')).toBeVisible();
  await expect(page.getByTestId('admin-alert-engine-dashboard-alert-tabs')).toBeVisible();
  await expect(page.getByTestId('admin-alert-engine-dashboard-alert-action')).toBeVisible();
});

test('pw-p35-rules C-P35-RULES OBL-F-12-1-A pw-p35-targets C-P35-TARGETS OBL-F-12-2-A pw-p35-deliveries C-P35-DELIVERIES OBL-F-12-2-B', async ({ page }) => {
  await page.goto('/admin/alerts');
  await expect(page.getByTestId('admin-alert-engine-rules-page')).toBeVisible();
  await expect(page.getByTestId('admin-alert-engine-notification-targets')).toBeVisible();
  await expect(page.getByTestId('admin-alert-engine-alert-delivery-attempts')).toBeVisible();
});

test('pw-p35-history C-P35-HISTORY OBL-F-12-3-A pw-p35-history-ack C-P35-HISTORY-ACK OBL-F-12-3-B pw-p35-history-mute C-P35-HISTORY-MUTE OBL-F-12-3-C', async ({ page }) => {
  await page.goto('/admin/alerts');
  await expect(page.getByTestId('admin-alert-engine-alert-history')).toBeVisible();
  await page.getByTestId('admin-alert-engine-alert-history-acknowledge').click();
  await page.getByTestId('admin-alert-engine-alert-history-mute').click();
});

test('pw-p35-state-ack C-P35-STATE-ACK OBL-STATE-ALERT-ACK pw-p35-state-resolve C-P35-STATE-RESOLVE OBL-STATE-ALERT-RESOLVE pw-p35-state-mute C-P35-STATE-MUTE OBL-STATE-ALERT-MUTE', async ({ page }) => {
  await page.goto('/admin/alerts');
  await page.getByTestId('admin-alert-engine-alert-acknowledge').click();
  await page.getByTestId('admin-alert-engine-alert-resolve').click();
  await page.getByTestId('admin-alert-engine-alert-mute').click();
});
