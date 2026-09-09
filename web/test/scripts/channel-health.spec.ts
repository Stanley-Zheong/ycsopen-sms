import { expect, test, type Page } from '@playwright/test';

// Phase 11 is desktop Chrome-only at the contracted console viewport.
test.use({ viewport: { width: 1440, height: 900 } });
test.describe.configure({ mode: 'serial' });

const runtimeEnvironment = (globalThis as typeof globalThis & {
  process?: { env?: Record<string, string | undefined> };
}).process?.env ?? {};
const password = runtimeEnvironment.PHASE11_TEST_PASSWORD ?? 'Phase11-Valid!123';
const adminUsername = runtimeEnvironment.PHASE11_ADMIN_USERNAME ?? 'phase11-admin';

async function login(page: Page) {
  await page.goto('/login');
  await page.getByTestId('shared-auth-login-username').fill(adminUsername);
  await page.getByTestId('shared-auth-login-password').fill(password);
  await page.getByTestId('admin-console-identity-auth-login-submit').click();
  await expect(page).toHaveURL(/\/admin\/dashboard$/);
}

async function expectHealthPage(page: Page) {
  await expect(page.getByTestId('admin-channel-health-channel-monitor-page')).toBeVisible();
}

function healthRow(page: Page, channelName: string) {
  return page.getByRole('row', { name: new RegExp(`^${channelName}\\s`) });
}

test.describe('Phase 11 channel health, pools, candidate pause', () => {
  test('pw-p11-channel-monitor C-P11-HEALTH-METRICS OBL-F-4-3-A', async ({ page }) => {
    await login(page);
    await page.goto('/admin/channel/health');
    await expect(page.getByTestId('admin-channel-health-channel-monitor-page')).toBeVisible();
    await expectHealthPage(page);
    await expect(healthRow(page, 'phase11-main')).toBeVisible();
  });

  test('pw-p11-health-state C-P11-SUSTAINED-FAILURE OBL-F-4-3-B', async ({ page }) => {
    await login(page);
    await page.goto('/admin/channel/health');
    await expect(page.getByTestId('admin-channel-health-channel-monitor-health-state')).toHaveCount(2);
    await expectHealthPage(page);
    const row = healthRow(page, 'phase11-main');
    await expect(row.getByTestId('admin-channel-health-channel-monitor-health-state')).toContainText('HEALTHY');
    await row.getByTestId('admin-channel-health-channel-monitor-sample').click();
    await expect(page.getByRole('status')).toContainText('健康校验已记录');
  });

  test('pw-p11-channel-pools C-P11-POOL-CREATE OBL-F-4-6-A', async ({ page }) => {
    await login(page);
    await page.goto('/admin/channel/pools');
    await expect(page.getByTestId('admin-channel-health-channel-pools-page')).toBeVisible();
    await expect(page.getByTestId('admin-channel-health-channel-pools-row').filter({ hasText: 'phase11-weighted' })).toBeVisible();
  });

  test('pw-p11-pool-weight-editor C-P11-POOL-VALIDATION OBL-F-4-6-B', async ({ page }) => {
    await login(page);
    await page.goto('/admin/channel/pools');
    await expect(page.getByTestId('admin-channel-health-channel-pools-page')).toBeVisible();
    await page.getByTestId('admin-channel-health-channel-pools-create-open').click();
    await expect(page.getByTestId('admin-channel-health-channel-pools-weight-editor')).toBeVisible();
    await page.getByTestId('admin-channel-health-channel-pools-form-name').fill(`phase11-e2e-${Date.now()}`);
    await page.getByTestId('admin-channel-health-channel-pools-member-add').click();
    await page.getByTestId('admin-channel-health-channel-pools-member-weight').fill('90');
    await expect(page.getByRole('alert')).toContainText('权重总和必须等于 100');
    await page.getByTestId('admin-channel-health-channel-pools-member-weight').fill('100');
    await page.getByTestId('admin-channel-health-channel-pools-form-save').click();
    await expect(page.getByRole('status')).toContainText('通道池已保存');
  });

  test('pw-p11-maintenance-start C-P11-STATE-MAINTAIN OBL-STATE-CHANNEL-MAINTAIN', async ({ page }) => {
    await login(page);
    await page.goto('/admin/channel/health');
    await expect(page.getByTestId('admin-channel-health-channel-monitor-maintenance')).toHaveCount(2);
    await expectHealthPage(page);
    const row = healthRow(page, 'phase11-main');
    await row.getByTestId('admin-channel-health-channel-monitor-maintenance').click();
    await expect(page.getByTestId('admin-channel-health-channel-monitor-action-actor')).toContainText('当前登录账号');
    await page.getByTestId('admin-channel-health-channel-monitor-action-reason').fill('planned maintenance from Chrome');
    await page.getByTestId('admin-channel-health-channel-monitor-action-submit').click();
    await expect(page.getByRole('status')).toContainText('通道状态已更新');
    await expect(row).toContainText('MAINTENANCE');
  });

  test('pw-p11-maintenance-end C-P11-STATE-MAINTAIN-END OBL-STATE-CHANNEL-MAINTAIN-END', async ({ page }) => {
    await login(page);
    await page.goto('/admin/channel/health');
    await expect(page.getByTestId('admin-channel-health-channel-monitor-maintenance-end')).toHaveCount(2);
    await expectHealthPage(page);
    const row = healthRow(page, 'phase11-maintenance');
    await expect(row).toContainText('MAINTENANCE');
    await row.getByTestId('admin-channel-health-channel-monitor-maintenance-end').click();
    await expect(page.getByTestId('admin-channel-health-channel-monitor-action-actor')).toContainText('当前登录账号');
    await page.getByTestId('admin-channel-health-channel-monitor-action-reason').fill('latest validation passed');
    await page.getByTestId('admin-channel-health-channel-monitor-action-submit').click();
    await expect(page.getByRole('status')).toContainText('通道状态已更新');
    await expect(row).toContainText('可候选');
  });

  test('pw-p11-channel-pause pw-p11-channel-state-pause C-P11-CHANNEL-PAUSE C-P11-STATE-PAUSE OBL-F-4-7-A OBL-STATE-CHANNEL-PAUSE', async ({ page }) => {
    await login(page);
    await page.goto('/admin/channel/health');
    await expect(page.getByTestId('admin-channel-health-channel-monitor-pause')).toHaveCount(2);
    await expectHealthPage(page);
    const row = healthRow(page, 'phase11-maintenance');
    await row.getByTestId('admin-channel-health-channel-monitor-pause').click();
    await page.getByTestId('admin-channel-health-channel-monitor-action-trigger').selectOption('MANUAL');
    await expect(page.getByTestId('admin-channel-health-channel-monitor-action-actor')).toContainText('当前登录账号');
    await page.getByTestId('admin-channel-health-channel-monitor-action-reason').fill('manual pause from Chrome');
    await page.getByTestId('admin-channel-health-channel-monitor-action-submit').click();
    await expect(page.getByRole('status')).toContainText('通道状态已更新');
    await expect(row).toContainText('STATUS_PAUSED');
  });
});
