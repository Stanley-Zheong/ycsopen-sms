import { expect, test } from '@playwright/test';
import { apiResponse, loginAs, mockEmptyDashboard } from './helpers';

const tenantPending = { id: 7, tenantNo: 'T0007', shortName: '示例机构', fullName: '示例机构有限公司', verificationStatus: 'PENDING', lifecycleStatus: 'SUBMITTED' };
const channelNormal = { id: 11, channelName: '移动主通道', protocol: 'CMPP', operator: 'MOBILE', status: 'NORMAL', priority: 1 };

test('WEB-TENANT-001 approving a tenant refreshes its trial state', async ({ page }) => {
  let approved = false;
  await mockEmptyDashboard(page);
  await page.route('**/api/v1/console/tenants', (route) => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify(apiResponse([{ ...tenantPending, verificationStatus: approved ? 'VERIFIED' : 'PENDING', lifecycleStatus: approved ? 'TRIAL' : 'SUBMITTED' }])),
  }));
  await page.route('**/api/v1/console/tenants/7/approve-and-activate-trial?*', async (route) => {
    expect(route.request().method()).toBe('POST');
    expect(Object.fromEntries(new URL(route.request().url()).searchParams)).toEqual({ approvedBy: 'operator-demo', trialQuota: '500', trialDays: '14' });
    approved = true;
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify(apiResponse({ ...tenantPending, verificationStatus: 'VERIFIED', lifecycleStatus: 'TRIAL' })) });
  });
  await loginAs(page, 'OPERATOR');
  await page.getByRole('link', { name: '机构管理' }).click();
  await page.getByRole('button', { name: '审核通过并开通试用' }).click();
  const row = page.getByRole('row').filter({ hasText: '示例机构' });
  await expect(row).toContainText('VERIFIED');
  await expect(row).toContainText('TRIAL');
  await expect(row.getByRole('button', { name: '审核通过并开通试用' })).toHaveCount(0);
});
test('WEB-CHANNEL-001 pausing a channel submits reason and reads back PAUSED', async ({ page }) => {
  let status = 'NORMAL';
  await mockEmptyDashboard(page);
  await page.route('**/api/v1/console/channels', (route) => route.fulfill({ contentType: 'application/json', body: JSON.stringify(apiResponse([{ ...channelNormal, status }])) }));
  await page.route('**/api/v1/console/channels/11/pause?*', async (route) => {
    expect(route.request().method()).toBe('POST');
    expect(Object.fromEntries(new URL(route.request().url()).searchParams)).toEqual({ reason: '投诉率超阈值', operatedBy: 'operator-demo' });
    status = 'PAUSED';
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify(apiResponse({ ...channelNormal, status })) });
  });
  page.on('dialog', (dialog) => dialog.accept('投诉率超阈值'));
  await loginAs(page, 'OPERATOR');
  await page.getByRole('link', { name: '通道管理' }).click();
  await page.getByRole('button', { name: '暂停' }).click();
  const row = page.getByRole('row').filter({ hasText: '移动主通道' });
  await expect(row).toContainText('PAUSED');
  await expect(row.getByRole('button', { name: '恢复' })).toBeVisible();
});

test('WEB-CHANNEL-002 resuming a channel reads back NORMAL', async ({ page }) => {
  let status = 'PAUSED';
  await mockEmptyDashboard(page);
  await page.route('**/api/v1/console/channels', (route) => route.fulfill({ contentType: 'application/json', body: JSON.stringify(apiResponse([{ ...channelNormal, status }])) }));
  await page.route('**/api/v1/console/channels/11/resume', async (route) => {
    expect(route.request().method()).toBe('POST');
    status = 'NORMAL';
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify(apiResponse({ ...channelNormal, status })) });
  });
  await loginAs(page, 'OPERATOR');
  await page.getByRole('link', { name: '通道管理' }).click();
  await page.getByRole('button', { name: '恢复' }).click();
  const row = page.getByRole('row').filter({ hasText: '移动主通道' });
  await expect(row).toContainText('NORMAL');
  await expect(row.getByRole('button', { name: '暂停' })).toBeVisible();
});
