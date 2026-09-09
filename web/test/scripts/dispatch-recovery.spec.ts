import { expect, test, type Page, type Route } from '@playwright/test';

test.use({ viewport: { width: 1440, height: 900 } });

const tokenPayload = btoa(JSON.stringify({ sub: 'phase25-admin', exp: 4_102_444_800, jti: 'phase25' }))
  .replace(/\+/g, '-')
  .replace(/\//g, '_')
  .replace(/=+$/, '');
const token = `eyJhbGciOiJub25lIn0.${tokenPayload}.signature`;

const monitorRows = [
  {
    channelId: 1101,
    channelName: 'phase25-paused',
    protocol: 'HTTP',
    operator: 'MOBILE',
    status: 'PAUSED',
    healthState: 'PAUSED',
    timeoutRate: '0.4200',
    failureRate: '0.1200',
    averageLatencyMs: 320,
    reasonCode: 'SUSTAINED_FAILURE',
    candidateEligible: false,
    candidateReasonCode: 'STATUS_PAUSED',
    eventCount: 3,
    pauseReason: 'provider outage',
    pausedBy: 'operator',
    pausedAt: '2026-09-09T10:00:00Z',
  },
];

const recoveryRows = [
  {
    taskId: 2501,
    messageId: 'MSG_P25_MIGRATE',
    tenantId: 17,
    channelId: 1101,
    channelName: 'phase25-paused',
    channelStatus: 'PAUSED',
    sendStatus: 'PENDING',
    outboxState: 'READY',
    outboxErrorCode: null,
    recoveryState: 'MIGRATABLE',
  },
  {
    taskId: 2502,
    messageId: 'MSG_P25_RETRY',
    tenantId: 17,
    channelId: 1101,
    channelName: 'phase25-paused',
    channelStatus: 'PAUSED',
    sendStatus: 'FAILED',
    outboxState: 'FAILED',
    outboxErrorCode: 'PROVIDER_REJECTED',
    recoveryState: 'RETRYABLE',
  },
  {
    taskId: 2503,
    messageId: 'MSG_P25_UNCERTAIN',
    tenantId: 17,
    channelId: 1101,
    channelName: 'phase25-paused',
    channelStatus: 'PAUSED',
    sendStatus: 'PENDING',
    outboxState: 'CLAIMED',
    outboxErrorCode: 'PROVIDER_TIMEOUT',
    recoveryState: 'UNCERTAIN',
  },
];

async function seedAuthenticatedRecoveryPage(page: Page) {
  await page.addInitScript(([storageToken]) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify({
      accessToken: storageToken,
      userType: 'ADMIN',
      tenantId: null,
    }));
  }, [token]);
  await page.route('**/api/v1/console/channel-health/monitor', (route: Route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 200, message: 'success', data: monitorRows }),
  }));
  await page.route('**/api/v1/console/account-overview', (route: Route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 200, message: 'success', data: { displayName: 'phase25-admin' } }),
  }));
  await page.route('**/api/v1/console/dispatch-recovery/inventory', (route: Route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 200, message: 'success', data: recoveryRows }),
  }));
  await page.route('**/api/v1/console/dispatch-recovery/tasks/2501/migrate', (route: Route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 200, message: 'success', data: { originalTaskId: 2501, action: 'MIGRATED', channelId: 1201 } }),
  }));
  await page.route('**/api/v1/console/dispatch-recovery/tasks/2502/retry', (route: Route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 200, message: 'success', data: { originalTaskId: 2502, newTaskId: 2602, action: 'RETRY_CREATED', channelId: 1201 } }),
  }));
  await page.route('**/api/v1/console/dispatch-recovery/channels/1101/recovery-tests', (route: Route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 200, message: 'success', data: { channelId: 1101, success: true, state: 'RECOVERY_TEST_PASSED' } }),
  }));
  await page.route('**/api/v1/console/dispatch-recovery/channels/1101/resume', (route: Route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 200, message: 'success', data: { channelId: 1101, success: true, state: 'CHANNEL_RECOVERED' } }),
  }));
}

test.describe('Phase 25 dispatch migration and recovery', () => {
  test('pw-p25-task-migration C-P25-TASK-MIGRATION OBL-F-4-7-C', async ({ page }) => {
    await seedAuthenticatedRecoveryPage(page);
    await page.goto('/admin/channel/health');
    await expect(page.getByTestId('admin-dispatch-task-channel-monitor-task-migration')).toBeVisible();
    await page.getByTestId('admin-dispatch-task-channel-monitor-recovery-evidence').fill('operator verified fallback');
    await page.getByRole('row', { name: /MSG_P25_MIGRATE/ }).getByTestId('admin-dispatch-task-channel-monitor-migrate').click();
    await expect(page.getByRole('status')).toContainText('派发恢复动作已记录');
  });

  test('pw-p25-recovery-test C-P25-RECOVERY-TEST OBL-F-4-7-D', async ({ page }) => {
    await seedAuthenticatedRecoveryPage(page);
    await page.goto('/admin/channel/health');
    await expect(page.getByTestId('admin-dispatch-task-channel-monitor-recovery-test')).toBeVisible();
    await page.getByTestId('admin-dispatch-task-channel-monitor-recovery-evidence').fill('sandbox probe accepted');
    await page.getByTestId('admin-dispatch-task-channel-monitor-recovery-test-run').click();
    await expect(page.getByRole('status')).toContainText('派发恢复动作已记录');
  });

  test('pw-p25-channel-recover C-P25-CHANNEL-RECOVER OBL-STATE-CHANNEL-RECOVER', async ({ page }) => {
    await seedAuthenticatedRecoveryPage(page);
    await page.goto('/admin/channel/health');
    await expect(page.getByTestId('admin-dispatch-task-channel-monitor-recovery-test')).toBeVisible();
    await page.getByTestId('admin-dispatch-task-channel-monitor-recovery-evidence').fill('latest recovery test passed');
    await page.getByTestId('admin-dispatch-task-channel-monitor-recovery-resume').click();
    await expect(page.getByRole('status')).toContainText('派发恢复动作已记录');
  });

  test('pw-p25-upstream-outage C-P25-UPSTREAM-OUTAGE OBL-EDGE-UPSTREAM-OUTAGE', async ({ page }) => {
    await seedAuthenticatedRecoveryPage(page);
    await page.goto('/admin/channel/health');
    await expect(page.getByTestId('admin-dispatch-task-channel-monitor-failover')).toBeVisible();
    await page.getByTestId('admin-dispatch-task-channel-monitor-failover').click();
    await expect(page.getByText('MSG_P25_UNCERTAIN')).toBeVisible();
  });
});
