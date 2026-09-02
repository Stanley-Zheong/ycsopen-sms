import { expect, test } from '@playwright/test';
import { apiResponse, loginAs } from './helpers';

test('WEB-DASH-001 complaint ratio panels render values and threshold status', async ({ page }) => {
  await page.route('**/api/v1/console/dashboard/complaint-ratio/channel', (route) => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify(apiResponse([
      { dimensionId: 11, dimensionName: '移动主通道', sendCount: 1000, complaintCount: 3, ratio: 0.003, overThreshold: true },
      { dimensionId: 12, dimensionName: '联通备用通道', sendCount: 2000, complaintCount: 2, ratio: 0.001, overThreshold: false },
    ])),
  }));
  await page.route('**/api/v1/console/dashboard/complaint-ratio/tenant', (route) => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify(apiResponse([
      { dimensionId: 7, dimensionName: '示例机构', sendCount: 500, complaintCount: 1, ratio: 0.002, overThreshold: false },
    ])),
  }));
  await loginAs(page, 'ADMIN');
  const channelRow = page.getByRole('row').filter({ hasText: '移动主通道' });
  await expect(channelRow.getByRole('cell').nth(1)).toHaveText('1,000');
  await expect(channelRow.getByRole('cell').nth(2)).toHaveText('3');
  await expect(channelRow.getByRole('cell').nth(3)).toHaveText('3.00‰');
  await expect(channelRow.getByRole('cell').nth(4)).toHaveText('超阈值');
  await expect(channelRow).toHaveCSS('background-color', 'rgb(253, 236, 236)');
  await expect(page.getByText('1 项超阈值')).toBeVisible();
  const normalChannelRow = page.getByRole('row').filter({ hasText: '联通备用通道' });
  await expect(normalChannelRow.getByRole('cell').nth(1)).toHaveText('2,000');
  await expect(normalChannelRow.getByRole('cell').nth(2)).toHaveText('2');
  await expect(normalChannelRow.getByRole('cell').nth(3)).toHaveText('1.00‰');
  await expect(normalChannelRow.getByRole('cell').nth(4)).toHaveText('正常');
  await expect(normalChannelRow).not.toContainText('超阈值');
  const tenantRow = page.getByRole('row').filter({ hasText: '示例机构' });
  await expect(tenantRow.getByRole('cell').nth(1)).toHaveText('500');
  await expect(tenantRow.getByRole('cell').nth(2)).toHaveText('1');
  await expect(tenantRow.getByRole('cell').nth(3)).toHaveText('2.00‰');
  await expect(tenantRow.getByRole('cell').nth(4)).toHaveText('正常');
  await expect(tenantRow).not.toContainText('超阈值');
});

test('WEB-DASH-002 complaint ratio failures render stable error states', async ({ page }) => {
  await page.route('**/api/v1/console/dashboard/complaint-ratio/*', (route) =>
    route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ message: 'failure' }) }),
  );
  await loginAs(page, 'ADMIN');
  await expect(page.getByText(/加载失败，请稍后重试/)).toHaveCount(2);
  await expect(page.getByRole('row').filter({ hasText: '超阈值' })).toHaveCount(0);
});
