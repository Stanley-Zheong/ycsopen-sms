import { expect, test } from '@playwright/test';

test('pw-p25-task-migration C-P25-TASK-MIGRATION OBL-F-4-7-C', async ({ page }) => {
  await page.goto('/admin/channel/health');
  await expect(page.getByTestId('admin-dispatch-task-channel-monitor-task-migration')).toBeVisible();
});

test('pw-p25-recovery-test C-P25-RECOVERY-TEST OBL-F-4-7-D', async ({ page }) => {
  await page.goto('/admin/channel/health');
  await expect(page.getByTestId('admin-dispatch-task-channel-monitor-recovery-test')).toBeVisible();
});

test('pw-p25-channel-recover C-P25-CHANNEL-RECOVER OBL-STATE-CHANNEL-RECOVER', async ({ page }) => {
  await page.goto('/admin/channel/health');
  await expect(page.getByTestId('admin-dispatch-task-channel-monitor-recovery-test')).toBeVisible();
  await page.getByTestId('admin-dispatch-task-channel-monitor-recovery-resume').click();
});

test('pw-p25-upstream-outage C-P25-UPSTREAM-OUTAGE OBL-EDGE-UPSTREAM-OUTAGE', async ({ page }) => {
  await page.goto('/admin/channel/health');
  await page.getByTestId('admin-dispatch-task-channel-monitor-failover').click();
});
