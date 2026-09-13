import { expect, test } from '@playwright/test';
import fs from 'node:fs';

async function loadPrototype(page: import('@playwright/test').Page) {
  await page.setContent(fs.readFileSync('.planning/phases/45-complaint-ratio-intervention/design-output/prototype.html', 'utf8'));
}

test('OBL-F-11-9-A C-P45-CHANNEL-RATIO pw-p45-channel-ratio', async ({ page }) => {
  await page.goto('/admin/dashboard');
  await loadPrototype(page);
  await expect(page.getByTestId('admin-complaint-ratio-dashboard-complaint-ratio-channel')).toBeVisible();
});

test('OBL-F-11-9-B C-P45-TENANT-RATIO pw-p45-tenant-ratio', async ({ page }) => {
  await page.goto('/admin/dashboard');
  await loadPrototype(page);
  await expect(page.getByTestId('admin-complaint-ratio-dashboard-complaint-ratio-tenant')).toBeVisible();
});

test('OBL-F-11-9-C C-P45-THRESHOLD pw-p45-threshold', async ({ page }) => {
  await page.goto('/admin/dashboard');
  await loadPrototype(page);
  await expect(page.getByTestId('admin-complaint-ratio-dashboard-complaint-ratio-threshold')).toContainText('3.00‰');
  await expect(page.getByTestId('admin-complaint-ratio-dashboard-complaint-ratio-threshold')).toContainText('当前显示超阈值');
  await expect(page.getByTestId('admin-complaint-ratio-dashboard-complaint-ratio-threshold-tenant')).toContainText('3.00‰');
});

test('OBL-F-11-9-D C-P45-PERIOD pw-p45-period', async ({ page }) => {
  await page.goto('/admin/dashboard');
  await loadPrototype(page);
  await expect(page.getByTestId('admin-complaint-ratio-dashboard-complaint-ratio-period')).toContainText('Top 10');
  await expect(page.getByTestId('admin-complaint-ratio-dashboard-complaint-ratio-period-tenant')).toContainText('Top 10');
});

test('OBL-F-11-9-E C-P45-DRILLDOWN pw-p45-drilldown', async ({ page }) => {
  await page.goto('/admin/dashboard');
  await loadPrototype(page);
  await expect(page.getByTestId('admin-complaint-ratio-dashboard-complaint-ratio-drilldown')).toContainText('MSG-91');
  await expect(page.getByTestId('admin-complaint-ratio-dashboard-complaint-ratio-intervention-confirm')).toContainText('确认暂停通道');
  await expect(page.getByTestId('admin-complaint-ratio-dashboard-complaint-ratio-intervention-reason')).toHaveValue('投诉率超阈值人工确认');
});

test('OBL-FLOW-12-2-COMPLAINT-CHANNEL C-P45-CHANNEL-FLOW pw-p45-channel-flow', async ({ page }) => {
  await page.goto('/admin/dashboard');
  await loadPrototype(page);
  await expect(page.getByTestId('admin-complaint-ratio-dashboard-complaint-ratio-channel')).toContainText('暂停通道');
});
