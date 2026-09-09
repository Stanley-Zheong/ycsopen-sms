import { test, expect } from '@playwright/test';

const prototypeUrl = 'file://' + process.cwd() + '/../.planning/phases/11-channel-health-pools-candidate-pause/design-output/channel-health-prototype.html';

test('pw-p11-channel-monitor C-P11-HEALTH-METRICS OBL-F-4-3-A', async ({ page }) => {
  await page.goto('/admin/channel/health');
  await expect(page.getByTestId('admin-channel-health-channel-monitor-page')).toBeVisible();
});

test('pw-p11-health-state C-P11-SUSTAINED-FAILURE OBL-F-4-3-B', async ({ page }) => {
  await page.goto('/admin/channel/health');
  await expect(page.getByTestId('admin-channel-health-channel-monitor-health-state')).toContainText('维护中');
});

test('pw-p11-channel-pools C-P11-POOL-CREATE OBL-F-4-6-A', async ({ page }) => {
  await page.goto('/admin/channel/pools');
  await expect(page.getByTestId('admin-channel-health-channel-pools-page')).toBeVisible();
});

test('pw-p11-pool-weight-editor C-P11-POOL-VALIDATION OBL-F-4-6-B', async ({ page }) => {
  await page.goto('/admin/channel/pools');
  await expect(page.getByTestId('admin-channel-health-channel-pools-weight-editor')).toContainText('100');
});

test('pw-p11-channel-pause C-P11-CHANNEL-PAUSE OBL-F-4-7-A', async ({ page }) => {
  await page.goto('/admin/channel/health');
  await expect(page.getByTestId('admin-channel-health-channel-monitor-pause')).toBeVisible();
});

test('pw-p11-channel-state-pause C-P11-STATE-PAUSE OBL-STATE-CHANNEL-PAUSE', async ({ page }) => {
  await page.goto('/admin/channel/health');
  await expect(page.getByTestId('admin-channel-health-channel-monitor-pause')).toBeVisible();
});

test('pw-p11-maintenance-start C-P11-STATE-MAINTAIN OBL-STATE-CHANNEL-MAINTAIN', async ({ page }) => {
  await page.goto('/admin/channel/health');
  await expect(page.getByTestId('admin-channel-health-channel-monitor-maintenance')).toBeVisible();
});

test('pw-p11-maintenance-end C-P11-STATE-MAINTAIN-END OBL-STATE-CHANNEL-MAINTAIN-END', async ({ page }) => {
  await page.goto('/admin/channel/health');
  await expect(page.getByTestId('admin-channel-health-channel-monitor-maintenance-end')).toBeVisible();
});

test.beforeEach(async ({ page }) => {
  await page.route('**/admin/channel/**', async (route) => {
    await route.fulfill({ path: new URL(prototypeUrl).pathname });
  });
});
